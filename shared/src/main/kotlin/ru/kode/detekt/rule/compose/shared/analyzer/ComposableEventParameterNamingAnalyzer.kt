package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed
import ru.kode.detekt.rule.compose.shared.isEventHandler

private val PRESENT_TENSE_VERBS_ENDING_IN_ED = setOf(
  "Bed", "Bleed", "Embed", "Exceed", "Feed", "Heed", "Need", "Proceed",
  "Seed", "Shed", "Shred", "Sled", "Speed", "Succeed", "Wed", "Weed",
)

private val OFFICIAL_PAST_TENSE_EVENT_NAMES = setOf("onFocusChanged", "onPlaced")

/**
 * Checks that event parameters of Composable functions have proper naming
 *
 * Wrong:
 *
 * ```
 * Button(
 *   somethingClicked = { ... }
 * )
 * ```
 *
 * Correct:
 *
 * ```
 * Button(
 *   onSomethingClick = { ... }
 * )
 * ```
 */
class ComposableEventParameterNamingAnalyzer {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    return function.valueParameters
      .filter { it.isEventHandler() }
      .mapNotNull { parameter ->
        val name = parameter.name ?: return@mapNotNull null
        when {
          !name.startsWith("on") -> {
            ComposeDiagnostic(
              "Invalid event parameter name \"$name\". Use names like \"onClick\", \"onValueChange\" etc",
              parameter,
            )
          }

          name.endsWith("ed") &&
            name !in OFFICIAL_PAST_TENSE_EVENT_NAMES &&
            PRESENT_TENSE_VERBS_ENDING_IN_ED.none { name.endsWith(it) } -> {
            ComposeDiagnostic(
              "Invalid event parameter name \"$name\". Do not use past tense. For example: \"onClicked\" → \"onClick\"",
              parameter,
            )
          }

          else -> null
        }
      }
  }
}
