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
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import io.gitlab.arturbosch.detekt.rules.isOverride
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Checks that composable function is defined as a top-level function.
 *
 * `allowInObjects` config property can be used to control if usage of composable functions
 * in `object` is permitted.
 *
 * Non-compliant:
 *
 * ```
 * interface Screen {
 *   @Composable
 *   fun Content(modifier: Modifier = Modifier)
 * }
 *
 * class ScreenImpl : Screen {
 *   @Composable
 *   override fun Content(modifier: Modifier) {
 *     Text("Greetings", modifier.fillMaxSize())
 *   }
 * }
 * ```
 *
 * Compliant:
 *
 * ```
 * fun ScreenContent(modifier: Modifier = Modifier) {
 *   Text("Greetings", modifier.fillMaxSize())
 * }
 * ```
 */
class TopLevelComposableFunctions(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    "Checks that composable function is defined as a top-level function",
    Debt.FIVE_MINS,
  )

  private val allowInObjects by config(defaultValue = false)

  override fun visitNamedFunction(function: KtNamedFunction) {
    if (function.hasAnnotation("Composable")) {
      // do not report overridden functions to reduce "error spam": presumably the "parent" class will be reported
      // too and that's enough
      if (!function.isTopLevel && !function.isOverride() &&
        (function.containingClassOrObject !is KtObjectDeclaration || !allowInObjects)
      ) {
        reportError(function)
      }
    }
  }

  private fun reportError(node: KtNamedFunction) {
    report(
      CodeSmell(
        issue,
        Entity.from(node),
        "Composable functions should be defined as top-level functions",
      ),
    )
  }
}
