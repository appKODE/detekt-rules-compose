/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import dev.detekt.test.lint
import io.kotest.assertions.withClue
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

  should("ignore parameters with scoped receiver") {
    // language=kotlin
    val code = """
      @Composable
      fun Scaffold(
          topBar: @Composable () -> Unit,
          logo: @Composable BoxScope.() -> Unit,
          content: LazyListScope.(minContentHeight: Dp) -> Unit,
          bottomContent: @Composable ColumnScope.() -> Unit,
          modifier: Modifier = Modifier,
          snackbarHost: @Composable (SnackbarHostState) -> Unit = { SnackbarHost(it) },
      ) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings.shouldBeEmpty()
  }

  should("ignore non-composable functions") {
    // language=kotlin
    val code = """
      fun Test(click: () -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings.shouldBeEmpty()
  }

  should("report suspend event handlers") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(refresh: suspend () -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings shouldHaveSize 1
  }

  should("ignore composable slots") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(content: @Composable () -> Unit) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings.shouldBeEmpty()
  }

  should("report nullable event handler") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier, clicked: (() -> Unit)? = null) {
      }
    """.trimIndent()

    val findings = ComposableEventParameterNaming().lint(code)

    findings shouldHaveSize 1
  }

  should("ignore nullable composable slots") {
    listOf(
      "(@Composable () -> Unit)?",
      "@Composable (() -> Unit)?",
      "(@Composable RowScope.() -> Unit)?",
    ).forEach { type ->
      // language=kotlin
      val code = """
        @Composable
        fun Test(content: $type = null) {
        }
      """.trimIndent()

      val findings = ComposableEventParameterNaming().lint(code)

      withClue(type) { findings.shouldBeEmpty() }
    }
  }

  should("not report present tense verbs ending in ed and official Compose names") {
    listOf("onSpeed", "onFeed", "onNeed", "onSeed", "onBed", "onEmbed", "onFocusChanged", "onPlaced").forEach { name ->
      // language=kotlin
      val code = """
        @Composable
        fun Test(modifier: Modifier, $name: () -> Unit) {
        }
      """.trimIndent()

      val findings = ComposableEventParameterNaming().lint(code)

      withClue(name) { findings.shouldBeEmpty() }
    }
  }
})
