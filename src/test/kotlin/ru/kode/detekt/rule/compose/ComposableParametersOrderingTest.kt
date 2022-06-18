package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain

class ComposableParametersOrderingTest : ShouldSpec(
  {
    should("report when optional go before required") {
      // language=kotlin
      val code = """
      @Composable
      fun Test(
        enabled: Boolean = false,
        text: String,
        age: Int
      ) {
      }
      """.trimIndent()

      val findings = ComposableParametersOrdering().lint(code)

      findings shouldHaveSize 1
    }

    should("report when optional and required are mixed") {
      // language=kotlin
      val code = """
      @Composable
      fun Test(
        title: String,
        enabled: Boolean = false,
        text: String,
        age: Int = 8
      ) {
      }
      """.trimIndent()

      val findings = ComposableParametersOrdering().lint(code)

      findings shouldHaveSize 1
    }

    should("not report if all required") {
      // language=kotlin
      val code = """
      @Composable
      fun Test(
        title: String,
        text: String,
      ) {
      }
      """.trimIndent()

      val findings = ComposableParametersOrdering().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report if all optional") {
      // language=kotlin
      val code = """
      @Composable
      fun Test(
        title: String = "",
        text: String = "",
      ) {
      }
      """.trimIndent()

      val findings = ComposableParametersOrdering().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report trailing required composable slot") {
      // language=kotlin
      val code = """
      @Composable
      fun Test(
        title: String,
        subtitle: String? = null,
        content: @Composable () -> Unit,
      ) {
      }
      """.trimIndent()

      val findings = ComposableParametersOrdering().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report multiple trailing required composable slots") {
      // language=kotlin
      val code = """
      @Composable
      fun Test(
        title: String,
        subtitle: String? = null,
        content1: @Composable () -> Unit,
        content2: @Composable () -> Unit,
      ) {
      }
      """.trimIndent()

      val findings = ComposableParametersOrdering().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report if composable slots are not trailing and mixed with optional") {
      // language=kotlin
      val code = """
      @Composable
      fun Test(
        title: String,
        subtitle: String? = null,
        content2: @Composable () -> Unit,
        subtitle2: String? = null,
      ) {
      }
      """.trimIndent()

      val findings = ComposableParametersOrdering().lint(code)

      findings shouldHaveSize 1
    }

    should("not report trailing event handler") {
      // language=kotlin
      val code = """
      @Composable
      fun OnBackPressedHandler(
        enabled: Boolean = false,
        onBack: () -> Unit,
      ) {
      }
      """.trimIndent()

      val findings = ComposableParametersOrdering().lint(code)

      findings.shouldBeEmpty()
    }

    should("report if required slots are mixed with non-slot required parameters") {
      // language=kotlin
      val code = """
      @Composable
      fun Test(
        title: String,
        content: @Composable () -> Unit,
        subtitle: String,
        contentOptional: @Composable () -> Unit,
      ) {
      }
      """.trimIndent()

      val findings = ComposableParametersOrdering().lint(code)

      findings shouldHaveSize 1
      findings.first().message shouldContain "\"content\" after \"subtitle\""
    }

    should("report if optional slots are mixed with non-slot optional parameters") {
      // language=kotlin
      val code = """
      @Composable
      fun Test(
        title: String,
        content: @Composable () -> Unit,
        contentOptional: @Composable (() -> Unit)? = null,
        subtitle: String? = null,
      ) {
      }
      """.trimIndent()

      val findings = ComposableParametersOrdering().lint(code)

      findings shouldHaveSize 1
      findings.first().message shouldContain "\"contentOptional\" after \"subtitle\""
    }
  }
)
