package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFinallySection
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic
import ru.kode.detekt.rule.compose.shared.ResolvedClass
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed
import java.util.regex.PatternSyntaxException

data class PartiallyUsedViewStateOptions(
  val stateClassPattern: Regex = Regex(".*ViewState"),
  val usedFieldsThreshold: Int = 3,
)

/** Compiles the value of the `stateClassPattern` option, an invalid one fails with an error which names the option. */
fun stateClassPattern(pattern: String): Regex = try {
  Regex(pattern)
} catch (e: PatternSyntaxException) {
  throw IllegalArgumentException("PartiallyUsedViewState: stateClassPattern \"$pattern\" is not a valid regex", e)
}

/**
 * Reports a composable function which takes a whole view state but reads only a few of its properties. Such a
 * function is recomposed on every change of the state, it should take the properties it needs instead.
 *
 * Non-compliant:
 * ```
 * data class ViewState(val title: String, val subtitle: String, val items: List<Item>, val isLoading: Boolean)
 *
 * @Composable
 * fun Header(state: ViewState) {
 *   Text(text = state.title)
 * }
 * ```
 *
 * Compliant:
 * ```
 * @Composable
 * fun Header(title: String) {
 *   Text(text = title)
 * }
 * ```
 *
 * A parameter is checked when the name of its class matches [PartiallyUsedViewStateOptions.stateClassPattern] and
 * the class has at least [PartiallyUsedViewStateOptions.usedFieldsThreshold] public or internal properties, inherited
 * ones included. It is reported when fewer than `usedFieldsThreshold` of them are read: as `state.title`, as `title`
 * inside `with(state) { }`, `state.run { }` or `state.apply { }`, or by a destructuring declaration. A function which
 * uses the state as a whole (passes it on, calls its function) is not reported, a comparison of the state with `null`
 * is not such a use. A function whose signature is not its own (`override`, `open`, `abstract`, `expect`, `actual` or
 * declared in an interface) is not checked.
 *
 * `state.let { it.title }` and `state.also { }` pass the state on, so they are uses of the whole state and
 * the function is not reported; count the reads of the lambda parameter like the ones of `with` if that hides real
 * findings.
 */
class PartiallyUsedViewStateAnalyzer(
  private val options: PartiallyUsedViewStateOptions,
  private val semantic: ComposeSemantic,
) {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()
    // the parameters of such a function can't be replaced
    if (FIXED_SIGNATURE_MODIFIERS.any(function::hasModifier) || function.containingClass()?.isInterface() == true) {
      return emptyList()
    }
    val body = function.bodyExpression ?: return emptyList()

    // a vararg parameter is an array of its declared type
    return function.valueParameters.filterNot { it.isVarArg }.mapNotNull { parameter ->
      val name = parameter.name ?: return@mapNotNull null
      val stateClass = parameter.typeReference?.let(semantic::resolveClass) ?: return@mapNotNull null
      val className = stateClass.fqName.substringAfterLast('.')
      val total = stateClass.propertyNames.size
      if (total < options.usedFieldsThreshold || !options.stateClassPattern.matches(className)) return@mapNotNull null

      val references = body.collectDescendantsOfType<KtNameReferenceExpression>().filter { reference ->
        // `holder.state` and `call(state = 1)` don't reference the parameter
        reference.getReferencedName() == name && reference.getReceiverExpression() == null &&
          reference.parent !is KtValueArgumentName && !reference.isDeclaredBetween(name, body)
      }
      val used = references.map { it.withoutNotNullAssertions() }.filterNot { it.isComparedToNull() }
        .flatMap { it.propertyReads(stateClass) ?: return@mapNotNull null }
        .distinct()
      // an unused parameter is not a partially used state
      if (used.isEmpty() || used.size >= options.usedFieldsThreshold) return@mapNotNull null

      ComposeDiagnostic(
        "Parameter \"$name\" reads only ${used.size} of $total \"$className\" properties " +
          "(${used.joinToString()}). Pass them instead of the whole state",
        parameter,
      )
    }
  }

  /** The properties this read of the state reads, null when it uses the state as a whole. */
  private fun KtExpression.propertyReads(stateClass: ResolvedClass): List<String>? {
    val parent = parent
    return when {
      parent is KtQualifiedExpression && parent.receiverExpression == this ->
        when (val selector = parent.selectorExpression) {
          is KtNameReferenceExpression -> selector.getReferencedName().takeIf { it in stateClass.propertyNames }
            ?.let(::listOf)

          // `apply` returns the state, so its result must not be used
          is KtCallExpression -> selector.receiverLambda("run", "apply")
            ?.takeIf { selector.calleeExpression?.text == "run" || parent.isUnusedStatement() }
            ?.implicitPropertyReads(stateClass)

          else -> null
        }

      parent is KtDestructuringDeclaration && parent.initializer == this ->
        parent.entries.withIndex().filterNot { it.value.name == "_" }
          .map { stateClass.componentNames.getOrNull(it.index) ?: return null }

      parent is KtValueArgument -> parent.getStrictParentOfType<KtCallExpression>()
        ?.takeIf { it.valueArguments.size == 2 && it.valueArguments.first() == parent }
        ?.receiverLambda("with")
        ?.implicitPropertyReads(stateClass)

      else -> null
    }
  }

  /**
   * The properties read on the state as the implicit receiver of this lambda, null when the lambda uses the state as
   * a whole: calls its function or mentions `this`. A read on the receiver of a nested lambda is not one of the state.
   * `with`, `run` and `apply` are matched by name, a namesake of them which takes a lambda without a
   * receiver gets its state argument counted as no read at all; resolve the callee if somebody declares one.
   */
  private fun KtLambdaExpression.implicitPropertyReads(stateClass: ResolvedClass): List<String>? {
    if (anyDescendantOfType<KtThisExpression>()) return null
    return collectDescendantsOfType<KtNameReferenceExpression>()
      .filter { it.parent !is KtValueArgumentName && semantic.isOnImplicitReceiverOf(it, functionLiteral) }
      .map { reference ->
        // `"x".decorate()` with an explicit receiver is a member extension, a function of the state
        reference.getReferencedName()
          .takeIf { it in stateClass.propertyNames && reference.getReceiverExpression() == null }
          ?: return null
      }
  }
}

/** The lambda of a call of one of [names] whose last argument it is. */
private fun KtCallExpression.receiverLambda(vararg names: String): KtLambdaExpression? {
  if (calleeExpression?.text !in names) return null
  return (valueArguments.lastOrNull()?.getArgumentExpression() as? KtLambdaExpression)
}

/** False when the value of the expression may be used, so also for the last statement of a lambda or of a branch. */
private fun KtExpression.isUnusedStatement(): Boolean = when (val parent = parent) {
  is KtBlockExpression -> parent.statements.lastOrNull() != this || parent.isUnusedStatement()
  is KtNamedFunction -> parent.hasBlockBody()
  is KtContainerNodeForControlStructureBody -> (parent.parent as? KtIfExpression)?.isUnusedStatement() ?: true
  is KtWhenEntry -> (parent.parent as KtExpression).isUnusedStatement()
  is KtTryExpression -> parent.isUnusedStatement()
  is KtCatchClause -> (parent.parent as KtExpression).isUnusedStatement()
  is KtFinallySection -> true
  else -> false
}

private val FIXED_SIGNATURE_MODIFIERS = listOf(
  KtTokens.OVERRIDE_KEYWORD,
  KtTokens.OPEN_KEYWORD,
  KtTokens.ABSTRACT_KEYWORD,
  KtTokens.EXPECT_KEYWORD,
  KtTokens.ACTUAL_KEYWORD,
)

private val EQUALITY_OPERATORS = setOf(KtTokens.EQEQ, KtTokens.EXCLEQ, KtTokens.EQEQEQ, KtTokens.EXCLEQEQEQ)

/** `state`, `(state)` and `state!!` are the same read. */
private fun KtExpression.withoutNotNullAssertions(): KtExpression {
  val parent = parent
  return when {
    parent is KtParenthesizedExpression -> parent.withoutNotNullAssertions()
    parent is KtPostfixExpression && parent.operationToken == KtTokens.EXCLEXCL -> parent.withoutNotNullAssertions()
    else -> this
  }
}

private fun KtExpression.isComparedToNull(): Boolean {
  val comparison = parent as? KtBinaryExpression ?: return false
  return comparison.operationToken in EQUALITY_OPERATORS &&
    listOfNotNull(comparison.left, comparison.right).any(KtPsiUtil::isNullConstant)
}
