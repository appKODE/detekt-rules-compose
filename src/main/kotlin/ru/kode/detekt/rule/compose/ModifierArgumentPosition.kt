/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.DetektVisitor
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Location
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Checks that "modifier" argument for Composable function is passed as a first parameter.
 *
 * Wrong:
 * ```
 * Button(
 *   arrangement = Vertical,
 *   modifier = Modifier,
 * )
 * ```
 * Correct:
 * ```
 * Button(
 *   modifier = Modifier,
 *   arrangement = Vertical,
 * )
 * ```
 */
class ModifierArgumentPosition(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    "Reports incorrect modifier argument position",
    Debt.FIVE_MINS
  )

  private val incorrectPositions = Collections.newSetFromMap(IdentityHashMap<KtCallExpression, Boolean>())

  override fun visitNamedFunction(function: KtNamedFunction) {
    if (function.hasAnnotation("Composable")) {
      function.bodyBlockExpression?.accept(ChildComposableFunctionCallsVisitor())
    }
  }

  override fun preVisit(root: KtFile) {
    incorrectPositions.clear()
  }

  override fun postVisit(root: KtFile) {
    incorrectPositions.forEach { node ->
      report(
        CodeSmell(
          issue,
          Entity.from(node, Location.from(node.valueArguments.first { it.isModifierArgument() })),
          "Modifier argument of composable function must always be first"
        )
      )
    }
  }

  inner class ChildComposableFunctionCallsVisitor : DetektVisitor() {
    override fun visitCallExpression(expression: KtCallExpression) {
      checkCallExpression(expression)
      super.visitCallExpression(expression)
    }

    private fun checkCallExpression(expression: KtCallExpression) {
      val position = expression.valueArguments.indexOfFirst { it.isModifierArgument() }
      if (position > 0) {
        incorrectPositions.add(expression)
      }
    }
  }

  private fun KtValueArgument.isModifierArgument() = getArgumentName()?.text == "modifier"
}
