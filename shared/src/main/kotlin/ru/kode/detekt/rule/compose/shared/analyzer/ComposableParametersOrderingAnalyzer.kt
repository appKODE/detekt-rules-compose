package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed
import ru.kode.detekt.rule.compose.shared.isComposableSlot
import ru.kode.detekt.rule.compose.shared.isLambda
import ru.kode.detekt.rule.compose.shared.isModifier

/**
 * Checks that parameters of Composable functions have a correct order:
 *
 * 1. Required parameters come first
 * 2. Optional parameters come after required
 *
 * Non-compliant:
 *
 * ```
 * Header(
 *   title: String,
 *   enabled: Boolean = false,
 *   description: String,
 * )
 * ```
 *
 * Compliant:
 *
 * ```
 * Header(
 *   title: String,
 *   description: String,
 *   enabled: Boolean = false,
 * )
 * ```
 */
class ComposableParametersOrderingAnalyzer {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    val valueParameters = function.valueParameters.dropLastWhile { it.isLambda() }
    val lastRequiredIndex = valueParameters.indexOfLast { !it.hasDefaultValue() }
    val firstOptionalIndex = valueParameters.indexOfFirst { it.hasDefaultValue() }
    val lastOptionalIndex = valueParameters.indexOfLast { it.hasDefaultValue() }
    val firstComposableSlotIndex = function.valueParameters.indexOfFirst { it.isComposableSlot() }

    if (firstOptionalIndex in 0 until lastRequiredIndex) {
      val node = valueParameters[firstOptionalIndex]
      return listOf(
        ComposeDiagnostic(
          "Composable function parameters should follow this order: required parameters, modifier parameter, " +
            "optional parameters, composable slots",
          node,
        ),
      )
    }

    if (firstComposableSlotIndex >= 0 &&
      (firstComposableSlotIndex < lastRequiredIndex || firstComposableSlotIndex < lastOptionalIndex)
    ) {
      val node = valueParameters[firstComposableSlotIndex]
      return listOf(
        ComposeDiagnostic(
          "Composable function parameters should follow this order: required parameters, modifier parameter, " +
            "optional parameters, composable slots",
          node,
        ),
      )
    }

    val modifierIndex = valueParameters.indexOfFirst { it.isModifier() }
    if (modifierIndex < 0) return emptyList()

    val lastRequiredNonModifierIndex = valueParameters.indexOfLast { !it.hasDefaultValue() && !it.isModifier() }
    val firstOptionalNonModifierIndex = valueParameters.indexOfFirst { it.hasDefaultValue() && !it.isModifier() }

    val hasWrongPosition = when {
      lastRequiredNonModifierIndex >= 0 -> modifierIndex != lastRequiredNonModifierIndex + 1
      firstOptionalNonModifierIndex >= 0 -> modifierIndex != firstOptionalNonModifierIndex - 1
      else -> false
    }
    if (!hasWrongPosition) return emptyList()

    val firstOptional = valueParameters.firstOrNull { it.hasDefaultValue() }
    val lastRequired = valueParameters.filterNot { it.isModifier() }.lastOrNull { !it.hasDefaultValue() }
    val message = if (firstOptional != null && lastRequired == null) {
      "Modifier parameter should be the first optional parameter" +
        " (put it before \"${firstOptional.name ?: "parameter"}\")"
    } else if (lastRequired != null) {
      "Modifier parameter should be the first optional parameter after required parameters" +
        " (put it after \"${lastRequired.name ?: "parameter"}\")"
    } else {
      "Modifier parameter must be a first optional parameter"
    }

    return listOf(
      ComposeDiagnostic(
        message,
        valueParameters.first { it.isModifier() },
      ),
    )
  }
}
