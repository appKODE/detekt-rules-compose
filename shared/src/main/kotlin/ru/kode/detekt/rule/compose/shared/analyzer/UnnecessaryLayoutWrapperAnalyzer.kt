package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic

private val layoutNames = setOf("Box", "Column", "Row")

// modifiers of the wrapper's scope: without the wrapper they'd mean something else or not compile
private val parentScopeModifierNames = setOf("weight", "align", "alignBy", "alignByBaseline", "matchParentSize")

/**
 * Reports a `Box`, `Column` or `Row` which has no parameters and wraps a single `Box`, `Column` or `Row`:
 * such a wrapper usually adds nothing but a layout node, and its child can be used directly.
 *
 * Non-compliant:
 *
 * ```
 * Box {
 *   Row(modifier = Modifier.padding(16.dp)) {
 *     Text("hello")
 *   }
 * }
 * ```
 *
 * Compliant:
 *
 * ```
 * Row(modifier = Modifier.padding(16.dp)) {
 *   Text("hello")
 * }
 * ```
 *
 * The check is name-based and conservative: the wrapper must have nothing but a trailing content lambda, and the
 * lambda must contain nothing but the child call. Still, removing a wrapper is not always a no-op, which is why the
 * rule is disabled by default:
 * - scope modifiers: a child using `weight`, `align`, `alignBy`, `alignByBaseline` or `matchParentSize` depends on
 *   the wrapper's scope and is not reported, but the same modifiers passed through a variable are not detected
 * - min constraints: the wrapper measures its child with a minimum size of 0, without it the child gets the
 *   incoming minimum constraints (e.g. as the content of a `Surface`) and can be stretched
 * - name-based matching: project components named `Box`, `Row` or `Column` are matched too
 */
class UnnecessaryLayoutWrapperAnalyzer {
  fun analyze(call: KtCallExpression): ComposeDiagnostic? {
    val wrapperName = call.layoutName() ?: return null
    if (call.typeArgumentList != null) return null
    // getLambdaExpression unwraps labelled lambdas (`Box label@{}`)
    val lambda = (call.valueArguments.singleOrNull() as? KtLambdaArgument)?.getLambdaExpression() ?: return null
    if (lambda.valueParameters.isNotEmpty()) return null
    val body = lambda.bodyExpression?.takeIf { it.hasSingleLayoutCall() } ?: return null
    val child = body.statements.single() as KtCallExpression

    return ComposeDiagnostic(
      "\"$wrapperName\" has no parameters and wraps a single \"${child.layoutName()}\", it is likely unnecessary",
      call,
    )
  }

  private fun KtBlockExpression.hasSingleLayoutCall(): Boolean {
    val child = statements.singleOrNull() as? KtCallExpression ?: return false
    return child.layoutName() != null &&
      child.valueArguments
        // content lambdas, labelled ones included, run in the child's own scope
        .filter { argument ->
          argument.getArgumentExpression()?.let(KtPsiUtil::safeDeparenthesize) !is KtLambdaExpression
        }
        .none { argument ->
          argument.anyDescendantOfType<KtCallExpression> { it.calleeExpression?.text in parentScopeModifierNames }
        }
  }
}

private fun KtCallExpression.layoutName(): String? {
  return (calleeExpression as? KtNameReferenceExpression)?.getReferencedName()?.takeIf { it in layoutNames }
}
