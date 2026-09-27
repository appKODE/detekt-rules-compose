package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed
import ru.kode.detekt.rule.compose.shared.isModifier

private val MODIFIER_COMPANION_SPELLINGS = setOf(
  "Modifier.Companion",
  "androidx.compose.ui.Modifier",
  "androidx.compose.ui.Modifier.Companion",
)

/**
 * Checks that the `modifier` parameter of a Composable function has the correct default value.
 *
 * Using a default value other than `Modifier` can lead to various non-obvious issues and inconveniences.
 *
 * Non-compliant:
 *
 * ```
 * fun Content(modifier: Modifier = Modifier.fillMaxSize()) {
 *   Text("Greetings", modifier) // fillMaxSize will be ignored here
 * }
 * ```
 *
 * Compliant:
 *
 * ```
 * fun Content(modifier: Modifier = Modifier) {
 *   Text("Greetings", modifier.fillMaxSize())
 * }
 * ```
 */
class ModifierDefaultValueAnalyzer {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    val modifierParameter = function.valueParameters.find { it.isModifier() } ?: return emptyList()
    val defaultValue = modifierParameter.defaultValue
    if (defaultValue is KtDotQualifiedExpression &&
      defaultValue.text.filterNot { it.isWhitespace() } !in MODIFIER_COMPANION_SPELLINGS
    ) {
      return listOf(
        ComposeDiagnostic(
          "Modifier parameter should not have a default value other than \"Modifier\"",
          modifierParameter,
        ),
      )
    }

    return emptyList()
  }
}
