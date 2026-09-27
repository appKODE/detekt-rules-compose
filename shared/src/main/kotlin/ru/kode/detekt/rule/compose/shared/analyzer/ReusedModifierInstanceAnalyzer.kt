package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed
import ru.kode.detekt.rule.compose.shared.isModifier

data class ReusedModifierInstanceOptions(
  val modifierClassPackage: String = "androidx.compose.ui",
)

/**
 * Reports errors when reusing the modifier instance on a wrong level of composable hierarchy, for example:
 *
 * ```kotlin
 * @Composable
 * fun MyComposable(modifier: Modifier) {
 *   Row(modifier = Modifier.height(30.dp)) {
 *     Column(modifier = modifier.width(20.dp)) {
 *     }
 *   }
 * }
 * ```
 *
 * Above code is wrong, and `modifier` parameter should be used on the top Composable:
 *
 * ```kotlin
 * @Composable
 * fun MyComposable(modifier: Modifier) {
 *   Row(modifier = modifier.height(30.dp)) {
 *     Column(modifier = Modifier.width(20.dp)) {
 *     }
 *   }
 * }
 * ```
 */
class ReusedModifierInstanceAnalyzer(
  private val options: ReusedModifierInstanceOptions,
  private val semantic: ComposeSemantic,
) {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable") || function.valueParameters.none { it.isModifier() }) {
      return emptyList()
    }

    val diagnostics = mutableListOf<ComposeDiagnostic>()

    val childVisitor = object : KtTreeVisitorVoid() {
      override fun visitCallExpression(expression: KtCallExpression) {
        val modifierArgumentExpression = expression.valueArguments
          .find { it.getArgumentExpression()?.isModifierChainExpression() == true }
          ?.getArgumentExpression()

        if (modifierArgumentExpression?.text?.startsWith("modifier") == true &&
          !modifierArgumentExpression.isDeclaredBetween("modifier", function)
        ) {
          diagnostics += ComposeDiagnostic(
            "Composable uses \"modifier\" on the wrong level, non-direct children should use \"Modifier\"",
            expression,
          )
        }

        super.visitCallExpression(expression)
      }
    }

    val composableCallsVisitor = object : KtTreeVisitorVoid() {
      override fun visitCallExpression(expression: KtCallExpression) {
        if (semantic.callHasParameterOfType(expression, "${options.modifierClassPackage}.Modifier")) {
          val contentLambdaExpression = expression.valueArguments
            .find { it.getArgumentExpression() is KtLambdaExpression }
            ?.getArgumentExpression() as? KtLambdaExpression
          contentLambdaExpression?.bodyExpression?.accept(childVisitor)
        } else {
          super.visitCallExpression(expression)
        }
      }
    }

    val topLevelCalls = function.bodyBlockExpression?.getChildrenOfType<KtCallExpression>()?.toList()
      ?: listOfNotNull(function.bodyExpression as? KtCallExpression)
    topLevelCalls.forEach { it.accept(composableCallsVisitor) }

    return diagnostics
  }
}
