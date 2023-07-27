package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.DetektVisitor
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.resolve.calls.model.DefaultValueArgument
import org.jetbrains.kotlin.resolve.calls.results.argumentValueType
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import ru.kode.detekt.rule.compose.node.hasComposableCallChildren
import ru.kode.detekt.rule.compose.node.isComposableCall

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
 * Use [ignoreCallsWithArgumentNames] config option to specify argument names which (when present) will make this rule
 * ignore and skip those calls:
 *
 * ```
 * // in detekt-config.yaml
 * ConditionCouldBeLifted:
 *   active: true
 *   ignoreCallsWithArgumentNames: [ 'modifier', 'contentAlignment' ]
 * ```
 */
@RequiresTypeResolution
class ConditionCouldBeLifted(
  config: Config = Config.empty,
  // this parameter is used in tests to pass another package
  private val composableAnnotationClassPackage: String = "androidx.compose.runtime"
) : Rule(config) {

  override val issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    "Reports liftable conditions in compose layouts",
    Debt.FIVE_MINS
  )

  private val ignoreCallsWithArgumentNames by config(defaultValue = listOf("modifier"))

  override fun visitNamedFunction(function: KtNamedFunction) {
    if (function.hasAnnotation("Composable")) {
      function.bodyBlockExpression?.accept(ComposableLayoutCallsVisitor())
    }
  }

  private inner class ComposableLayoutCallsVisitor : DetektVisitor() {
    // @Composable
    // fun Component() {
    //   Row { <- this visitor will find and work inside this lambda
    //     ...
    //     Column { <- it can also descend recursively and work inside this lambda
    //        ...
    //     }
    //   }
    // }
    override fun visitCallExpression(expression: KtCallExpression) {
      val contentComposableLambda = expression.tryExtractContentLambda()
        ?: return super.visitCallExpression(expression)

      val conditionalExpression = contentComposableLambda.bodyExpression?.getChildrenOfType<KtIfExpression>()
        ?.singleOrNull()
      if (conditionalExpression != null &&
        conditionalExpression.`else`
          ?.hasComposableCallChildren(composableAnnotationClassPackage, bindingContext) != true
      ) {
        // do child expressions of the layout content contain composable calls?
        val contentHasComposableCallChildren = contentComposableLambda.bodyExpression
          ?.getChildrenOfType<KtCallExpression>()
          .orEmpty()
          .any { it.isComposableCall(composableAnnotationClassPackage, bindingContext) }
        if (!contentHasComposableCallChildren) {
          // do child expressions of the conditional expression contain composable calls?
          val conditionalHasComposableCallChildren = conditionalExpression.then
            ?.hasComposableCallChildren(composableAnnotationClassPackage, bindingContext)
            ?: false
          if (conditionalHasComposableCallChildren) {
            reportError(conditionalExpression, expression)
          }
        }
      }
      super.visitCallExpression(expression)
    }

    /**
     * Checks if the receiver expression is a composable call which returns Unit and
     * has a single "composable content" lambda, for example
     * ```
     * Row(modifier = Modifier, content = { ... })
     * ```
     *
     * @return a content lambda expression or null if check failed
     */
    private fun KtCallExpression.tryExtractContentLambda(): KtLambdaExpression? {
      val resolvedCall = this.getResolvedCall(bindingContext) ?: return null
      if (resolvedCall.valueArguments.any { (key, value) ->
        ignoreCallsWithArgumentNames.contains(key.name.toString()) &&
          value !is DefaultValueArgument
      }
      ) {
        return null
      }
      val annotationFqName = FqName("$composableAnnotationClassPackage.Composable")
      val potentialContentArg = resolvedCall.valueArguments.keys.firstOrNull { arg ->
        arg.argumentValueType.isFunctionType && arg.argumentValueType.annotations.hasAnnotation(annotationFqName)
      } ?: return null

      // for now restricting to this name. Generally this restriction could be lifted, but
      // not all composables with single lambda would have "content" semantics, this could be tricky
      // to derive correctly
      if (potentialContentArg.name.toString() != "content") {
        return null
      }

      // NOTE: the tricky part here is that resolved call has all the arguments in declaration while AST call
      // (represented by 'this.valueArguments') can have default parameters omitted.
      // And there's more: both resolved call and valueArguments can have parameters reordered
      // (when call has named args), so this code has to account for that too.
      // For now, we try few things to try to find the "content" lambda, but perhaps this detection
      // can still be improved

      // first try to find by name
      var argElement = valueArguments.find { it.getArgumentName()?.text == potentialContentArg.name.toString() }
      if (argElement == null) {
        // try to detect the case when content lambda is last value argument
        if (potentialContentArg.index == resolvedCall.valueArguments.size - 1) {
          argElement = valueArguments.lastOrNull()
        }
      }

      return when (argElement) {
        is KtLambdaArgument -> argElement.getLambdaExpression()
        is KtValueArgument -> argElement.getArgumentExpression() as? KtLambdaExpression?
        else -> null
      }
    }
  }

  private fun reportError(node: KtIfExpression, layoutCall: KtCallExpression) {
    report(
      CodeSmell(
        issue,
        Entity.from(node),
        "Condition could be lifted out of \"${layoutCall.calleeExpression?.text ?: "unknown"}\""
      )
    )
  }
}
