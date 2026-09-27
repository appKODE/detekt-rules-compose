/*
 * Copyright 2026 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import dev.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

private val layouts = listOf("Box", "Column", "Row")

class UnnecessaryLayoutWrapperTest : ShouldSpec({
  context("report a parameterless layout wrapping a single layout") {
    withData(
      nameFn = { (parent, child) -> "$parent { $child {} }" },
      layouts.flatMap { parent -> layouts.map { child -> parent to child } },
    ) { (parent, child) ->
      // language=kotlin
      val code = """
        @Composable
        fun Test() {
          $parent {
            $child(modifier = Modifier.padding(16.dp)) {
              Text("hello")
            }
          }
        }
      """.trimIndent()

      val finding = UnnecessaryLayoutWrapper().lint(code).single()

      finding.message shouldBe "\"$parent\" has no parameters and wraps a single \"$child\", it is likely unnecessary"
      finding.shouldStartAt(code, "$parent {")
    }
  }

  context("not report") {
    withData(
      mapOf(
        "a wrapper with arguments" to "Box(modifier = Modifier.fillMaxSize()) { Row {} }",
        "a wrapper with type arguments" to "Box<Int> { Row {} }",
        "a wrapper with two children" to "Box {\n Row {}\n Column {}\n}",
        "a non-layout child" to "Box { Text(\"hello\") }",
        "an if around the child" to "Box { if (visible) Row {} }",
        "a when around the child" to "Box {\n when (visible) {\n true -> Row {}\n else -> Column {}\n }\n}",
        "a for around the child" to "Box { for (item in items) Row {} }",
        "a forEach around the child" to "Box { items.forEach { Row {} } }",
        "a val next to the child" to "Box {\n val padding = 16.dp\n Row(Modifier.padding(padding)) {}\n}",
        "a child using weight" to "Row { Box(modifier = Modifier.weight(1f)) {} }",
        "a child using align" to "Box { Row(Modifier.align(Alignment.Center)) {} }",
        "a child using alignBy" to "Row { Column(Modifier.alignBy(FirstBaseline)) {} }",
        "a child using alignByBaseline" to "Row { Column(Modifier.padding(4.dp).alignByBaseline()) {} }",
        "a child using matchParentSize" to "Box { Column(Modifier.matchParentSize()) {} }",
        "a lambda with parameters" to "Row { scope -> Column {} }",
        "a named content lambda" to "Box(content = { Row {} })",
        "a dot-qualified child" to "Box { scope.Row {} }",
        "an empty wrapper" to "Box {}",
      ),
    ) { body ->
      // language=kotlin
      val code = """
        @Composable
        fun Test(visible: Boolean, items: List<Int>) {
          $body
        }
      """.trimIndent()

      UnnecessaryLayoutWrapper().lint(code).shouldBeEmpty()
    }
  }

  should("report both wrappers of a nested chain") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Box { Box { Row {} } }
      }
    """.trimIndent()

    UnnecessaryLayoutWrapper().lint(code) shouldHaveSize 2
  }

  should("report a wrapper with empty parentheses") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Box() { Row {} }
      }
    """.trimIndent()

    UnnecessaryLayoutWrapper().lint(code) shouldHaveSize 1
  }

  should("report wrappers in default parameter values, slot lambdas, expression bodies and key") {
    // language=kotlin
    val code = """
      @Composable
      fun DefaultParam(content: @Composable () -> Unit = { Box { Row {} } }) {}

      @Composable
      fun Slot() {
        Scaffold(topBar = { Column { Row {} } }) {}
      }

      @Composable
      fun ExpressionBody() = Box { Column {} }

      @Composable
      fun Keyed(id: Int) {
        key(id) {
          Row { Box {} }
        }
      }
    """.trimIndent()

    UnnecessaryLayoutWrapper().lint(code) shouldHaveSize 4
  }

  should("report wrappers and children with labelled lambdas") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Box wrapper@{ Row {} }
        Column { Row child@{} }
        Row { Box content@{ Text("hello", Modifier.align(Alignment.Center)) } }
        Column { Box(content = named@{ Text("hello", Modifier.align(Alignment.Center)) }) }
      }
    """.trimIndent()

    UnnecessaryLayoutWrapper().lint(code) shouldHaveSize 4
  }

  should("honour @Suppress on the function") {
    // language=kotlin
    val code = """
      @Suppress("UnnecessaryLayoutWrapper")
      @Composable
      fun Test() {
        Box { Row {} }
      }
    """.trimIndent()

    UnnecessaryLayoutWrapper().lint(code).shouldBeEmpty()
  }
})
