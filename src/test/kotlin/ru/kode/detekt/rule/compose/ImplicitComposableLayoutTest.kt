package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain

class ImplicitComposableLayoutTest : ShouldSpec({
  should("report implicit layout with multiple top-level composables") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Text("hello")
        Text("world")
      }
    """.trimIndent()

    val findings = ImplicitComposableLayout().lint(code)

    findings shouldHaveSize 1
  }

  should("report implicit layout with multiple top-level composables in if/else") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        val condition = false
        if (condition) {
          Text("hello")
          Text("world")
        } else {
          Text("hello")
          Text("world")
        }
      }
    """.trimIndent()

    val findings = ImplicitComposableLayout().lint(code)

    findings shouldHaveSize 1
  }

  should("report implicit layout with multiple top-level composables in when") {
    // language=kotlin
    val code = """
      enum class State { One, Two }
      @Composable
      fun Test() {
        val condition = State.One
        when (condition) {
          State.One -> {
            Text("hello")
            Text("world")
          }
          State.Two -> {
            Text("hello")
          }
        } 
      }
    """.trimIndent()

    val findings = ImplicitComposableLayout().lint(code)

    findings shouldHaveSize 1
  }

  // TODO "not"-version for all of the above when wrapped in layout
})
