package ru.kode.detekt.rule.compose.shared.analyzer

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
 */
class MissingModifierDefaultValueAnalyzer {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (function.isActualLike()) return emptyList()
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    val modifierParameter = function.valueParameters.find { it.isModifier() } ?: return emptyList()

    if (function.isAbstractLike() || function.isOpenLike() || function.containingClass()?.isInterface() == true) {
      return emptyList()
    }

    if (!function.isOverrideLike() && !modifierParameter.hasDefaultValue()) {
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
