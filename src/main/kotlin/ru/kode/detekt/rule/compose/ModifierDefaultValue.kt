package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity

/**
 * Checks that the `modifier` parameter of a Composable function has the correct default value.
 *
 * Using a default value other than `Modifier` can lead to various non-obvious issues and inconveniences.
 *
 * Non-compliant:
 *
 * ```
 * fun Content(modifier: Modifier = Modifier.fillMaxSize()) {
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
class ModifierDefaultValue(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    "Checks that Modifier parameter has a correct default value",
    Debt.FIVE_MINS
  )
}
