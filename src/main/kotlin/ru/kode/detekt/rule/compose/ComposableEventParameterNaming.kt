/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import ru.kode.detekt.rule.compose.node.isEventHandler

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
class ComposableEventParameterNaming(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    "Checks Composable event parameters naming",
    Debt.FIVE_MINS
  )

  override fun visitNamedFunction(function: KtNamedFunction) {
    if (function.hasAnnotation("Composable")) {
      function.valueParameters.filter { it.isEventHandler() }.forEach { parameter ->
        val name = parameter.name!!
        if (!name.startsWith("on")) {
          reportError(parameter)
        } else if (name.endsWith("ed")) {
          reportError(parameter)
        }
      }
    }
  }

  private fun reportError(node: KtParameter) {
    val name = node.name!!
    val usesPastTense = name.startsWith("on") && name.endsWith("ed")
    report(
      CodeSmell(
        issue,
        Entity.from(node),
        if (usesPastTense) {
          "Invalid event parameter name \"$name\". Do not use past tense. For example: \"onClicked\" → \"onClick\""
        } else {
          "Invalid event parameter name \"$name\". Use names like \"onClick\", \"onValueChange\" etc"
        }
      )
    )
  }
}
