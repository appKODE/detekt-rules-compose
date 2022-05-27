/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain

class ModifierParameterPositionTest : ShouldSpec({
  should("report when incorrect position with all optional parameters") {
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

    val findings = ModifierParameterPosition().lint(code)

    findings shouldHaveSize 1
    findings.first().message shouldContain "before \"verticalAlignment\""
  }

  should("not report when in first position with all optional parameters") {
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

    val findings = ModifierParameterPosition().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report when it is the single parameter") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(
          modifier: Modifier = Modifier.height(24.dp),
      ) {
          Text(text = props.title)
      }
    """.trimIndent()

    val findings = ModifierParameterPosition().lint(code)

    findings.shouldBeEmpty()
  }

  should("report incorrect position with a mix of required and optional parameters") {
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

    val findings = ModifierParameterPosition().lint(code)

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

    val findings = ModifierParameterPosition().lint(code)

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

    val findings = ModifierParameterPosition().lint(code)

    findings.shouldBeEmpty()
  }

  should("report incorrect position when no optional parameters and modifier is not optional") {
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

    val findings = ModifierParameterPosition().lint(code)

    findings shouldHaveSize 1
    findings.first().message shouldContain "after \"verticalAlignment\""
  }

  should("not report incorrect position when no optional parameters non-optional modifier is last") {
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

    val findings = ModifierParameterPosition().lint(code)

    findings.shouldBeEmpty()
  }

  should("report incorrect position when optional parameters exist and modifier is not optional") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(
          text: String,
          verticalAlignment: Alignment = Alignment.Center,
          modifier: Modifier,
      ) {
          Text(text = props.title)
      }
    """.trimIndent()

    val findings = ModifierParameterPosition().lint(code)

    findings shouldHaveSize 1
    findings.first().message shouldContain "after \"text\""
  }

  should("not report when optional parameters and event handlers and modifier is before first optional") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(
          text: String,
          onClick: () -> Unit,
          modifier: Modifier,
          verticalAlignment: Alignment = Alignment.Center,
      ) {
          Text(text = props.title)
      }
    """.trimIndent()

    val findings = ModifierParameterPosition().lint(code)

    findings.shouldBeEmpty()
  }

  should("report incorrect position when optional parameters interspersed with required parameters") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(
          text: String,
          modifier: Modifier,
          isEnabled: Boolean = false,
          verticalAlignment: Alignment,
      ) {
          Text(text = props.title)
      }
    """.trimIndent()

    val findings = ModifierParameterPosition().lint(code)

    findings shouldHaveSize 1
    findings.first().message shouldContain "after \"verticalAlignment\""
  }

  should("not report incorrect position when last parameter is a required composable lambda") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(
          text: String,
          modifier: Modifier,
          verticalAlignment: Alignment,
          content: @Composable () -> Unit,
      ) {
          Text(text = props.title)
      }
    """.trimIndent()

    val findings = ModifierParameterPosition().lint(code)

    findings.shouldBeEmpty()
  }

  should("report incorrect position when incorrect position with composable lambda with default value") {
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

    val findings = ModifierParameterPosition().lint(code)

    findings shouldHaveSize 1
    findings.first().message shouldContain "after \"verticalAlignment\""
  }
})
