/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import dev.detekt.test.lint
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize

class ModifierDefaultValueTest : ShouldSpec(
  {
    should("report if modifier parameter has an invalid default value") {
      // language=kotlin
      val code = """
      @Composable
      @Preview
      fun Test(modifier: Modifier = Modifier.fillMaxSize()) {
        Text(text = "3")
      }
      """.trimIndent()

      val findings = ModifierDefaultValue().lint(code)

      findings shouldHaveSize 1
    }

    should("report if modifier parameter has an invalid default value wrapped on multiple lines") {
      // language=kotlin
      val code = """
      @Composable
      @Preview
      fun Test(
        modifier: Modifier = Modifier
          .fillMaxSize()
          .weight(1f)
      ) {
        Text(text = "3")
      }
      """.trimIndent()

      val findings = ModifierDefaultValue().lint(code)

      findings shouldHaveSize 1
    }

    should("not report if modifier parameter has a correct default value") {
      // language=kotlin
      val code = """
      @Composable
      @Preview
      fun Test(modifier: Modifier = Modifier) {
        Text(text = "3")
      }
      """.trimIndent()

      val findings = ModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report if modifier parameter defaults to the companion written out") {
      listOf(
        "Modifier.Companion",
        "androidx.compose.ui.Modifier",
        "androidx.compose.ui.Modifier.Companion",
        "Modifier\n          .Companion",
      ).forEach { defaultValue ->
        // language=kotlin
        val code = """
        @Composable
        fun Test(modifier: Modifier = $defaultValue) {
        }
        """.trimIndent()

        val findings = ModifierDefaultValue().lint(code)

        withClue(defaultValue) { findings.shouldBeEmpty() }
      }
    }

    should("report if modifier parameter chains off the companion written out") {
      // language=kotlin
      val code = """
      @Composable
      fun Test(modifier: Modifier = Modifier.Companion.padding(1.dp)) {
      }
      """.trimIndent()

      val findings = ModifierDefaultValue().lint(code)

      findings shouldHaveSize 1
    }

    should("ignore non-composable functions") {
      // language=kotlin
      val code = """
      fun Test(modifier: Modifier = Modifier.fillMaxSize()) {
      }
      """.trimIndent()

      val findings = ModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }
  },
)
