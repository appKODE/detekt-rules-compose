package ru.kode.detekt.rule.compose.shared

/** A callee parameter whose type is a `@Composable` function type, see [ComposeSemantic.composableLambdaParameters]. */
data class ComposableLambdaParameter(
  val name: String,
  /**
   * Whether a lambda passed to the parameter is inlined into the caller: the callee is `inline`, the parameter is
   * neither `noinline` nor `crossinline` and its type is not nullable. The Compose compiler makes no restart scope of
   * such a lambda. A `crossinline` lambda is inlined into a lambda the callee creates (`itemContent` of the lazy list
   * `items`), which is a scope of its own.
   */
  val isInlined: Boolean,
)
