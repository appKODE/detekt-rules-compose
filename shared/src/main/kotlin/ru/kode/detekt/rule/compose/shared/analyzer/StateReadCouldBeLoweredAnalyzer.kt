package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import ru.kode.detekt.rule.compose.shared.ComposableLambdaParameter
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed

data class StateReadCouldBeLoweredOptions(
  val composableAnnotationClassPackage: String = "androidx.compose.runtime",
  val minDepth: Int = 2,
)

/**
 * Reports a state read (`.value` of a `State` or `.current` of a `CompositionLocal`) stored in a `val` of a composable
 * function which is only used [StateReadCouldBeLoweredOptions.minDepth] or more composable lambdas deeper. Reading
 * the state next to its use keeps the read out of the scopes which do not need it. `State` and `CompositionLocal` are
 * looked up in [StateReadCouldBeLoweredOptions.composableAnnotationClassPackage].
 *
 * Non-compliant:
 * ```
 * val isPortrait = LocalOrientation.current == Orientation.Portrait
 * Card {
 *   Surface {
 *     if (isPortrait) Text("Portrait")
 *   }
 * }
 * ```
 *
 * Compliant:
 * ```
 * Card {
 *   Surface {
 *     val isPortrait = LocalOrientation.current == Orientation.Portrait
 *     if (isPortrait) Text("Portrait")
 *   }
 * }
 * ```
 *
 * A lambda passed to any composable lambda parameter counts (`content`, `topBar`, ...), but not one which is inlined:
 * the lambda of an `inline` composable (`Box`, `Column`, `Row`) is no recomposition scope of its own, a read moved
 * into it is still a read of the scope around. A `crossinline` lambda (`itemContent` of the lazy list `items`) does
 * count: it is inlined into a lambda which the callee creates, not into the caller.
 *
 * A `val` with a delegate, a lambda or a `remember*` call in its initializer is not reported, neither is one whose
 * initializer uses a name which is redeclared on the way down.
 */
class StateReadCouldBeLoweredAnalyzer(
  private val options: StateReadCouldBeLoweredOptions,
  private val semantic: ComposeSemantic,
) {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()
    // Only `val`s declared directly in the function body, check nested blocks too if they are missed
    val body = function.bodyBlockExpression ?: return emptyList()

    return body.statements.filterIsInstance<KtProperty>().filter { it.isStateRead() }.mapNotNull { property ->
      val name = property.name ?: return@mapNotNull null
      val contentLambdas = body.collectDescendantsOfType<KtNameReferenceExpression>()
        .filter { reference ->
          // `holder.name`, `call(name = 1)` and `name()` don't reference the local `name`
          reference.getReferencedName() == name && reference.getReceiverExpression() == null &&
            reference.parent !is KtValueArgumentName &&
            (reference.parent as? KtCallExpression)?.calleeExpression != reference &&
            !reference.isDeclaredBetween(name, body)
        }
        .map { it.enclosingContentLambdas(body) }
        .reduceOrNull { common, lambdas -> common.zip(lambdas).takeWhile { (a, b) -> a == b }.map { it.first } }
        .orEmpty()
      // an inlined lambda is recomposed together with the lambda around it, so it is not a level of its own
      if (contentLambdas.count { !it.parameter.isInlined } < options.minDepth) return@mapNotNull null
      val target = contentLambdas.last()
      // moved into the target lambda the initializer would read another declaration
      val targetBody = target.lambda.bodyExpression ?: return@mapNotNull null
      val initializerNames = property.initializer?.collectDescendantsOfType<KtNameReferenceExpression>().orEmpty()
        .filter { it.getReceiverExpression() == null && it.parent !is KtValueArgumentName }
      if (initializerNames.any { targetBody.isDeclaredBetween(it.getReferencedName(), body) }) return@mapNotNull null

      val callee = target.lambda.getStrictParentOfType<KtCallExpression>()?.calleeExpression?.text
      val slot = target.parameter.name.takeUnless { it == "content" }?.let { "\"$it\" slot" } ?: "content"
      ComposeDiagnostic(
        "\"$name\" is only used inside the $slot of \"$callee\". Read the state there instead",
        property,
        atName = true,
      )
    }
  }

  /** Lambdas passed to the composable lambda parameters of the calls around this element, outermost first. */
  private fun KtNameReferenceExpression.enclosingContentLambdas(body: KtBlockExpression): List<ContentLambda> {
    return parents.takeWhile { it != body }.filterIsInstance<KtLambdaExpression>()
      .mapNotNull { lambda ->
        val call = lambda.getStrictParentOfType<KtCallExpression>() ?: return@mapNotNull null
        semantic.composableLambdaParameters(call, options.composableAnnotationClassPackage)
          .firstOrNull { semantic.argumentForParameterNamed(call, it.name) == lambda }
          ?.let { ContentLambda(lambda, it) }
      }
      .toList()
      .asReversed()
  }

  private data class ContentLambda(val lambda: KtLambdaExpression, val parameter: ComposableLambdaParameter)

  private fun KtProperty.isStateRead(): Boolean {
    val initializer = initializer?.takeUnless { isVar || hasDelegate() } ?: return false
    if (initializer.anyDescendantOfType<KtLambdaExpression>()) return false
    if (initializer.anyDescendantOfType<KtCallExpression> {
        it.calleeExpression?.text?.startsWith("remember") == true
      }
    ) {
      return false
    }
    val runtimePackage = options.composableAnnotationClassPackage
    return initializer.anyDescendantOfType<KtQualifiedExpression> { expression ->
      when (expression.selectorExpression?.text) {
        "value" -> semantic.isInstanceOf(expression.receiverExpression, "$runtimePackage.State")
        "current" -> semantic.isInstanceOf(expression.receiverExpression, "$runtimePackage.CompositionLocal")
        else -> false
      }
    }
  }
}
