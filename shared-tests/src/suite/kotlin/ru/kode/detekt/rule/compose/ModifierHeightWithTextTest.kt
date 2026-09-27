/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import dev.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class ModifierHeightWithTextTest : ShouldSpec({
  should("report with single modifier") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(
          modifier = modifier
            .height(24.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val color = Color.White
          Text(
            modifier = Modifier.weight(1f),
            style = DomInvestTheme.typography.caption2,
            color = color,
            text = props.title,
          )
        }
    }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings shouldHaveSize 1
  }

  should("report with multiple modifiers") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(
          modifier = modifier
            .height(24.dp)
            .weight(1f),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val color = Color.White
          Text(
            modifier = Modifier.weight(1f),
            style = DomInvestTheme.typography.caption2,
            color = color,
            text = props.title,
          )
        }
    }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings shouldHaveSize 1
  }

  should("report with modifier on same line") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(
          modifier = modifier.height(24.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val color = Color.White
          Text(
            modifier = Modifier.weight(1f),
            style = DomInvestTheme.typography.caption2,
            color = color,
            text = props.title,
          )
        }
    }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings shouldHaveSize 1
  }

  should("report with modifier without modifier arg name") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(
          modifier.height(24.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val color = Color.White
          Text(
            modifier = Modifier.weight(1f),
            style = DomInvestTheme.typography.caption2,
            color = color,
            text = props.title,
          )
        }
    }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings shouldHaveSize 1
  }

  should("report with modifier with argument name") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(
          modifier = modifier
            .height(height = 24.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val color = Color.White
          Text(
            modifier = Modifier.weight(1f),
            style = DomInvestTheme.typography.caption2,
            color = color,
            text = props.title,
          )
        }
    }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings shouldHaveSize 1
  }

  should("not report with heightIn modifier") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(
          modifier = modifier
            .heightIn(24.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val color = Color.White
          Text(
            modifier = Modifier.weight(1f),
            style = DomInvestTheme.typography.caption2,
            color = color,
            text = props.title,
          )
        }
    }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report with heightIn modifier with argument name") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(
          modifier = modifier
            .heightIn(min = 24.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val color = Color.White
          Text(
            modifier = Modifier.weight(1f),
            style = DomInvestTheme.typography.caption2,
            color = color,
            text = props.title,
          )
        }
    }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report when no Text inside") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(
          modifier = modifier
            .height(24.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val color = Color.White
          SummaryTextComponent(
            modifier = Modifier.weight(1f),
            style = DomInvestTheme.typography.caption2,
            color = color,
            text = props.title,
          )
        }
    }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report when Text is in grandchild") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(
          modifier = modifier
            .height(24.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              modifier = Modifier.weight(1f),
              style = DomInvestTheme.typography.caption2,
              color = color,
              text = props.title,
            )
          }
        }
    }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report when height call is not a modifier") {
    // language=kotlin
    val code = """
      fun height(x: Dp) = Alignment.CenterVertically

      @Composable
      fun Test() {
        Row(
          verticalAlignment = height(8.dp)
        ) {
          val color = Color.White
          Text(
            modifier = Modifier.weight(1f),
            style = DomInvestTheme.typography.caption2,
            color = color,
            text = props.title,
          )
        }
    }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings.shouldBeEmpty()
  }

  should("report every nested layout that has a height modifier and a Text child") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(modifier = Modifier.height(48.dp)) {
          Text(text = "outer")
          Column(modifier = Modifier.height(24.dp)) {
            Text(text = "inner")
          }
        }
      }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings shouldHaveSize 2
  }

  should("report in an expression-bodied composable") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() = Row(modifier = Modifier.height(48.dp)) {
        Text(text = "outer")
      }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings shouldHaveSize 1
  }

  should("not report heightIn in an expression-bodied composable") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() = Row(modifier = Modifier.heightIn(min = 48.dp)) {
        Text(text = "outer")
      }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings.shouldBeEmpty()
  }

  should("ignore non-composable functions") {
    // language=kotlin
    val code = """
      fun Test() {
        Row(modifier = Modifier.height(48.dp)) {
          Text(text = "outer")
        }
      }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings.shouldBeEmpty()
  }

  // https://github.com/appKODE/detekt-rules-compose/issues/25, #34
  listOf(
    "IntrinsicSize.Min",
    "IntrinsicSize.Max",
    "Min",
    "Max",
    "androidx.compose.foundation.layout.IntrinsicSize.Min",
    "androidx.compose.foundation.layout.IntrinsicSize.Max",
  ).forEach { size ->
    should("not report height($size) passed as a named modifier argument") {
      // language=kotlin
      val code = """
        @Composable
        fun Test() {
          Row(
            modifier = Modifier
              .height($size),
          ) {
            Text(
              modifier = Modifier
                .fillMaxHeight(),
              text = "Hello world",
            )
          }
        }
      """.trimIndent()

      val findings = ModifierHeightWithText().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report height($size) passed as a positional modifier argument") {
      // language=kotlin
      val code = """
        @Composable
        fun Test() {
          Row(Modifier.height($size)) {
            Text(text = "Hello world")
          }
        }
      """.trimIndent()

      val findings = ModifierHeightWithText().lint(code)

      findings.shouldBeEmpty()
    }
  }

  should("report a fixed height passed as a positional modifier argument") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(Modifier.height(24.dp)) {
          Text(text = "Hello world")
        }
      }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings shouldHaveSize 1
    findings.single().message shouldBe
      "Composable uses \"height\" modifier and contains a Text child. Use heightIn(min = N.dp) instead"
    findings.single().shouldStartAt(code, "height(24.dp)")
  }

  should("report a fixed height next to an intrinsic height") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(modifier = Modifier.height(IntrinsicSize.Min).height(24.dp)) {
          Text(text = "Hello world")
        }
      }
    """.trimIndent()

    val findings = ModifierHeightWithText().lint(code)

    findings shouldHaveSize 1
    findings.single().shouldStartAt(code, "height(24.dp)")
  }
})
