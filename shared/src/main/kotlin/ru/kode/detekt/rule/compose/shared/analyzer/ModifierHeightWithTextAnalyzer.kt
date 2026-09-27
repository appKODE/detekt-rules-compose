package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed

/**
 * Reports usage of height modifier in composable functions with text.
 * Text is measured in sp and so its contents can change depending on the system setting.
 * Therefore, it is not safe to use fixed height on containers with Text: this will lead
 * to clipped text in some cases.
 *
 * This check suggests replacing this code:
 *
 * ```
 * Row(modifier = Modifier.height(24.dp)) {
 *   Text("hello")
 * }
 * ```
 * with
 * ```
 * Row(modifier = Modifier.heightIn(min = 24.dp)) {
 *   Text("hello")
 * }
 * ```
 *
 * In this case parent container can be larger if needed.
 *
 * `height(IntrinsicSize.Min)` and `height(IntrinsicSize.Max)` size to the content and are not reported; they are
 * matched by text, so a `Min` or `Max` value of another type passed to `height` is skipped too.
 */
class ModifierHeightWithTextAnalyzer {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    val diagnostics = mutableListOf<ComposeDiagnostic>()
    function.bodyExpression?.accept(
      object : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          val contentLambdaExpression = expression.valueArguments
            .find { it.getArgumentExpression() is KtLambdaExpression }
            ?.getArgumentExpression() as? KtLambdaExpression

          if (contentLambdaExpression != null) {
            val argumentWithHeight = expression.valueArguments.find { argument ->
              argument.getArgumentExpression()?.isModifierChainExpression() == true &&
                argument.anyDescendantOfType<KtCallExpression> { it.isFixedHeightCall() }
            }

            if (argumentWithHeight != null) {
              val containsTextChild = contentLambdaExpression.bodyExpression
                ?.getChildrenOfType<KtCallExpression>()
                ?.any { it.calleeExpression?.text == "Text" } == true

              if (containsTextChild) {
                val heightCall = argumentWithHeight
                  .findDescendantOfType<KtCallExpression> { it.isFixedHeightCall() }
                if (heightCall != null) {
                  diagnostics += ComposeDiagnostic(
                    "Composable uses \"height\" modifier and contains a Text child. Use heightIn(min = N.dp) instead",
                    heightCall,
                  )
                }
              }
            }
          }

          super.visitCallExpression(expression)
        }
      },
    )

    return diagnostics
  }
}

// `height(IntrinsicSize.Min)` sizes to the content, so it can't clip the text
private fun KtCallExpression.isFixedHeightCall(): Boolean {
  if (calleeExpression?.text != "height") return false
  val argument = valueArguments.singleOrNull()?.getArgumentExpression()?.text ?: return true
  // IntrinsicSize.Min, fully qualified, or Min imported from IntrinsicSize
  return argument.substringAfterLast("IntrinsicSize.") !in setOf("Min", "Max")
}

private fun KtValueArgument.anyDescendantOfType(predicate: (KtCallExpression) -> Boolean): Boolean {
  return anyDescendantOfType<KtCallExpression>(predicate)
}
