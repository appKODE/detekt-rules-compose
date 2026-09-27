package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed

/**
 * Checks that all composable preview functions are private
 */
class PublicComposablePreviewAnalyzer {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (function.hasAnnotationNamed("Composable") && function.hasAnnotationNamed("Preview") && function.isPublic) {
      return listOf(
        ComposeDiagnostic(
          "Preview composable must not be public",
          function,
        ),
      )
    }

    return emptyList()
  }
}
