/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import ru.kode.detekt.rule.compose.snippet.composeSnippet

class UnnecessaryEventHandlerParameterTest : ShouldSpec({
  val envWrapper = createEnvironment()
  val env: KotlinCoreEnvironment = envWrapper.env

  afterSpec { envWrapper.dispose() }

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

    val findings = UnnecessaryEventHandlerParameter().compileAndLintWithContext(env, code)

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

    val findings = UnnecessaryEventHandlerParameter().compileAndLintWithContext(env, code)

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

    val findings = UnnecessaryEventHandlerParameter().compileAndLintWithContext(env, code)

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

    val findings = UnnecessaryEventHandlerParameter().compileAndLintWithContext(env, code)

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

    val findings = UnnecessaryEventHandlerParameter().compileAndLintWithContext(env, code)

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

    val findings = UnnecessaryEventHandlerParameter().compileAndLintWithContext(env, code)

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

    val findings = UnnecessaryEventHandlerParameter().compileAndLintWithContext(env, code)

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

    val findings = UnnecessaryEventHandlerParameter().compileAndLintWithContext(env, code)

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

    val findings = UnnecessaryEventHandlerParameter().compileAndLintWithContext(env, code)

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

    val findings = UnnecessaryEventHandlerParameter().compileAndLintWithContext(env, code)

    findings.single().message shouldContain "switch \"onButtonClick\" type to \"(Int, Int) -> Unit\""
  }

  should("allow annotations on the lambda parameter") {
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

    shouldNotThrowAny {
      UnnecessaryEventHandlerParameter().compileAndLintWithContext(env, code)
    }
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

    val findings = UnnecessaryEventHandlerParameter().compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }
})
