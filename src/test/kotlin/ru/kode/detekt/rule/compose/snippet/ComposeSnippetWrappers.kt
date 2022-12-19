package ru.kode.detekt.rule.compose.snippet

/**
 * Appends a "fake" compose functions/classes/imports to mimic ones provided by Compose framework.
 *
 * This is required, because at the moment Detekt doesn't support adding custom plugins when it builds
 * BindingContext and while Compose libraries can be added on classpath through testImplementation in build.gradle,
 * but snippets won't compile without the Compose plugin.
 *
 * TODO: create a detekt-repo issue to request support for configuring BindingContext with custom compiler plugins
 */
fun composeSnippet(code: String): String {
  return """
      package ru.kode.detekt.rule

      @Target(
          AnnotationTarget.FUNCTION,
          AnnotationTarget.TYPE,
          AnnotationTarget.TYPE_PARAMETER,
          AnnotationTarget.PROPERTY_GETTER
      )
      annotation class Composable
      data class Dp(val v: Int)
      val Int.dp get() = Dp(this)
      interface Modifier {
        fun weight(v: Float): Modifier { TODO() }
        fun fillMaxSize(): Modifier { TODO() }
        fun padding(horizontal: Dp, vertical: Dp): Modifier { TODO() }
        fun padding(all: Dp): Modifier { TODO() }
      }
      val Modifier = object : Modifier {
      }
      enum class Alignment { CenterVertically }

      @Composable fun Row(modifier: Modifier = Modifier, verticalAlignment: Alignment = Alignment.CenterVertically, content: () -> Unit) {}
      @Composable fun Column(modifier: Modifier = Modifier, verticalAlignment: Alignment = Alignment.CenterVertically, content: () -> Unit) {}
      @Composable fun Box(modifier: Modifier = Modifier, content: () -> Unit) {}
      @Composable fun Text(modifier: Modifier = Modifier, text: String) {}

      $code
  """.trimIndent()
}
