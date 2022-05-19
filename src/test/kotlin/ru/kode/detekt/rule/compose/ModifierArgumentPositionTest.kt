/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize

class ModifierArgumentPositionTest : ShouldSpec({
  should("report when incorrect position with named invocation") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = modifier
            .height(24.dp),
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

    val findings = ModifierArgumentPosition().lint(code)

    findings shouldHaveSize 1
  }

  should("report with named hierarchical invocation") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          val color = Color.White
          Text(
            style = DomInvestTheme.typography.caption2,
            modifier = Modifier.weight(1f),
            color = color,
            text = props.title,
          )
        }
    }
    """.trimIndent()

    val findings = ModifierArgumentPosition().lint(code)

    findings shouldHaveSize 1
  }

  should("not report when in first position") {
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

    val findings = ModifierArgumentPosition().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report not named invocations") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(
          Alignment.CenterVertically,
          modifier.height(24.dp),
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

    val findings = ModifierArgumentPosition().lint(code)

    findings.shouldBeEmpty()
  }
})
