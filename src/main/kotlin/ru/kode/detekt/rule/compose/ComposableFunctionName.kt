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
import io.gitlab.arturbosch.detekt.rules.isOverride
import org.jetbrains.kotlin.psi.KtNamedFunction

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

 * Compliant:
 * ```
 * @Composable
 * fun value(): Int = ...
 * ```
 *
 * **See also: [Compose api guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md#naming-unit-composable-functions-as-entities)
 */
class ComposableFunctionName(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    "Incorrect composable function name",
    Debt.FIVE_MINS,
  )

  override fun visitNamedFunction(function: KtNamedFunction) {
    super.visitNamedFunction(function)
    if (function.isOverride()) return
    if (!function.hasAnnotation("Composable")) return

    if (function.returnsUnit()) {
      reportIfStartWithLowerCase(function)
    } else {
      reportIfStartWithUpperCase(function)
    }
  }

  private fun reportIfStartWithLowerCase(function: KtNamedFunction) {
    val name = function.name ?: return
    if (name.first().isLowerCase()) {
      report(CodeSmell(issue, Entity.atName(function), "Composable function '$name' should start with upper case"))
    }
  }

  private fun reportIfStartWithUpperCase(function: KtNamedFunction) {
    val name = function.name ?: return
    if (name.first().isUpperCase()) {
      report(CodeSmell(issue, Entity.atName(function), "Composable function '$name' should start with lower case"))
    }
  }
}

private fun KtNamedFunction.returnsUnit(): Boolean {
  val typeReference = typeReference
  return if (typeReference == null) {
    // I'm assuming that any function without block body and not defined type should not return `Unit`.
    // I think that's a safe call. This is the best we can do without type solving
    hasBlockBody()
  } else {
    typeReference.text == "Unit"
  }
}
