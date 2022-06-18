/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
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
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.node.isComposableSlot
import ru.kode.detekt.rule.compose.node.isModifier
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Checks that "modifier" argument for Composable function is passed as a first optional parameter.
 *
 * Wrong:
 * ```
 * fun Button(
 *   arrangement: Arrangement = Arrangement.spacedBy(12.dp),
 *   modifier: Modifier = Modifier.height(16.dp),
 * )
 * ```
 * Correct:
 * ```
 * fun Button(
 *   modifier: Modifier = Modifier.height(16.dp),
 *   arrangement: Arrangement = Arrangement.spacedBy(12.dp),
 * )
 * ```
 *
 * When required and optional parameters are present, `modifier` needs to be the first among the optional parameters:
 *
 * Wrong:
 * ```
 * fun Button(
 *   text: String,
 *   onClick: () -> Unit,
 *   arrangement: Arrangement = Arrangement.spacedBy(12.dp),
 *   modifier: Modifier = Modifier.height(16.dp),
 * )
 * ```
 * Correct:
 * ```
 * fun Button(
 *   text: String,
 *   onClick: () -> Unit,
 *   modifier: Modifier = Modifier.height(16.dp),
 *   arrangement: Arrangement = Arrangement.spacedBy(12.dp),
 * )
 * ```
 */
class ModifierParameterPosition(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    "Reports incorrect modifier parameter position",
    Debt.FIVE_MINS
  )

  private val incorrectPositions = Collections.newSetFromMap(IdentityHashMap<KtNamedFunction, Boolean>())

  override fun visitNamedFunction(function: KtNamedFunction) {
    if (function.hasAnnotation("Composable")) {
      checkFunction(function)
    }
  }

  private fun checkFunction(function: KtNamedFunction) {
    if (function.valueParameters.any { it.isComposableSlot() && !it.hasDefaultValue() }) {
      // there's no point in enforcing modifier-after-last-required in presence of required composable lambda slots:
      //  putting modifier after "content" slot would break "trailing lambda" syntax, and even if we would make
      //  an exception and require to put modifier before the slot, it still accomplishes nothing, because slot is
      //  a required-argument and so named arguments syntax would be needed anyway
      return
    }
    val valueParameters = function.valueParameters
    val modifierPosition = valueParameters.indexOfFirst { it.isModifier() }
    if (modifierPosition >= 0) {
      val firstOptionalPosition = valueParameters.indexOfFirst { it.hasDefaultValue() && !it.isModifier() }
      val lastRequiredPosition = valueParameters.indexOfLast { !it.hasDefaultValue() && !it.isModifier() }
      when {
        lastRequiredPosition >= 0 && modifierPosition != lastRequiredPosition + 1 -> {
          incorrectPositions.add(function)
        }
        firstOptionalPosition >= 0 && modifierPosition != firstOptionalPosition - 1 -> {
          incorrectPositions.add(function)
        }
      }
    }
  }

  override fun preVisit(root: KtFile) {
    incorrectPositions.clear()
  }

  override fun postVisit(root: KtFile) {
    incorrectPositions.forEach { node ->
      val valueParameters = node.valueParameters
      val firstOptional = valueParameters.firstOrNull { it.hasDefaultValue() }
      val lastRequired = valueParameters
        .filterNot { it.isModifier() }.lastOrNull { !it.hasDefaultValue() }
      report(
        CodeSmell(
          issue,
          Entity.from(node, Location.from(valueParameters.first { it.isModifier() })),
          if (firstOptional != null && lastRequired == null) {
            "Modifier parameter should be the first optional parameter" +
              " (put it before \"${firstOptional.identifierName()}\")"
          } else if (lastRequired != null) {
            "Modifier parameter should be the first parameter after required parameters" +
              " (put it after \"${lastRequired.identifierName()}\")"
          } else {
            "Modifier parameter must be a first optional parameter"
          }
        )
      )
    }
  }
}
