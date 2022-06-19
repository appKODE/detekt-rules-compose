package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import io.gitlab.arturbosch.detekt.rules.identifierName
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import ru.kode.detekt.rule.compose.node.isComposableSlot

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
    Debt.FIVE_MINS
  )

  override fun visitNamedFunction(function: KtNamedFunction) {
    if (function.hasAnnotation("Composable")) {
      checkRequiredOptionalParametersOrdering(function)
      if (CHECK_IN_PRESENCE_OF_SLOTS_ENABLED && findings.isEmpty()) {
        checkSlotParametersOrdering(function)
      }
    }
  }

  private fun checkSlotParametersOrdering(function: KtNamedFunction) {
    // assuming we get here only after optional/required are placed correctly
    checkSlotParametersOrdering(function.valueParameters.filter { !it.hasDefaultValue() })
    checkSlotParametersOrdering(function.valueParameters.filter { it.hasDefaultValue() })
  }

  private fun checkSlotParametersOrdering(valueParameters: List<KtParameter>) {
    val firstSlotParameterIndex = valueParameters.indexOfFirst { it.isComposableSlot() }
    val lastNonSlotParameterIndex = valueParameters.indexOfLast { !it.isComposableSlot() }
    if (firstSlotParameterIndex in 0 until lastNonSlotParameterIndex) {
      reportSlotOrderError(valueParameters[firstSlotParameterIndex], valueParameters[lastNonSlotParameterIndex])
    }
  }

  private fun checkRequiredOptionalParametersOrdering(function: KtNamedFunction) {
    // Should we drop last required slots from the check to allow more convenient calls?
    //
    // Test("hello") { Content() }
    //
    // instead of more verbose (especially in presence of a lot of arguments)
    //
    // Test("hello", content = { Content() })
    //
    // (assuming `Test` has required + optional parameters + trailing required lambda)
    //
    // I.e. below we could do
    //
    // val valueParameters = function.valueParameters.dropLastWhile { it.isComposableSlot() || it.isEventHandler() }
    //
    // Seems to work, but could be not the best way to go!
    //
    // See (grep for) NOTE_ALLOWING_REQUIRED_TRAILING_SLOT_SPECIAL_CASE for details
    //
    if (function.valueParameters.any { it.isComposableSlot() } && !CHECK_IN_PRESENCE_OF_SLOTS_ENABLED) {
      return
    }
    val valueParameters = function.valueParameters
    val lastRequiredIndex = valueParameters.indexOfLast { !it.hasDefaultValue() }
    val firstOptionalIndex = valueParameters.indexOfFirst { it.hasDefaultValue() }
    if (firstOptionalIndex in 0 until lastRequiredIndex) {
      reportRequiredOptionalOrderError(valueParameters[firstOptionalIndex])
    }
  }

  private fun reportRequiredOptionalOrderError(node: KtParameter) {
    report(
      CodeSmell(
        issue,
        Entity.from(node),
        "Optional parameters should be placed after required parameters"
      )
    )
  }

  private fun reportSlotOrderError(slotNode: KtParameter, nonSlotNode: KtParameter) {
    val modifier = if (slotNode.hasDefaultValue()) "Optional" else "Required"
    report(
      CodeSmell(
        issue,
        Entity.from(slotNode),
        "$modifier slot parameters should be placed last in ${modifier.toLowerCaseAsciiOnly()} " +
          "parameter list (put \"${slotNode.identifierName()}\" after \"${nonSlotNode.identifierName()}\")"
      )
    )
  }
}

// Currently disabled
// See (grep in project) NOTE_ALLOWING_REQUIRED_TRAILING_SLOT_SPECIAL_CASE
// for details
internal const val CHECK_IN_PRESENCE_OF_SLOTS_ENABLED = false
