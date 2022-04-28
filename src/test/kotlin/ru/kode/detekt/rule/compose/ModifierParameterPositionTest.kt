package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize

class ModifierParameterPositionTest : ShouldSpec({
    should("report when incorrect position") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          verticalAlignment = Alignment.CenterVertically,
          modifier = modifier.height(24.dp),
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ModifierParameterPosition().lint(code)

        findings shouldHaveSize 1
    }

    should("not report when in first position") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          modifier = modifier.height(24.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ModifierParameterPosition().lint(code)

        findings.shouldBeEmpty()
    }
})
