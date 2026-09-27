package ru.kode.detekt.rule.compose.snippet

/**
 * Appends fake Compose declarations used by contract tests.
 * [imports] go right after the package directive (e.g. `import ru.kode.detekt.rule.Text as Label`).
 */
fun composeSnippet(code: String, imports: String = ""): String {
  return """
      package ru.kode.detekt.rule
      $imports

      @Target(
          AnnotationTarget.FUNCTION,
          AnnotationTarget.TYPE,
          AnnotationTarget.TYPE_PARAMETER,
          AnnotationTarget.PROPERTY_GETTER
      )
      annotation class Composable
      annotation class Preview
      data class Dp(val v: Int)
      val Int.dp get() = Dp(this)

      interface Modifier {
        fun weight(v: Float): Modifier { return this }
        fun fillMaxSize(): Modifier { return this }
        fun padding(horizontal: Dp, vertical: Dp): Modifier { return this }
        fun padding(all: Dp): Modifier { return this }
        fun height(height: Dp): Modifier { return this }
        fun heightIn(min: Dp): Modifier { return this }
        companion object : Modifier
      }

      enum class Alignment { CenterVertically }

      @Composable fun Row(modifier: Modifier = Modifier, verticalAlignment: Alignment = Alignment.CenterVertically, content: @Composable () -> Unit) {}
      @Composable fun Column(modifier: Modifier = Modifier, verticalAlignment: Alignment = Alignment.CenterVertically, content: @Composable () -> Unit) {}
      @Composable fun Box(modifier: Modifier = Modifier, content: @Composable () -> Unit) {}
      @Composable fun Text(text: String, modifier: Modifier = Modifier) {}
      @Composable fun Button(onClick: () -> Unit, content: @Composable () -> Unit) {}

      $code
  """.trimIndent()
}
