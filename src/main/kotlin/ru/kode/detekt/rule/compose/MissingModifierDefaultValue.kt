package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import ru.kode.detekt.rule.compose.node.isModifier

/**
 * Checks that the `modifier` parameter of a Composable function has a default value.
 *
 * Non-compliant:
 *
 * ```
 * fun Content(modifier: Modifier) {
 *   Text("Greetings")
 * }
 * ```
 *
 * Compliant:
 *
 * ```
 * fun Content(modifier: Modifier = Modifier) {
 *   Text("Greetings")
 * }
 * ```
 */
class MissingModifierDefaultValue(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    "Checks that Modifier parameter has a default value",
    Debt.FIVE_MINS
  )

  override fun visitNamedFunction(function: KtNamedFunction) {
    if (function.hasAnnotation("Composable")) {
      val modifierParameter = function.valueParameters.find { it.isModifier() }
      if (modifierParameter?.hasDefaultValue() == false) {
        reportError(modifierParameter)
      }
    }
  }

  private fun reportError(node: KtParameter) {
    report(
      CodeSmell(
        issue,
        Entity.from(node),
        "Modifier parameter should have a default value: \"modifier = Modifier\""
      )
    )
  }
}
