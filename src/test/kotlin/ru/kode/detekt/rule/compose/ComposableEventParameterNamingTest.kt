package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain

class ComposableEventParameterNamingTest : ShouldSpec({
  should("report no arg listener") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier, click: () -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings shouldHaveSize 1
  }

  should("report arg listener") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier, change: (Int) -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings shouldHaveSize 1
  }

  should("report click ed-listener") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier, onSomethingClicked: () -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings shouldHaveSize 1
    findings.first().message shouldContain "past tense"
  }

  should("report change ed-listener") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier, onValueChanged: () -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings shouldHaveSize 1
    findings.first().message shouldContain "past tense"
  }

  should("report general ed-listener") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier, onSomethingProduced: () -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings shouldHaveSize 1
    findings.first().message shouldContain "past tense"
  }

  should("report multiple findings") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(click: () -> Unit, change: (String) -> Unit, onChanged: () -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings shouldHaveSize 3
  }

  should("not report correct name") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(onChange: () -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings.shouldBeEmpty()
  }

  should("not ignore annotated parameters") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(@CustomEvent changed: () -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings shouldHaveSize 1
  }

  should("not ignore annotated lambda parameters") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(changed: @CustomArg () -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings shouldHaveSize 1
  }

  should("ignore composable functions name") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(changed: @Composable () -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings.shouldBeEmpty()
  }

  should("ignore functions returning value") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(changed: () -> Int) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings.shouldBeEmpty()
  }
})
