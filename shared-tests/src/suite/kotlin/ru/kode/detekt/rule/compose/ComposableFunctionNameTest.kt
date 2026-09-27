/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import dev.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class ComposableFunctionNameTest : ShouldSpec({
  should("report when returns Unit and starts with lower case") {
    // language=kotlin
    val code = """
      @Composable
      fun test(modifier: Modifier) {
        Text(text = "3")
      }
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings shouldHaveSize 1
  }

  should("report when returns Unit explicitly and starts with lower case") {
    // language=kotlin
    val code = """
      @Composable
      fun test(modifier: Modifier): Unit {
        Text(text = "3")
      }
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings shouldHaveSize 1
  }

  should("not report when returns Unit and starts with upper case") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier) {
        Text(text = "3")
      }
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report when returns Unit explicitly and starts with upper case") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier): Unit {
        Text(text = "3")
      }
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings.shouldBeEmpty()
  }

  should("report when returns Int inline and starts with upper case") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier) = 3
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings shouldHaveSize 1
  }

  should("report when returns Int explicitly inline and starts with upper case") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier): Int = 3
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings shouldHaveSize 1
  }

  should("report when returns Int and starts with upper case") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier): Int{
        return 3
      }
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings shouldHaveSize 1
  }

  should("not report when returns Int inline and starts with lower case") {
    // language=kotlin
    val code = """
      @Composable
      fun test(modifier: Modifier) = 3
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report when returns Int explicitly inline and starts with lower case") {
    // language=kotlin
    val code = """
      @Composable
      fun test(modifier: Modifier): Int = 3
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report when returns Int and starts with lower case") {
    // language=kotlin
    val code = """
      @Composable
      fun test(modifier: Modifier): Int {
        return 3
      }
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report upper case expression-bodied composable calling a layout") {
    // language=kotlin
    val code = """
      @Composable
      fun Screen() = Box {}
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report upper case composable returning kotlin.Unit explicitly") {
    // language=kotlin
    val code = """
      @Composable
      fun Screen(): kotlin.Unit {
      }
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings.shouldBeEmpty()
  }

  should("report extension functions with a receiver by name") {
    // language=kotlin
    val code = """
      @Composable
      fun RowScope.item() {
      }
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings.single().message shouldBe "Composable function 'item' should start with upper case"
  }

  should("ignore non-composable functions") {
    // language=kotlin
    val code = """
      fun button() {
      }
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings.shouldBeEmpty()
  }

  should("honour @Suppress on the function") {
    // language=kotlin
    val code = """
      @Suppress("ComposableFunctionName")
      @Composable
      fun button() {
      }
    """.trimIndent()

    val findings = ComposableFunctionName().lint(code)

    findings.shouldBeEmpty()
  }
})
