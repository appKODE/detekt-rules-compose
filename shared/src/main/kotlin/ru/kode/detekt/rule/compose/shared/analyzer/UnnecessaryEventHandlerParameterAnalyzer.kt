package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
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
 *
 * A constant argument (a literal, a string without templates, a Kotlin `const val`, an enum entry or an object) is
 * reported the same way when every call of the event handler (`onClose(...)` or `onClose?.invoke(...)`) passes the
 * same constant at that position and the handler is used nowhere else (not passed on as a value): the parent already
 * knows it, so `onClose(Intent.Close)` should become `onClose()`. All such arguments of a handler are reported in one
 * finding. Different constants (`onCheckedChange(true)` and `onCheckedChange(false)`) are not reported.
 * Disabled with [reportConstantArguments].
 */
class UnnecessaryEventHandlerParameterAnalyzer(
  private val semantic: ComposeSemantic,
  private val reportConstantArguments: Boolean = true,
) {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    val stateParameters = function.valueParameters.filter { !it.isEventHandler() }
    val eventParameters = function.valueParameters.filter { it.isEventHandler() }
    val stateParameterNames = stateParameters.mapNotNull { it.name }.toSet()
    val diagnostics = mutableListOf<ComposeDiagnostic>()
    val eventCalls = mutableMapOf<KtParameter, MutableList<KtCallExpression>>()
    val eventsUsedOtherwise = mutableSetOf<KtParameter>()

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
              diagnostics +=
                buildDiagnostic(eventParameterForCall, "all \"$argumentReceiverName\" access", setOf(index))
            }
          }
        }

        override fun visitReferenceExpression(expression: KtReferenceExpression) {
          super.visitReferenceExpression(expression)
          if (expression !is KtNameReferenceExpression) return
          val name = expression.getReferencedName()
          val eventParameter = eventParameters.find { it.name == name } ?: return
          if (expression.parent is KtValueArgumentName || expression.getReceiverExpression() != null) return
          if (expression.isDeclaredBetween(name, function)) return

          val call = when (val parent = expression.parent) {
            is KtCallExpression -> parent.takeIf { it.calleeExpression == expression }

            is KtQualifiedExpression -> (parent.selectorExpression as? KtCallExpression)
              ?.takeIf { parent.receiverExpression == expression && it.calleeExpression?.text == "invoke" }

            else -> null
          }
          if (call != null) {
            eventCalls.getOrPut(eventParameter) { mutableListOf() } += call
          } else {
            eventsUsedOtherwise += eventParameter
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

    if (reportConstantArguments) {
      eventCalls.filterKeys { it !in eventsUsedOtherwise }.forEach { (eventParameter, calls) ->
        constantArgumentDiagnostic(eventParameter, calls)?.let { diagnostics += it }
      }
    }
    return diagnostics
  }

  /** One finding for all argument indexes which every call of [eventParameter] passes the same constant at. */
  private fun constantArgumentDiagnostic(
    eventParameter: KtParameter,
    calls: List<KtCallExpression>,
  ): ComposeDiagnostic? {
    val constants = (0 until calls.minOf { it.valueArguments.size }).mapNotNull { index ->
      val constant = calls.map { call ->
        call.valueArguments[index].getArgumentExpression()
          ?.let(KtPsiUtil::safeDeparenthesize)
          ?.takeIf { it.isConstant() }
          ?.text
          ?: return@mapNotNull null
      }.distinct().singleOrNull() ?: return@mapNotNull null
      index to constant
    }.toMap()
    if (constants.isEmpty()) return null
    val movedArgument = if (constants.size == 1) "constant" else "constants"
    return buildDiagnostic(
      eventParameter,
      "$movedArgument ${constants.values.joinToString {
        "\"$it\""
      }}",
      constants.keys,
    )
  }

  private fun KtExpression.isConstant(): Boolean = when (this) {
    is KtConstantExpression -> true

    is KtPrefixExpression -> baseExpression?.isConstant() == true

    is KtStringTemplateExpression -> !hasInterpolation()

    is KtNameReferenceExpression -> semantic.isConstantReference(this)

    is KtDotQualifiedExpression -> (selectorExpression as? KtNameReferenceExpression)
      ?.let(semantic::isConstantReference) == true

    else -> false
  }

  private fun buildDiagnostic(
    eventParameter: KtParameter,
    movedArgument: String,
    argumentIndexes: Set<Int>,
  ): ComposeDiagnostic {
    return ComposeDiagnostic(
      "Unnecessary event callback arguments. Move $movedArgument " +
        "to the parent composable event handler and switch \"${eventParameter.name}\" type to " +
        "\"${suggestedType(eventParameter, argumentIndexes)}\"",
      eventParameter,
    )
  }

  private fun suggestedType(eventParameter: KtParameter, argumentIndexes: Set<Int>): String {
    val parameterList = eventParameter.functionTypeOrNull()
      ?.parameters
      ?.filterIndexed { index, _ -> index !in argumentIndexes }
      .orEmpty()
    return "${parameterList.joinToString(prefix = "(", postfix = ")", transform = { it.text })} -> Unit"
      .let { if (eventParameter.typeReference?.typeElement is KtNullableType) "($it)?" else it }
  }
}
