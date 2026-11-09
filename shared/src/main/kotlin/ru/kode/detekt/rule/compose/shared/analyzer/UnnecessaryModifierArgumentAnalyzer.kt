package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic

data class UnnecessaryModifierArgumentOptions(
  val modifierClassPackage: String = "androidx.compose.ui",
)

/**
 * Reports a `Modifier` argument passed to a parameter whose default value is `Modifier` already.
 *
 * Non-compliant:
 * ```
 * @Composable
 * fun Title(text: String, modifier: Modifier = Modifier) {}
 *
 * @Composable
 * fun Test() {
 *   Title("Hello", Modifier)
 *   Title("Hello", modifier = Modifier)
 * }
 * ```
 *
 * Compliant:
 * ```
 * @Composable
 * fun Test(modifier: Modifier = Modifier) {
 *   Title("Hello")
 *   Title("Hello", Modifier.padding(4.dp))
 *   Title("Hello", modifier)
 * }
 * ```
 *
 * A positional argument followed by another positional one can't be removed alone, the following arguments have to
 * be passed by name then: `Card("Hello", Modifier, false)` becomes `Card("Hello", enabled = false)`.
 *
 * Only the functions declared in the linted sources are checked, the default values of a library function are not
 * known.
 */
class UnnecessaryModifierArgumentAnalyzer(
  private val options: UnnecessaryModifierArgumentOptions,
  private val semantic: ComposeSemantic,
) {
  // An overload without the parameter (`Title(text: String)`) is not looked for, there removing the argument
  // would call another function and the finding has to be suppressed. Resolve the call without the argument if that
  // turns up
  fun analyze(call: KtCallExpression): List<ComposeDiagnostic> {
    return call.valueArguments.mapNotNull { argument ->
      val expression = argument.getArgumentExpression()?.takeIf { it.isModifierCompanion() } ?: return@mapNotNull null
      val declaration = semantic.parameterOfArgument(call, expression) ?: return@mapNotNull null
      if (declaration.defaultValue?.isModifierCompanion() != true) return@mapNotNull null
      // a constructor has the name of its class
      val functionName = declaration.ownerFunction?.name
      // the positional arguments after a removed one would move to other parameters, a trailing lambda would not
      val hasPositionalFollower = !argument.isNamed() &&
        call.valueArgumentList?.arguments.orEmpty().dropWhile { it !== argument }.drop(1).any { !it.isNamed() }

      ComposeDiagnostic(
        "\"${expression.text}\" is the default value of the \"${declaration.name}\" parameter of " +
          "\"$functionName\". Remove the argument" +
          if (hasPositionalFollower) " and pass the following arguments by name" else "",
        argument,
      )
    }
  }

  /** Whether the expression is `Modifier` itself, also when fully qualified or parenthesized. */
  private fun KtExpression.isModifierCompanion(): Boolean {
    val expression = KtPsiUtil.safeDeparenthesize(this)
    val name = (expression as? KtDotQualifiedExpression)?.selectorExpression ?: expression
    return (name as? KtNameReferenceExpression)?.getReferencedName() == "Modifier" &&
      semantic.isInstanceOf(expression, "${options.modifierClassPackage}.Modifier.Companion")
  }
}
