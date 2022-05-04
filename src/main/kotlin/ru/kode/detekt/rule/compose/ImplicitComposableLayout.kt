package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.DetektVisitor
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import org.jetbrains.kotlin.com.google.gwt.dev.js.rhino.Context.reportError
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * Reports composables with a missing root layout
 *
 * Wrong:
 *
 * ```
 * @Composable
 * fun Header() {
 *   Text("hello")
 *   Text("world")
 * }
 * ```
 *
 * Correct:
 *
 * ```
 * @Composable
 * fun Header() {
 *   Column {
 *     Text("hello")
 *     Text("world")
 *   }
 * }
 * ```
 */
class ImplicitComposableLayout(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    "Reports composables with a missing root layout",
    Debt.FIVE_MINS
  )

  override fun visitNamedFunction(function: KtNamedFunction) {
    if (function.hasAnnotation("Composable")) {
      function.bodyExpression?.accept(ComposableBodyVisitor())
    }
  }

  private class ComposableBodyVisitor : DetektVisitor() {
    override fun visitCallExpression(expression: KtCallExpression) {
      super.visitCallExpression(expression)
    }
  }

}
