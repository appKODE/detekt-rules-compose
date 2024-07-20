package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain

class ComposableParametersOrderingTest : ShouldSpec() {
  init {
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

    context("modifier position") {
      should("report when incorrect modifier position with all optional parameters") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          verticalAlignment: Alignment = Alignment.CenterVertically,
          modifier: Modifier = Modifier.height(24.dp),
          enabled: Boolean = false,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.first().message shouldContain "before \"verticalAlignment\""
      }

      should("not report when modifier in first position with all optional parameters") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          modifier: Modifier = Modifier.height(24.dp),
          verticalAlignment: Alignment = Alignment.CenterVertically,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("not report when modifier is the single parameter") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          modifier: Modifier = Modifier.height(24.dp),
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("report incorrect modifier position with a mix of required and optional parameters") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          verticalAlignment: Alignment = Alignment.CenterVertically,
          modifier: Modifier = Modifier.height(24.dp),
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.first().message shouldContain "after \"text\""
      }

      should("not report when modifier is optional parameter") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          modifier: Modifier = Modifier.height(24.dp),
          verticalAlignment: Alignment = Alignment.CenterVertically,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("not report when modifier is last and no optional parameters") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          modifier: Modifier = Modifier.height(24.dp),
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("report incorrect modifier position when no optional parameters and modifier is not optional") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          modifier: Modifier,
          verticalAlignment: Alignment,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.first().message shouldContain "after \"verticalAlignment\""
      }

      should("not report incorrect modifier position when no optional parameters non-optional modifier is last") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          verticalAlignment: Alignment,
          modifier: Modifier,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("report when optional parameters and event handlers and modifier is not the first optional") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          onClick: () -> Unit,
          verticalAlignment: Alignment = Alignment.Center,
          modifier: Modifier = Modifier,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
      }

      should("report incorrect modifier position when last parameter is a required composable lambda") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          verticalAlignment: Alignment = Alignment.CenterVertically,
          modifier: Modifier = Modifier,
          content: @Composable () -> Unit,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.first().message shouldContain "after \"text\""
      }

      should("report incorrect modifier position when last parameter is a composable slot with a scoped receiver") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          modifier: Modifier,
          verticalAlignment: Alignment,
          content: @Composable ColumnScope.() -> Unit,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.first().message shouldContain "after \"verticalAlignment\""
      }

      should("report incorrect modifier position when incorrect position with composable lambda with default value") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          modifier: Modifier,
          verticalAlignment: Alignment,
          content: @Composable () -> Unit = {},
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.first().message shouldContain "after \"verticalAlignment\""
      }
    }

    context("with slots") {
      should("report incorrect order with trailing required composable slot") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          subtitle: String? = null,
          title: String,
          content: @Composable () -> Unit,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)
        findings shouldHaveSize 1
      }

      should("report incorrect order with multiple trailing required composable slot") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          subtitle: String? = null,
          title: String,
          content1: @Composable () -> Unit,
          content2: @Composable () -> Unit,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)
        findings shouldHaveSize 1
      }

      should("report multiple trailing required composable slots and some are non-trailing") {
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

        val findings = ComposableParametersOrdering().lint(code)
        findings shouldHaveSize 1
      }

      should("not report mixing trailing optional/required composable slots") {
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

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
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

      should("not report required event handlers") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          onClick: () -> Unit,
          text: String
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
      }
    }
  }
}
