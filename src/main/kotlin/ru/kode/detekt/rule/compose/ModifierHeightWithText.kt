package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.DetektVisitor
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType

/**
 * Reports usage of height modifier in composable functions with text.
 * Text is measured in sp and so its contents can change depending on the system setting.
 * Therefore it is not safe to use fixed height on containers with Text: this will lead
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
 */
class ModifierHeightWithText(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "Reports usage of height modifier in composable functions with text",
        Debt.FIVE_MINS
    )

    override fun visitNamedFunction(function: KtNamedFunction) {
        if (function.hasAnnotation("Composable")) {
            function.bodyBlockExpression?.accept(ChildrenWithModifiersVisitor())
        }
        super.visitNamedFunction(function)
    }

    private inner class ChildrenWithModifiersVisitor : DetektVisitor() {
        override fun visitCallExpression(expression: KtCallExpression) {
            val contentLambdaExpression = expression.valueArguments.find { it.getArgumentExpression() is KtLambdaExpression }
                ?.getArgumentExpression() as KtLambdaExpression?
            if (contentLambdaExpression != null) {
                val argumentWithHeight = expression.valueArguments.find { arg ->
                    arg.getArgumentExpression()?.isModifierChainExpression() == true &&
                        arg.anyDescendantOfType<KtCallExpression> { it.calleeExpression?.text == "height" }
                }
                if (argumentWithHeight != null) {
                    val containsTextChild = contentLambdaExpression.bodyExpression?.getChildrenOfType<KtCallExpression>()?.any {
                        it.calleeExpression?.text == "Text"
                    }
                    if (containsTextChild == true) {
                        reportError(argumentWithHeight)
                    }
                }
            }
            super.visitCallExpression(expression)
        }

        private fun KtExpression.isModifierChainExpression(): Boolean {
            return (this as? KtDotQualifiedExpression)?.text?.let {
                it.startsWith("Modifier") || it.startsWith("modifier")
            } == true
        }

        private fun reportError(node: KtValueArgument) {
            val heightCall = node.findDescendantOfType<KtCallExpression>() { it.calleeExpression?.text == "height" }
                ?: error("didn't find height-call node")
            report(
                CodeSmell(
                    issue,
                    Entity.from(heightCall),
                    "Composable uses \"height\" modifier and contains a Text child. Use heightIn(min = N.dp) instead"
                )
            )
        }
    }
}
