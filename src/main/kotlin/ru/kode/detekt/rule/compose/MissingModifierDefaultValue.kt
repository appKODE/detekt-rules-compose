package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity

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
}
