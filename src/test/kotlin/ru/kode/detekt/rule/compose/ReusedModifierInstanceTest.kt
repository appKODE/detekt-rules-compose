/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import ru.kode.detekt.rule.compose.snippet.composeSnippet

class ReusedModifierInstanceTest : ShouldSpec({

  val envWrapper = createEnvironment()
  val env: KotlinCoreEnvironment = envWrapper.env

  afterSpec { envWrapper.dispose() }

  should("error on wrong modifier on grand-children") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(modifier: Modifier, value: Int) {
        Row(
          modifier = modifier,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            modifier = modifier.weight(1f), // should be Modifier
            text = "hello",
          )
        }
      }
      """.trimIndent(),
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings shouldHaveSize 1
  }

  should("error on wrong modifier on grand-grand-children") {
    // language=kotlin
    val code = composeSnippet(
      """
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
      """.trimIndent(),
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings shouldHaveSize 1
  }

  should("not error on modifier on direct children") {
    // language=kotlin
    val code = composeSnippet(
      """
data class SummaryRowProps(val title: String, val value: String)
@Composable
fun SummaryRow(
  modifier: Modifier = Modifier,
  props: SummaryRowProps,
) {
  Row(
    modifier = modifier
      .padding(horizontal = 16.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      modifier = Modifier.weight(1f),
      text = props.title,
    )
    Text(
      modifier = Modifier,
      text = props.value,
    )
  }
}
      """.trimIndent(),
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }

  should("error on modifier without expression chain") {
    // language=kotlin
    val code = composeSnippet(
      """
@Composable
fun CollapsableHeader(
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
      """.trimIndent(),
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings shouldHaveSize 1
  }

  should("not error on Modifier without expression chain") {
    // language=kotlin
    val code = composeSnippet(
      """
@Composable
fun CollapsableHeader(
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
      """.trimIndent(),
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }

  should("report when modifier is reused in a call wrapped in a conditional expression") {
    // language=kotlin
    val code = composeSnippet(
      """
@Composable
fun Test(
  value: String?,
  value2: String?,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
  ) {
    if (value != null) {
      Row(
        modifier = Modifier.padding(16.dp),
      ) {
        Column(
          modifier = Modifier
            .padding(8.dp)
        ) {
          if (value2 != null) {
            Text(
              modifier = modifier,
              text = value2,
            )
          }
        }
      }
    }
  }
}
      """.trimIndent(),
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings shouldHaveSize 1
  }

  should("not report when nested composable calls has no modifier argument") {
    // language=kotlin
    val code = composeSnippet(
      """
@Composable
fun ProvideWindowInsets(content: @Composable () -> Unit) = Unit
fun ProvideFooBar(content: @Composable () -> Unit) = Unit
@Composable
fun Test(
  modifier: Modifier = Modifier
) {
  ProvideWindowInsets {
    ProvideFooBar {
      Column(
        modifier = modifier,
      ) {
      }
    }
  }
}
      """.trimIndent(),
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }

  should("report when top composable call has no modifier argument, but its reused in its children") {
    // language=kotlin
    val code = composeSnippet(
      """
@Composable
fun ProvideWindowInsets(content: @Composable () -> Unit) = Unit
fun ProvideFooBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Unit
@Composable
fun Test(
  modifier: Modifier = Modifier
) {
  ProvideWindowInsets {
    ProvideFooBar {
      Column(
        modifier = modifier,
      ) {
      }
    }
  }
}
      """.trimIndent(),
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings shouldHaveSize 1
  }
})

private fun createRule() = ReusedModifierInstance(modifierClassPackage = "ru.kode.detekt.rule")
