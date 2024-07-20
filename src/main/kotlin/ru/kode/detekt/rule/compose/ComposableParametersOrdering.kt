package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Location
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import io.gitlab.arturbosch.detekt.rules.identifierName
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import ru.kode.detekt.rule.compose.node.isComposableSlot
import ru.kode.detekt.rule.compose.node.isLambda
import ru.kode.detekt.rule.compose.node.isModifier

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
class ComposableParametersOrdering(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    "Checks Composable function parameter ordering",
    Debt.FIVE_MINS,
  )

  override fun visitNamedFunction(function: KtNamedFunction) {
    if (!function.hasAnnotation("Composable")) {
      return
    }
    //
    // Checking parameter order:
    // 1) required parameters
    // 2) modifier parameter
    // 3) optional parameters
    // 4) composable slots / event handlers
    //
    val valueParameters = function.valueParameters.dropLastWhile { it.isLambda() }
    val lastRequiredIndex = valueParameters.indexOfLast { !it.hasDefaultValue() }
    val firstOptionalIndex = valueParameters.indexOfFirst { it.hasDefaultValue() }
    val lastOptionalIndex = valueParameters.indexOfLast { it.hasDefaultValue() }
    val firstComposableSlotIndex = function.valueParameters.indexOfFirst { it.isComposableSlot() }
    if (firstOptionalIndex in 0 until lastRequiredIndex) {
      reportRequiredOptionalOrderError(valueParameters[firstOptionalIndex])
    } else if (firstComposableSlotIndex >= 0 &&
      (firstComposableSlotIndex < lastRequiredIndex || firstComposableSlotIndex < lastOptionalIndex)
    ) {
      reportRequiredOptionalOrderError(valueParameters[firstComposableSlotIndex])
    } else {
      //
      // Modifier parameter position
      //
      val modifierIndex = valueParameters.indexOfFirst { it.isModifier() }
      if (modifierIndex >= 0) {
        val lastRequiredNonModifierIndex = valueParameters.indexOfLast { !it.hasDefaultValue() && !it.isModifier() }
        val firstOptionalNonModifierIndex = valueParameters.indexOfFirst { it.hasDefaultValue() && !it.isModifier() }
        when {
          lastRequiredNonModifierIndex >= 0 && modifierIndex != lastRequiredNonModifierIndex + 1 -> {
            reportModifierParameterPositionError(function)
          }

          firstOptionalNonModifierIndex >= 0 && modifierIndex != firstOptionalNonModifierIndex - 1 -> {
            reportModifierParameterPositionError(function)
          }
        }
      }
    }
  }

  private fun reportRequiredOptionalOrderError(node: KtParameter) {
    report(
      CodeSmell(
        issue,
        Entity.from(node),
        "Composable function parameters should follow this order: required parameters, modifier parameter, " +
          "optional parameters, composable slots",
      ),
    )
  }

  private fun reportModifierParameterPositionError(function: KtNamedFunction) {
    val valueParameters = function.valueParameters.dropLastWhile { it.isLambda() }
    val firstOptional = valueParameters.firstOrNull { it.hasDefaultValue() }
    val lastRequired = valueParameters.filterNot { it.isModifier() }.lastOrNull { !it.hasDefaultValue() }
    report(
      CodeSmell(
        issue,
        Entity.from(function, Location.from(valueParameters.first { it.isModifier() })),
        if (firstOptional != null && lastRequired == null) {
          "Modifier parameter should be the first optional parameter" +
            " (put it before \"${firstOptional.identifierName()}\")"
        } else if (lastRequired != null) {
          "Modifier parameter should be the first optional parameter after required parameters" +
            " (put it after \"${lastRequired.identifierName()}\")"
        } else {
          "Modifier parameter must be a first optional parameter"
        },
      ),
    )
  }
}
