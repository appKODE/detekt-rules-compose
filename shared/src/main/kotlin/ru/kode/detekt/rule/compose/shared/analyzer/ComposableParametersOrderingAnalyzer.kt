package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed
import ru.kode.detekt.rule.compose.shared.isActualLike
import ru.kode.detekt.rule.compose.shared.isComposableSlot
import ru.kode.detekt.rule.compose.shared.isEventHandler
import ru.kode.detekt.rule.compose.shared.isLambda
import ru.kode.detekt.rule.compose.shared.isModifier
import ru.kode.detekt.rule.compose.shared.isOverrideLike

/**
 * Checks that parameters of Composable functions have a correct order:
 *
 * 1. Required parameters come first
 * 2. Optional parameters come after required, the `modifier` parameter first among them
 * 3. Slots named in [trailingSlotNames] (`content` by default) come last, so they can be passed as a trailing lambda
 *
 * Other composable slots are not forced to the end: a required slot can stay among the required parameters and an
 * optional one among the optional parameters. Trailing lambdas may follow the optional parameters, but only the last
 * parameter may be a required composable slot there.
 *
 * With [allowTrailingEventHandlers] turned off a required event handler (a non-composable lambda without a receiver
 * returning `Unit`) is reported when it follows optional parameters, unless it is named in [trailingSlotNames].
 *
 * Overriding and `actual` functions are not checked: their order is dictated by the overridden or `expect` declaration.
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
class ComposableParametersOrderingAnalyzer(
  private val trailingSlotNames: List<String> = listOf("content"),
  private val allowTrailingEventHandlers: Boolean = true,
) {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()
    // the order is dictated by the overridden or the expect declaration
    if (function.isOverrideLike() || function.isActualLike()) return emptyList()

    val valueParameters = function.valueParameters.dropLastWhile { it.isLambda() }
    val lastRequiredIndex = valueParameters.indexOfLast { !it.hasDefaultValue() }
    val firstOptionalIndex = valueParameters.indexOfFirst { it.hasDefaultValue() }

    if (firstOptionalIndex in 0 until lastRequiredIndex) {
      val node = valueParameters[firstOptionalIndex]
      return listOf(
        ComposeDiagnostic(
          "Composable function parameters should follow this order: required parameters, modifier parameter, " +
            "optional parameters",
          node,
        ),
      )
    }

    if (!allowTrailingEventHandlers) {
      val firstOptionalParameterIndex = function.valueParameters.indexOfFirst { it.hasDefaultValue() }
      val misplacedEventHandler = function.valueParameters.withIndex().firstOrNull { (index, parameter) ->
        firstOptionalParameterIndex in 0 until index && parameter.isEventHandler() &&
          !parameter.hasDefaultValue() && parameter.name !in trailingSlotNames
      }?.value
      if (misplacedEventHandler != null) {
        return listOf(
          ComposeDiagnostic(
            "Required event handler \"${misplacedEventHandler.name}\" should be placed before optional parameters",
            misplacedEventHandler,
          ),
        )
      }
    }

    val lastParameter = function.valueParameters.lastOrNull()
    val misplacedTrailingSlot = function.valueParameters.firstOrNull {
      it != lastParameter && it.name in trailingSlotNames && it.isLambda()
    }
    if (misplacedTrailingSlot != null) {
      return listOf(
        ComposeDiagnostic("Slot \"${misplacedTrailingSlot.name}\" should be the last parameter", misplacedTrailingSlot),
      )
    }

    // only the last parameter can be passed as a trailing lambda, other required slots belong to required parameters
    val firstDefaultIndex = function.valueParameters.indexOfFirst { it.hasDefaultValue() }
    val misplacedRequiredSlot = function.valueParameters.withIndex().firstOrNull { (index, parameter) ->
      firstDefaultIndex in 0 until index && parameter != lastParameter &&
        parameter.isComposableSlot() && !parameter.hasDefaultValue()
    }?.value
    if (misplacedRequiredSlot != null) {
      return listOf(
        ComposeDiagnostic(
          "Required composable slot \"${misplacedRequiredSlot.name}\" after optional parameters should be the " +
            "last parameter",
          misplacedRequiredSlot,
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
