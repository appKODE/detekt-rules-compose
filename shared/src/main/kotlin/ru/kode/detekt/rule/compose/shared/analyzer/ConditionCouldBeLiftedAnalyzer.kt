package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed

data class ConditionCouldBeLiftedOptions(
  val composableAnnotationClassPackage: String = "androidx.compose.runtime",
  val ignoreCallsWithArgumentNames: List<String> = listOf("modifier"),
)

/**
 * Reports cases where a Compose layout contains a single conditional expression which could be lifted.
 *
 * Non-compliant:
 * ```
 * Column {
 *   if(condition) {
 *     Row()
 *     Row()
 *   }
 * }
 * ```
 *
 * Compliant:
 * ```
 * if(condition) {
 *   Column {
 *     Row()
 *     Row()
 *   }
 * }
 * ```
 *
 * Use [ConditionCouldBeLiftedOptions.ignoreCallsWithArgumentNames] config option to specify argument names which (when present) will make this rule
 * ignore and skip those calls:
 *
 * ```
 * // in detekt-config.yaml
 * ConditionCouldBeLifted:
 *   active: true
 *   ignoreCallsWithArgumentNames: [ 'modifier', 'contentAlignment' ]
 * ```
 *
 * A condition on a name declared inside the content lambda (its parameters, local `val`s, local functions) is not
 * reported. A local `val` derived from an outer one of the same name (`val items = items.sorted()`) counts as the
 * outer declaration, so such a condition is still reported.
 */
class ConditionCouldBeLiftedAnalyzer(
  private val options: ConditionCouldBeLiftedOptions,
  private val semantic: ComposeSemantic,
) {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    val diagnostics = mutableListOf<ComposeDiagnostic>()
    function.bodyExpression?.accept(
      object : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          val contentComposableLambda = expression.tryExtractContentLambda()
            ?: return super.visitCallExpression(expression)

          val conditionalExpression = contentComposableLambda.bodyExpression
            ?.getChildrenOfType<KtIfExpression>()
            ?.singleOrNull()
            // a condition on what the content lambda declares can't be lifted out of it
            ?.takeUnless { it.conditionUsesNamesDeclaredIn(contentComposableLambda, semantic) }

          if (conditionalExpression != null) {
            val elseExpression = conditionalExpression.`else`
            if (elseExpression?.hasComposableCallChildren(options.composableAnnotationClassPackage, semantic) == true) {
              return super.visitCallExpression(expression)
            }

            // `slot?.invoke()` or `slot?.let { it() }` is a qualified expression, not a call, so look inside statements
            val contentHasComposableCallChildren = contentComposableLambda.bodyExpression?.statements.orEmpty()
              .any {
                it != conditionalExpression && it !is KtDeclaration &&
                  it.hasComposableCallChildren(options.composableAnnotationClassPackage, semantic)
              }

            if (!contentHasComposableCallChildren) {
              val conditionalHasComposableCallChildren = conditionalExpression.then
                ?.hasComposableCallChildren(options.composableAnnotationClassPackage, semantic)
                ?: false

              if (conditionalHasComposableCallChildren) {
                diagnostics += ComposeDiagnostic(
                  "Condition could be lifted out of \"${expression.calleeExpression?.text ?: "unknown"}\"",
                  conditionalExpression,
                )
              }
            }
          }

          super.visitCallExpression(expression)
        }
      },
    )

    return diagnostics
  }

  private fun KtCallExpression.tryExtractContentLambda(): KtLambdaExpression? {
    if (semantic.explicitArgumentParameterNames(this).any { it in options.ignoreCallsWithArgumentNames }) return null

    // not every composable with a single composable lambda has "content" semantics, so only that name is checked
    val contentParameterName = semantic.firstComposableLambdaParameterName(
      this,
      options.composableAnnotationClassPackage,
    )
    if (contentParameterName != "content") return null

    return semantic.argumentForParameterNamed(this, contentParameterName) as? KtLambdaExpression
  }
}

private fun KtIfExpression.conditionUsesNamesDeclaredIn(
  lambda: KtLambdaExpression,
  semantic: ComposeSemantic,
): Boolean {
  val hasImplicitIt = semantic::lambdaHasImplicitIt
  return condition?.collectDescendantsOfType<KtNameReferenceExpression>().orEmpty().any { reference ->
    // `pagerState.page` and `check(page = 1)` don't reference a local `page`
    if (reference.getReceiverExpression() != null || reference.parent is KtValueArgumentName) return@any false
    val name = reference.getReferencedName()
    // skip names the condition declares itself, like the `it` of `items.any { it > 0 }`
    !reference.isDeclaredBetween(name, this, hasImplicitIt) && isDeclaredBetween(name, lambda, hasImplicitIt)
  }
}

private fun KtCallExpression.isComposableCall(
  composableAnnotationClassPackage: String,
  semantic: ComposeSemantic,
): Boolean {
  return semantic.isComposableCall(this, composableAnnotationClassPackage)
}

private fun KtExpression.hasComposableCallChildren(
  composableAnnotationClassPackage: String,
  semantic: ComposeSemantic,
): Boolean {
  return anyDescendantOfType<KtCallExpression> {
    it.isComposableCall(composableAnnotationClassPackage, semantic)
  }
}
