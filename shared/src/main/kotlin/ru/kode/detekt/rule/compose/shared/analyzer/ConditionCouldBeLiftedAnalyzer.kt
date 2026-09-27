package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
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

          if (conditionalExpression != null) {
            val elseExpression = conditionalExpression.`else`
            if (elseExpression?.hasComposableCallChildren(options.composableAnnotationClassPackage, semantic) == true) {
              return super.visitCallExpression(expression)
            }

            val contentHasComposableCallChildren = contentComposableLambda.bodyExpression
              ?.getChildrenOfType<KtCallExpression>()
              .orEmpty()
              .any { it.isComposableCall(options.composableAnnotationClassPackage, semantic) }

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
  return collectDescendantsOfType<KtCallExpression>().any {
    it.isComposableCall(composableAnnotationClassPackage, semantic)
  }
}
