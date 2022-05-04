package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain

class UnnecessaryEventHandlerParameterTest : ShouldSpec({
  should("report when whole class is passed") {
    // language=kotlin
    val code = """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onClick: (Data) -> Unit) {
          Button(onClick = { onClick(data) }) { }
        }
    """.trimIndent()

    val findings = UnnecessaryEventHandlerParameter().lint(code)

    findings shouldHaveSize 1
  }

  should("report when handler argument is class property of a parameter") {
    // language=kotlin
    val code = """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          Button(onClick = { onButtonClick(data.id) }) { }
        }
    """.trimIndent()

    val findings = UnnecessaryEventHandlerParameter().lint(code)

    findings shouldHaveSize 1
  }

  should("report when handler argument is nested class property of a parameter") {
    // language=kotlin
    val code = """
        data class Nested(val id: Int)
        data class Data(val id: Int, val nested: Nested)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          Button(onClick = { onButtonClick(data.nested.id) }) { }
        }
    """.trimIndent()

    val findings = UnnecessaryEventHandlerParameter().lint(code)

    findings shouldHaveSize 1
  }

  should("not report when handler argument is a class property not from parameter") {
    // language=kotlin
    val code = """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          val localData = Data(3, "hello")
          Button(onClick = { onButtonClick(localData.id) }) { }
        }
    """.trimIndent()

    val findings = UnnecessaryEventHandlerParameter().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report when handler argument name matches partially") {
    // language=kotlin
    val code = """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          val dataUsage = Data(3, "hello")
          Button(onClick = { onButtonClick(dataUsage.id) }) { }
          Button(onClick = { onButtonClick(dataUsage) }) { }
        }
    """.trimIndent()

    val findings = UnnecessaryEventHandlerParameter().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report when handler argument name is passed to a non event handler call") {
    // language=kotlin
    val code = """
        data class Data(val id: Int, val title: String)
        fun processData(d: Data) = Unit

        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          Button(onClick = { processData(data.id) }) { }
        }
    """.trimIndent()

    val findings = UnnecessaryEventHandlerParameter().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report when accessing non-properties of handler argument") {
    // language=kotlin
    val code = """
          data class Data(val id: Int, val title: String) {
            fun process()
          }

          @Composable
          fun Test(data: Data, onButtonClick: (Int) -> Unit) {
            Button(onClick = { onButtonClick(data.copy(id=33)) }) { }
            Button(onClick = { onButtonClick(data.process()) }) { }
          }
    """.trimIndent()

    val findings = UnnecessaryEventHandlerParameter().lint(code)

    findings.shouldBeEmpty()
  }

  should("report an error message with proper event callback type") {
    // language=kotlin
    val code = """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          Button(onClick = { onButtonClick(data.nested.id) }) { }
        }
    """.trimIndent()

    val findings = UnnecessaryEventHandlerParameter().lint(code)

    findings.single().message shouldContain "switch \"onButtonClick\" type to \"() -> Unit\""
  }

  should("report an error message with proper event callback type when multiple arguments for first") {
    // language=kotlin
    val code = """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int, String) -> Unit) {
          Button(onClick = { onButtonClick(data.nested.id, "hello") }) { }
        }
    """.trimIndent()

    val findings = UnnecessaryEventHandlerParameter().lint(code)

    findings.single().message shouldContain "switch \"onButtonClick\" type to \"(String) -> Unit\""
  }

  should("report an error message with proper event callback type when multiple arguments for second") {
    // language=kotlin
    val code = """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int, String, Int) -> Unit) {
          Button(onClick = { onButtonClick(0, data.title, 0) }) { }
        }
    """.trimIndent()

    val findings = UnnecessaryEventHandlerParameter().lint(code)

    findings.single().message shouldContain "switch \"onButtonClick\" type to \"(Int, Int) -> Unit\""
  }
})
