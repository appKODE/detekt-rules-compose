/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize

class MissingModifierDefaultValueTest : ShouldSpec(
  {
    should("report if modifier parameter has no default value") {
      // language=kotlin
      val code = """
      @Composable
      @Preview
      fun Test(modifier: Modifier) {
        Text(text = "3")
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings shouldHaveSize 1
    }

    should("not report if modifier parameter has a default value") {
      // language=kotlin
      val code = """
      @Composable
      @Preview
      fun Test(modifier: Modifier = Modifier) {
        Text(text = "3")
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report if no modifier parameter present") {
      // language=kotlin
      val code = """
      @Composable
      @Preview
      fun Test(text: String) {
        Text(text = text)
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }
  }
)
