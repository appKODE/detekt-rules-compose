package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.assertions.fail
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain

class ComposableParametersOrderingTest : ShouldSpec(
  {
    context("optional/required order check") {
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

      should("not report in presence of a required composable slot") {
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

        // See NOTE_ALLOWING_REQUIRED_TRAILING_SLOT_SPECIAL_CASE for details
        findings.shouldBeEmpty()
      }
    }

    context("slot ordering check").config(enabled = CHECK_IN_PRESENCE_OF_SLOTS_ENABLED) {
      // NOTE_ALLOWING_REQUIRED_TRAILING_SLOT_SPECIAL_CASE
      // There are several considerations on whether to enforce required/optional order in presence of slots
      // The following tests demonstrate some tricky situations to think about.
      //
      // The first one actually looks very handy: one could want to simply call
      //
      // Test("hello") { Content() }
      //
      // instead of explicit
      //
      // Test("hello", content = { Content() })
      //
      // which would be the case if this rule would enforce putting content above `subtitle`,
      // BUT this is a violation of rule "required first, optional last" and if it would be permitted,
      // all sorts of other "special cases" start to pop up, few of them are exemplified by tests below
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

        ComposableParametersOrdering().lint(code)

        fail("should this be permitted?")
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

        ComposableParametersOrdering().lint(code)

        fail("should this be permitted?")
      }

      should("not report multiple trailing required composable slots and some are non-trailing") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          icon: @Composable () -> Unit,
          subtitle: String? = null,
          description: String? = null,
          content: @Composable () -> Unit,
        ) {
        }
        """.trimIndent()

        ComposableParametersOrdering().lint(code)

        // This situation would likely confuse users too:
        // "what are the rules actually? are we splitting optional/required or are we not?"
        fail("should this be permitted?")
      }

      should("report mixing trailing optional/required composable slots") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          subtitle: String? = null,
          content1: @Composable () -> Unit,
          content2: @Composable (() -> Unit)? = null,
        ) {
        }
        """.trimIndent()

        ComposableParametersOrdering().lint(code)

        fail("should this be permitted?")
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

        ComposableParametersOrdering().lint(code)

        fail("should this be permitted?")
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
  }
)
