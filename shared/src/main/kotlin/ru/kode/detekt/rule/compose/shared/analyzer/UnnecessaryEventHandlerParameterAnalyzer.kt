package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic
import ru.kode.detekt.rule.compose.shared.functionTypeOrNull
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed
import ru.kode.detekt.rule.compose.shared.isEventHandler

/**
 * Checks that event handlers of Composable do not have unnecessary parameter which could be provided by parent.
 * This makes individual components less coupled to the structure of their parameter and leaves that to the parent,
 * and in turn this often leads to simplification of composable.
 *
 * Wrong:
 *
 * ```
 * data class Data(id: Int, title: String)
 *
 * fun Component(data: Data, somethingClicked: (Int) -> Unit) {
 *   Button(onClick = { somethingClicked(data.id) })
 * }
 *
 * fun Parent() {
 *   val data = Data(id = 3, title = "foo")
 *   Component(data = data, somethingClicked = { id -> process(id) })
 * }
 * ```
 *
 * Correct:
 *
 * ```
 * data class Data(id: Int, title: String)
 *
 * fun Component(data: Data, somethingClicked: () -> Unit) {
 *   Button(onClick = somethingClicked)
 * }
 *
 * fun Parent() {
 *   val data = Data(id = 3, title = "foo")
 *   Component(data = data, somethingClicked = { process(data.id) })
 * }
 * ```
 *
 * A constant argument (a literal, a string without templates, a Kotlin `const val`, an enum entry or an object) is
 * reported the same way when every call of the event handler (`onClose(...)` or `onClose?.invoke(...)`) passes the
 * same constant at that position and the handler is used nowhere else (not passed on as a value): the parent already
 * knows it, so `onClose(Intent.Close)` should become `onClose()`. All such arguments of a handler are reported in one
 * finding. Different constants (`onCheckedChange(true)` and `onCheckedChange(false)`) are not reported.
 * Disabled with [reportConstantArguments].
 */
class UnnecessaryEventHandlerParameterAnalyzer(
  private val semantic: ComposeSemantic,
  private val reportConstantArguments: Boolean = true,
) {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    val (eventParameters, stateParameters) = function.valueParameters.partition { it.isEventHandler() }
    val eventCalls = mutableMapOf<KtParameter, MutableList<KtCallExpression>>()
    val eventsUsedOtherwise = mutableSetOf<KtParameter>()

    function.bodyExpression?.accept(
      object : KtTreeVisitorVoid() {
        override fun visitReferenceExpression(expression: KtReferenceExpression) {
          super.visitReferenceExpression(expression)
          if (expression !is KtNameReferenceExpression || expression.parent is KtValueArgumentName) return
          val eventParameter = eventParameters.find { it.name == expression.getReferencedName() } ?: return
          val call = expression.calledAsFunction()
          val targetsEventParameter = semantic.referenceTargetsParameter(expression, eventParameter)
            ?: expression.looksLikeReferenceTo(eventParameter, call, function)
          if (!targetsEventParameter) return

          if (call != null) {
            eventCalls.getOrPut(eventParameter) { mutableListOf() } += call
          } else {
            eventsUsedOtherwise += eventParameter
          }
        }
      },
    )

    val diagnostics = mutableListOf<ComposeDiagnostic>()
    eventCalls.forEach { (eventParameter, calls) ->
      calls.forEach { call ->
        call.valueArguments.forEachIndexed { index, argument ->
          val stateParameterName = argument.getArgumentExpression()?.stateParameterName(stateParameters, function)
          if (stateParameterName != null) {
            diagnostics += buildDiagnostic(eventParameter, "all \"$stateParameterName\" access", setOf(index))
          }
        }
      }
    }
    if (reportConstantArguments) {
      eventCalls.filterKeys { it !in eventsUsedOtherwise }.forEach { (eventParameter, calls) ->
        constantArgumentDiagnostic(eventParameter, calls)?.let { diagnostics += it }
      }
    }
    return diagnostics
  }

  /** The `handler(...)` or `handler.invoke(...)` call this reference is the function of. */
  private fun KtNameReferenceExpression.calledAsFunction(): KtCallExpression? = when (val parent = parent) {
    is KtCallExpression -> parent.takeIf { it.calleeExpression == this }

    is KtQualifiedExpression -> (parent.selectorExpression as? KtCallExpression)
      ?.takeIf { parent.receiverExpression == this && it.calleeExpression?.text == "invoke" }

    else -> null
  }

  /**
   * Name-based stand-in for [ComposeSemantic.referenceTargetsParameter] when the reference can't be resolved. A
   * `handler(...)` call has to match the function type of [eventParameter] to be told from a same-named function.
   */
  private fun KtNameReferenceExpression.looksLikeReferenceTo(
    eventParameter: KtParameter,
    call: KtCallExpression?,
    function: KtNamedFunction,
  ): Boolean {
    if (getReceiverExpression() != null || isDeclaredBetween(getReferencedName(), function)) return false
    if (call == null || call.calleeExpression != this) return true
    return call.valueArguments.size == eventParameter.functionTypeOrNull()?.parameters?.size &&
      call.valueArguments.none { it.isNamed() || it.isSpread }
  }

  /** Name of the state parameter this argument is or reads a property of. */
  private fun KtExpression.stateParameterName(stateParameters: List<KtParameter>, function: KtNamedFunction): String? {
    val root = when (this) {
      is KtNameReferenceExpression -> this
      is KtDotQualifiedExpression -> rootReferenceUnlessSealed()
      else -> null
    } ?: return null
    val name = root.getReferencedName()
    val stateParameter = stateParameters.find { it.name == name } ?: return null
    val targetsStateParameter = semantic.referenceTargetsParameter(root, stateParameter)
      ?: !root.isDeclaredBetween(name, function)
    return name.takeIf { targetsStateParameter }
  }

  private fun KtDotQualifiedExpression.rootReferenceUnlessSealed(): KtNameReferenceExpression? {
    if (selectorExpression is KtCallExpression || semantic.receiverHasSealedTypeOrSupertype(receiverExpression)) {
      return null
    }
    return generateSequence(receiverExpression) { (it as? KtDotQualifiedExpression)?.receiverExpression }
      .last() as? KtNameReferenceExpression
  }

  /** One finding for all argument indexes which every call of [eventParameter] passes the same constant at. */
  private fun constantArgumentDiagnostic(
    eventParameter: KtParameter,
    calls: List<KtCallExpression>,
  ): ComposeDiagnostic? {
    val constants = (0 until calls.minOf { it.valueArguments.size }).mapNotNull { index ->
      val constant = calls.map { call ->
        call.valueArguments[index].getArgumentExpression()
          ?.let(KtPsiUtil::safeDeparenthesize)
          ?.takeIf { it.isConstant() }
          ?.text
          ?: return@mapNotNull null
      }.distinct().singleOrNull() ?: return@mapNotNull null
      index to constant
    }.toMap()
    if (constants.isEmpty()) return null
    val movedArgument = if (constants.size == 1) "constant" else "constants"
    return buildDiagnostic(
      eventParameter,
      "$movedArgument ${constants.values.joinToString {
        "\"$it\""
      }}",
      constants.keys,
    )
  }

  private fun KtExpression.isConstant(): Boolean = when (this) {
    is KtConstantExpression -> true

    is KtPrefixExpression -> baseExpression?.isConstant() == true

    is KtStringTemplateExpression -> !hasInterpolation()

    is KtNameReferenceExpression -> semantic.isConstantReference(this)

    is KtDotQualifiedExpression -> (selectorExpression as? KtNameReferenceExpression)
      ?.let(semantic::isConstantReference) == true

    else -> false
  }

  private fun buildDiagnostic(
    eventParameter: KtParameter,
    movedArgument: String,
    argumentIndexes: Set<Int>,
  ): ComposeDiagnostic {
    return ComposeDiagnostic(
      "Unnecessary event callback arguments. Move $movedArgument " +
        "to the parent composable event handler and switch \"${eventParameter.name}\" type to " +
        "\"${suggestedType(eventParameter, argumentIndexes)}\"",
      eventParameter,
    )
  }

  private fun suggestedType(eventParameter: KtParameter, argumentIndexes: Set<Int>): String {
    val parameterList = eventParameter.functionTypeOrNull()
      ?.parameters
      ?.filterIndexed { index, _ -> index !in argumentIndexes }
      .orEmpty()
    return "${parameterList.joinToString(prefix = "(", postfix = ")", transform = { it.text })} -> Unit"
      .let { if (eventParameter.typeReference?.typeElement is KtNullableType) "($it)?" else it }
  }
}
