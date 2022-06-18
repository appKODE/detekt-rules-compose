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
import ru.kode.detekt.rule.compose.node.isEventParameter

/**
 * Checks that parameters of Composable functions have a correct order:
 *
 * 1. Required parameters come first
 * 2. Optional parameters come after required
 * 3. Required composable slot parameters come after required non-slot parameters
 * 4. Optional composable slot parameters come after optional non-slot parameters
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
 *
 * For composable slots:
 *
 * Non-compliant:
 *
 * ```
 * Header(
 *   title: String,
 *   content: @Composable () -> Unit,
 *   description: String,
 *   subContent: (@Composable () -> Unit)? = null,
 *   subtitle: String? = null,
 * )
 * ```
 *
 * Compliant:
 *
 * ```
 * Header(
 *   title: String,
 *   description: String,
 *   content: @Composable () -> Unit,
 *   subtitle: String? = null,
 *   subContent: (@Composable () -> Unit)? = null,
 * )
 * ```
 *
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
      if (findings.isEmpty()) {
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
    val valueParameters = function.valueParameters.dropLastWhile { it.isComposableSlot() || it.isEventParameter() }
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
