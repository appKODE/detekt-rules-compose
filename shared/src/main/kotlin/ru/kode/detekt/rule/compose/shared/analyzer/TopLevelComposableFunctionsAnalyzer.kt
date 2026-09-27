package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed
import ru.kode.detekt.rule.compose.shared.isOverrideLike

data class TopLevelComposableFunctionsOptions(
  val allowInObjects: Boolean = false,
  val allowInInterfaces: Boolean = false,
)

/**
 * Checks that composable function is defined as a top-level function.
 *
 * `allowInObjects` config property can be used to control if usage of composable functions
 * in `object` is permitted, `allowInInterfaces` does the same for `interface`s (e.g. a composable
 * provided by another module through DI). Classes are always reported.
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
class TopLevelComposableFunctionsAnalyzer(
  private val options: TopLevelComposableFunctionsOptions,
) {
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    val container = function.containingClassOrObject
    if (!function.isTopLevel &&
      !function.isOverrideLike() &&
      (container !is KtObjectDeclaration || !options.allowInObjects) &&
      ((container as? KtClass)?.isInterface() != true || !options.allowInInterfaces)
    ) {
      return listOf(
        ComposeDiagnostic(
          "Composable functions should be defined as top-level functions",
          function,
        ),
      )
    }

    return emptyList()
  }
}
