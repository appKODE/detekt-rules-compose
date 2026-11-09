package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtCallExpression
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic

data class UnwrappedValueClassImageModelOptions(
  val imageFunctions: List<String> = listOf("AsyncImage", "SubcomposeAsyncImage", "rememberAsyncImagePainter"),
)

/**
 * Reports a value class passed as a Coil image model without unwrapping it. The `model` parameter is `Any?`, so the
 * code compiles, but the value class gets boxed and Coil can't load an image from it.
 *
 * Non-compliant:
 * ```
 * @JvmInline
 * value class ImageUrl(val value: String)
 *
 * @Composable
 * fun Avatar(url: ImageUrl) {
 *   AsyncImage(model = url, contentDescription = null)
 *   AsyncImage(model = ImageRequest.Builder(context).data(url).build(), contentDescription = null)
 * }
 * ```
 *
 * Compliant:
 * ```
 * @Composable
 * fun Avatar(url: ImageUrl) {
 *   AsyncImage(model = url.value, contentDescription = null)
 *   AsyncImage(model = ImageRequest.Builder(context).data(url.value).build(), contentDescription = null)
 * }
 * ```
 *
 * The `model` argument of [UnwrappedValueClassImageModelOptions.imageFunctions] and the `data` argument of an
 * `ImageRequest.Builder` function are checked.
 */
class UnwrappedValueClassImageModelAnalyzer(
  private val options: UnwrappedValueClassImageModelOptions,
  private val semantic: ComposeSemantic,
) {
  fun analyze(call: KtCallExpression): ComposeDiagnostic? {
    val model = when (call.calleeExpression?.text) {
      in options.imageFunctions -> semantic.argumentForParameterNamed(call, "model")

      // `coil.request.ImageRequest.Builder` or the one of `coil3`
      "data" -> semantic.argumentForParameterNamed(call, "data")
        ?.takeIf { ".${semantic.calleeDeclaringClass(call)}".endsWith(".ImageRequest.Builder") }

      else -> null
    } ?: return null
    if (!semantic.isValueClassInstance(model)) return null

    return ComposeDiagnostic(
      "\"${model.text}\" is a value class, Coil can't load an image from it. Pass its underlying value instead",
      model,
    )
  }
}
