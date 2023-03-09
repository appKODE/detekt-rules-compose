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
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import io.gitlab.arturbosch.detekt.rules.fqNameOrNull
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import ru.kode.detekt.rule.compose.node.isModifier

/**
 * Reports errors when reusing the modifier instance on a wrong level of composable hierarchy, for example:
 *
 * ```kotlin
 * @Composable
 * fun MyComposable(modifier: Modifier) {
 *   Row(modifier = Modifier.height(30.dp)) {
 *     Column(modifier = modifier.width(20.dp)) {
 *     }
 *   }
 * }
 * ```
 *
 * Above code is wrong, and `modifier` parameter should be used on the top Composable:
 *
 * ```kotlin
 * @Composable
 * fun MyComposable(modifier: Modifier) {
 *   Row(modifier = modifier.height(30.dp)) {
 *     Column(modifier = Modifier.width(20.dp)) {
 *     }
 *   }
 * }
 * ```
 */
@RequiresTypeResolution
class ReusedModifierInstance(
  config: Config = Config.empty,
  // this parameter is used in tests to pass another package
  private val modifierClassPackage: String = "androidx.compose.ui"
) : Rule(config) {
  override val issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    "Reports errors in using modifier on wrong level of composable hierarchy",
    Debt.FIVE_MINS
  )

  override fun visitNamedFunction(function: KtNamedFunction) {
    if (function.hasAnnotation("Composable") && function.valueParameters.any { it.isModifier() }) {
      function.bodyBlockExpression?.getChildrenOfType<KtCallExpression>()?.forEach {
        it.accept(ChildComposableCallsVisitor())
      }
    }
    super.visitNamedFunction(function)
  }

  private inner class ChildComposableCallsVisitor : DetektVisitor() {
    override fun visitCallExpression(expression: KtCallExpression) {
      if (expression.getResolvedCall(bindingContext)?.hasModifierParameter() == true) {
        val contentLambdaExpression = expression.valueArguments
          .find { it.getArgumentExpression() is KtLambdaExpression }
          ?.getArgumentExpression() as KtLambdaExpression?
        contentLambdaExpression?.bodyExpression?.accept(ChildrenWithModifiersVisitor())
      } else {
        super.visitCallExpression(expression)
      }
    }

    private fun ResolvedCall<out CallableDescriptor>.hasModifierParameter(): Boolean {
      return this.valueArguments.any { it.key.type.fqNameOrNull()?.asString() == "$modifierClassPackage.Modifier" }
    }
  }

  private inner class ChildrenWithModifiersVisitor : DetektVisitor() {
    override fun visitCallExpression(expression: KtCallExpression) {
      val modifierArgExpression = expression.valueArguments
        .find { it.getArgumentExpression()?.isModifierChainExpression() == true }
        ?.getArgumentExpression()
      if (modifierArgExpression != null) {
        if (modifierArgExpression.text.startsWith("modifier")) {
          reportError(expression)
        }
      }
      super.visitCallExpression(expression)
    }

    private fun KtExpression.isModifierChainExpression(): Boolean {
      return when (this) {
        is KtDotQualifiedExpression -> this.text.startsWith("Modifier") || this.text.startsWith("modifier")
        is KtReferenceExpression -> this.text == "modifier"
        else -> false
      }
    }

    private fun reportError(node: KtCallExpression) {
      report(
        CodeSmell(
          issue,
          Entity.from(node),
          "Composable uses \"modifier\" on the wrong level, non-direct children should use \"Modifier\""
        )
      )
    }
  }
}
