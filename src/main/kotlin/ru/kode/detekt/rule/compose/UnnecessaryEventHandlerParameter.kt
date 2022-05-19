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
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * Checks that event handlers of Composable do not have unnecessary parameter which could be provided by parent.
 * This makes individual components less coupled to the structure of their parameter and leaves that to the parent,
 * and in turn this often leads to simplification of composable.
 *
 * Wrong:
 *
 * ```
 * data class Data(id: Int, title: String)
 *
 * fun Component(data: Data, somethingClicked: (Int) -> Unit) {
 *   Button(onClick = { somethingClicked(data.id) })
 * }
 *
 * fun Parent() {
 *   val data = Data(id = 3, title = "foo")
 *   Component(data = data, somethingClicked = { id -> process(id) })
 * }
 * ```
 *
 * Correct:
 *
 * ```
 * data class Data(id: Int, title: String)
 *
 * fun Component(data: Data, somethingClicked: () -> Unit) {
 *   Button(onClick = somethingClicked)
 * }
 *
 * fun Parent() {
 *   val data = Data(id = 3, title = "foo")
 *   Component(data = data, somethingClicked = { process(data.id) })
 * }
 * ```
 */
class UnnecessaryEventHandlerParameter(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    "Checks for unnecessary event handler parameters",
    Debt.FIVE_MINS
  )

  override fun visitNamedFunction(function: KtNamedFunction) {
    if (function.hasAnnotation("Composable")) {
      val stateParameters = function.valueParameters.filter { it.isStateParameter() }
      val eventParameters = function.valueParameters.filter { it.isEventParameter() }
      if (stateParameters.isNotEmpty()) {
        function.bodyExpression?.accept(UnnecessaryHandlerArgumentsVisitor(stateParameters, eventParameters))
      }
    }
  }

  private fun KtParameter.isStateParameter(): Boolean {
    return !this.isEventParameter()
  }

  private fun KtParameter.isEventParameter(): Boolean {
    val firstChild = this.children.first { it is KtTypeReference }
    if ((firstChild as KtTypeReference).hasAnnotation("Composable")) return false
    return firstChild.typeElement is KtFunctionType &&
      (firstChild.typeElement as KtFunctionType).returnTypeReference?.text == "Unit"
  }

  private inner class UnnecessaryHandlerArgumentsVisitor(
    private val stateParameters: List<KtParameter>,
    private val eventParameters: List<KtParameter>
  ) : DetektVisitor() {
    private val stateParameterNames = stateParameters.map { it.name }

    override fun visitCallExpression(expression: KtCallExpression) {
      super.visitCallExpression(expression)
      val eventParameterForCall = eventParameters.find { it.name == expression.calleeExpression?.text }
      if (eventParameterForCall != null) {
        expression.valueArguments.forEachIndexed { index, argument ->
          val argumentExpression = argument.getArgumentExpression()
          //   data -> argumentReceiverName == data
          //   data.id -> argumentReceiverName == data
          //   data.nested.id -> argumentReceiverName == data
          //   data.copy() -> null
          val argumentReceiverName = when {
            argumentExpression is KtDotQualifiedExpression &&
              argumentExpression.lastChild !is KtCallExpression -> {
              argumentExpression.text.takeWhile { it != '.' }
            }
            argumentExpression is KtNameReferenceExpression -> argument.text
            else -> null
          }
          if (argumentReceiverName != null && stateParameterNames.contains(argumentReceiverName)) {
            reportError(eventParameterForCall, argumentReceiverName, argumentIndex = index)
          }
        }
      }
    }

    private fun reportError(eventParameter: KtParameter, argumentReceiverName: String, argumentIndex: Int) {
      val type = ((eventParameter.children.first() as KtTypeReference).typeElement as KtFunctionType)
      val parameterList = type.parameters.filterIndexed { index, _ -> index != argumentIndex }
      report(
        CodeSmell(
          issue,
          Entity.from(eventParameter, Location.from(eventParameter)),
          "Unnecessary event callback arguments. Move all \"$argumentReceiverName\" access " +
            "to the parent composable event handler and switch \"${eventParameter.name}\" type to " +
            "\"${parameterList.joinToString(prefix = "(", postfix = ")", transform = { it.text })} -> Unit\""
        )
      )
    }
  }
}
