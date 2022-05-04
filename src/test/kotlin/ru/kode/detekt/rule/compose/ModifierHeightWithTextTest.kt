package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize

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
})
