package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed
import ru.kode.detekt.rule.compose.shared.isOverrideLike
import ru.kode.detekt.rule.compose.shared.returnsUnitLike

/**
 * The Composable functions that return Unit should start with upper-case while the ones that return a value should
 * start with lower case.
 *
 * Non-compliant:
 * ```
 * @Composable
 * fun button() {
 *   ...
 * }
 * ```
 * Correct:
 * ```
 * @Composable
 * fun Button() {
 *   ...
 * }
 * ```
 *
 * Non-compliant:
 * ```
 * @Composable
 * fun Value(): Int = ...
 * ```
 *
 * Compliant:
 * ```
 * @Composable
 * fun value(): Int = ...
 * ```
 *
 * **See also: [Compose api guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md#naming-unit-composable-functions-as-entities)
 */
class ComposableFunctionNameAnalyzer {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (function.isOverrideLike()) return emptyList()
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    val name = function.name ?: return emptyList()
    val anchor = function

    val returnsUnit = function.returnsUnitLike() ?: return emptyList()
    return if (returnsUnit) {
      if (name.first().isLowerCase()) {
        listOf(
          ComposeDiagnostic(
            "Composable function '$name' should start with upper case",
            anchor,
            atName = true,
          ),
        )
      } else {
        emptyList()
      }
    } else {
      if (name.first().isUpperCase()) {
        listOf(
          ComposeDiagnostic(
            "Composable function '$name' should start with lower case",
            anchor,
            atName = true,
          ),
        )
      } else {
        emptyList()
      }
    }
  }
}
