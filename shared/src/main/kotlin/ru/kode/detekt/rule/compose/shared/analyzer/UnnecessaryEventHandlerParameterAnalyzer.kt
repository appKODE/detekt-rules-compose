package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic
import ru.kode.detekt.rule.compose.shared.functionTypeOrNull
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed
import ru.kode.detekt.rule.compose.shared.isEventHandler

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
class UnnecessaryEventHandlerParameterAnalyzer(
  private val semantic: ComposeSemantic,
) {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    val stateParameters = function.valueParameters.filter { !it.isEventHandler() }
    val eventParameters = function.valueParameters.filter { it.isEventHandler() }
    if (stateParameters.isEmpty()) return emptyList()

    val stateParameterNames = stateParameters.mapNotNull { it.name }.toSet()
    val diagnostics = mutableListOf<ComposeDiagnostic>()

    function.bodyExpression?.accept(
      object : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          super.visitCallExpression(expression)

          val eventParameterForCall = eventParameters.find { it.name == expression.calleeExpression?.text }
            ?: return

          expression.valueArguments.forEachIndexed { index, argument ->
            val argumentExpression = argument.getArgumentExpression()
            val argumentReceiverName = when (argumentExpression) {
              is KtDotQualifiedExpression -> argumentExpression.receiverNameUnlessSealed()
              is KtNameReferenceExpression -> argumentExpression.getReferencedName()
              else -> null
            }

            if (argumentReceiverName != null &&
              argumentReceiverName in stateParameterNames &&
              !argument.isDeclaredBetween(argumentReceiverName, function)
            ) {
              diagnostics += buildDiagnostic(eventParameterForCall, argumentReceiverName, index)
            }
          }
        }

        private fun KtDotQualifiedExpression.receiverNameUnlessSealed(): String? {
          if (lastChild is KtCallExpression) return null
          return if (semantic.receiverHasSealedTypeOrSupertype(receiverExpression)) {
            null
          } else {
            text.takeWhile { it != '.' }
          }
        }
      },
    )

    return diagnostics
  }

  private fun buildDiagnostic(
    eventParameter: KtParameter,
    argumentReceiverName: String,
    argumentIndex: Int,
  ): ComposeDiagnostic {
    val functionType = eventParameter.functionTypeOrNull()
    val parameterList = functionType
      ?.parameters
      ?.filterIndexed { index, _ -> index != argumentIndex }
      .orEmpty()
    val suggestedType = "${parameterList.joinToString(prefix = "(", postfix = ")", transform = { it.text })} -> Unit"
      .let { if (eventParameter.typeReference?.typeElement is KtNullableType) "($it)?" else it }

    return ComposeDiagnostic(
      "Unnecessary event callback arguments. Move all \"$argumentReceiverName\" access " +
        "to the parent composable event handler and switch \"${eventParameter.name}\" type to " +
        "\"$suggestedType\"",
      eventParameter,
    )
  }
}
