/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.kode.detekt.rule.compose.snippet.composeSnippet

class UnnecessaryEventHandlerParameterTest : ShouldSpec({
  val environment = createEnvironment()

  should("report when whole class is passed") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onClick: (Data) -> Unit) {
          Button(onClick = { onClick(data) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("report when handler argument is class property of a parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          Button(onClick = { onButtonClick(data.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("report when handler argument is nested class property of a parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Nested(val id: Int)
        data class Data(val id: Int, val nested: Nested)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          Button(onClick = { onButtonClick(data.nested.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("not report when handler argument is a class property not from parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          val localData = Data(3, "hello")
          Button(onClick = { onButtonClick(localData.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("not report when handler argument name matches partially") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit, onButton1Click: (Data) -> Unit) {
          val dataUsage = Data(3, "hello")
          Button(onClick = { onButtonClick(dataUsage.id) }) { }
          Button(onClick = { onButton1Click(dataUsage) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("not report when handler argument name is passed to a non event handler call") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        fun processData(d: Int) = Unit

        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          Button(onClick = { processData(data.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("not report when accessing non-properties of handler argument") {
    // language=kotlin
    val code = composeSnippet(
      """
          data class Data(val id: Int, val title: String) {
            fun process(): Int = 0
          }

          @Composable
          fun Test(data: Data, onButtonClick: (Int) -> Unit) {
            Button(onClick = { onButtonClick(data.process()) }) { }
          }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("report an error message with proper event callback type") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          Button(onClick = { onButtonClick(data.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single().message shouldContain "switch \"onButtonClick\" type to \"() -> Unit\""
  }

  should("report an error message with proper event callback type when multiple arguments for first") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int, String) -> Unit) {
          Button(onClick = { onButtonClick(data.id, "hello") }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single().message shouldContain "switch \"onButtonClick\" type to \"(String) -> Unit\""
  }

  should("report an error message with proper event callback type when multiple arguments for second") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int, String, Int) -> Unit) {
          Button(onClick = { onButtonClick(0, data.title, 0) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single().message shouldContain "switch \"onButtonClick\" type to \"(Int, Int) -> Unit\""
  }

  should("honour @Suppress on the event handler parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
          data class State(val id: String)
          @Composable
          fun MyButton(
            state: State,
            @Suppress("UnnecessaryEventHandlerParameter") onClick: (String) -> Unit,
          ) {
            Button(onClick = { onClick(state.id) }) { Text("Click here") }
          }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("not report if parameter is a sealed class") {
    // language=kotlin
    val code = composeSnippet(
      """
          sealed class State {
              object Loading : State()
              data class Data(val id: String) : State()
          }

          @Composable
          fun MyButton(
              state: State,
              onClick: (String) -> Unit,
          ) {
              when (state) {
                  State.Loading -> Text("Loading")
                  is State.Data -> Button(onClick = { onClick(state.id) }) {}
              }
          }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("report inside if/else-if branches and nested lambdas") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        @Composable
        fun Test(data: Data, first: Boolean, onClick: (Int) -> Unit) {
          if (first) {
            Text("first")
          } else if (data.id > 0) {
            Row {
              Button(onClick = { onClick(data.id) }) {}
            }
          }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("report nullable event handler and keep it nullable in the suggested type") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        @Composable
        fun Test(data: Data, onButtonClick: ((Int) -> Unit)? = null) {
          Button(onClick = { if (onButtonClick != null) onButtonClick(data.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.first().message shouldContain "switch \"onButtonClick\" type to \"(() -> Unit)?\""
  }

  should("ignore non-composable functions") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        fun Test(data: Data, onClick: (Int) -> Unit) {
          Button(onClick = { onClick(data.id) }) {}
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  // https://github.com/appKODE/detekt-rules-compose/issues/41
  should("not report a lambda parameter shadowing a state parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
        @Composable
        fun Asdf(rotation: Float, onClick: (Float) -> Unit) {
          Column {
            Asdf(rotation, { rotation -> onClick(rotation) })
          }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("report a state parameter passed from a lambda with another parameter name") {
    // language=kotlin
    val code = composeSnippet(
      """
        @Composable
        fun Asdf(rotation: Float, onClick: (Float) -> Unit) {
          Column {
            Asdf(rotation, { foo -> onClick(rotation) })
          }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.single().message shouldBe "Unnecessary event callback arguments. Move all \"rotation\" access " +
      "to the parent composable event handler and switch \"onClick\" type to \"() -> Unit\""
    findings.single().shouldStartAt(code, "onClick: (Float) -> Unit")
  }

  should("not report a local val or a catch parameter shadowing a state parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
        @Composable
        fun Test(id: Int, error: String, values: List<Int>, onClick: (Int) -> Unit, onError: (String?) -> Unit) {
          Button(onClick = {
            try {
              values.first()
            } catch (error: Exception) {
              onError(error.message)
            }
          }) { }
          Button(onClick = {
            val id = values.first()
            onClick(id)
          }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }
})
