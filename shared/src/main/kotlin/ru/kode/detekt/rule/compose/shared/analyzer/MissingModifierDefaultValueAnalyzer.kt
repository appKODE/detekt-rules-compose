package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed
import ru.kode.detekt.rule.compose.shared.isAbstractLike
import ru.kode.detekt.rule.compose.shared.isActualLike
import ru.kode.detekt.rule.compose.shared.isModifier
import ru.kode.detekt.rule.compose.shared.isOpenLike
import ru.kode.detekt.rule.compose.shared.isOverrideLike

/**
 * Checks that the `modifier` parameter of a Composable function has a default value.
 *
 * Non-compliant:
 *
 * ```
 * fun Content(modifier: Modifier) {
 *   Text("Greetings")
 * }
 * ```
 *
 * Compliant:
 *
 * ```
 * fun Content(modifier: Modifier = Modifier) {
 *   Text("Greetings")
 * }
 * ```
 *
 * Overriding and `actual` functions are not checked, they can't declare default values. Abstract and open functions
 * and functions of interfaces are checked only when [checkAbstractFunctions] is on: the Compose compiler accepts
 * default values in abstract composables from Kotlin language version 2.1 and in open ones from 2.2.
 */
class MissingModifierDefaultValueAnalyzer(private val checkAbstractFunctions: Boolean = false) {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (function.isActualLike()) return emptyList()
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    val modifierParameter = function.valueParameters.find { it.isModifier() } ?: return emptyList()

    if (function.isOverrideLike()) return emptyList()

    val containingClass = function.containingClass()
    val isInInterface = containingClass?.isInterface() == true
    // the single abstract function of a `fun interface` can't declare default values
    val isFunInterfaceMember = isInInterface && !function.hasBody() &&
      containingClass?.hasModifier(KtTokens.FUN_KEYWORD) == true
    val isOverridable = function.isAbstractLike() || function.isOpenLike() || isInInterface
    if (isFunInterfaceMember || (isOverridable && !checkAbstractFunctions)) return emptyList()

    if (!modifierParameter.hasDefaultValue()) {
      return listOf(
        ComposeDiagnostic(
          "Modifier parameter should have a default value: \"modifier = Modifier\"",
          modifierParameter,
        ),
      )
    }

    return emptyList()
  }
}
