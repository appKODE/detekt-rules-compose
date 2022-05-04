package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize

class ReusedModifierInstanceTest : ShouldSpec({
  should("error on wrong modifier on grand-children") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier, value: Int) {
        Row(
          modifier = modifier,
          verticalAlignment = Alignment.CenterVertically
        ) {
          val color = Color.White
          Text(
            modifier = modifier.weight(1f), // should be Modifier
            style = DomInvestTheme.typography.caption2,
            color = color,
            text = props.title,
          )
        }
      }
    """.trimIndent()

    val findings = ReusedModifierInstance().lint(code)

    findings shouldHaveSize 1
  }

  should("error on wrong modifier on grand-grand-children") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(modifier: Modifier, value: Int) {
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Box(modifier = modifier.fillMaxSize()) { }
          }
        }
      }
    """.trimIndent()

    val findings = ReusedModifierInstance().lint(code)

    findings shouldHaveSize 1
  }

  should("not error on modifier on direct children") {
    // language=kotlin
    val code = """
@Composable
internal fun SummaryRow(
  modifier: Modifier = Modifier,
  props: SummaryRowProps,
) {
  Row(
    modifier = modifier
      .heightIn(min = 24.dp)
      .padding(horizontal = 16.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    val color = when (props.style) {
      SummaryRowProps.Style.Normal -> DomInvestTheme.colors.textSecondary
      SummaryRowProps.Style.Warning -> DomInvestTheme.colors.indicatorContentRed
    }
    Text(
      modifier = Modifier.weight(1f),
      style = DomInvestTheme.typography.caption2,
      color = color,
      text = props.title,
    )
    Text(
      modifier = Modifier,
      style = DomInvestTheme.typography.caption2,
      color = color,
      text = props.value,
    )
  }
}
    """.trimIndent()

    val findings = ReusedModifierInstance().lint(code)

    findings.shouldBeEmpty()
  }

  should("error on modifier without expression chain") {
    // language=kotlin
    val code = """
@Composable
internal fun CollapsableHeader(
  modifier: Modifier = Modifier,
  title: String,
  isExpanded: Boolean,
  onChangeSize: () -> Unit,
  onCancel: () -> Unit,
  collapsableContent: @Composable () -> Unit,
) {
  Column(modifier = modifier) {
    Row(
      modifier = modifier,
    ) {
    }
  }
}
    """.trimIndent()

    val findings = ReusedModifierInstance().lint(code)

    findings shouldHaveSize 1
  }

  should("not error on Modifier without expression chain") {
    // language=kotlin
    val code = """
@Composable
internal fun CollapsableHeader(
  modifier: Modifier = Modifier,
  title: String,
  isExpanded: Boolean,
  onChangeSize: () -> Unit,
  onCancel: () -> Unit,
  collapsableContent: @Composable () -> Unit,
) {
  Column(modifier = modifier) {
    Row(
      modifier = Modifier,
    ) {
    }
  }
}
    """.trimIndent()

    val findings = ReusedModifierInstance().lint(code)

    findings.shouldBeEmpty()
  }
})
