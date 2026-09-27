/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import ru.kode.detekt.rule.compose.snippet.composeSnippet

class ReusedModifierInstanceTest : ShouldSpec({

  val environment = createEnvironment()

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("report modifier passed positionally to a grand-child") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(modifier: Modifier = Modifier) {
        Row(modifier) {
          Text("hello", modifier.fillMaxSize())
        }
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("resolve layouts imported under an alias") {
    // language=kotlin
    val code = composeSnippet(
      imports = "import ru.kode.detekt.rule.Row as Line",
      code =
      """
      @Composable
      fun Test(modifier: Modifier = Modifier) {
        Line {
          Text(text = "hello", modifier = modifier)
        }
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("not report when the function has no modifier parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test() {
        val modifier = Modifier
        Row {
          Text(text = "hello", modifier = modifier)
        }
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("ignore non-composable functions") {
    // language=kotlin
    val code = composeSnippet(
      """
      fun Test(modifier: Modifier = Modifier) {
        Row {
          Text(text = "hello", modifier = modifier)
        }
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("error on wrong modifier in an expression-bodied composable") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(modifier: Modifier) = Row(modifier = modifier) {
        Text(text = "hello", modifier = modifier.weight(1f))
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("not error on correct modifiers in an expression-bodied composable") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(modifier: Modifier) = Row(modifier = modifier) {
        Text(text = "hello", modifier = Modifier.weight(1f))
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  // https://github.com/appKODE/detekt-rules-compose/issues/30
  should("not error on a lambda parameter shadowing modifier") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Extra(a: @Composable (Modifier) -> Unit) {}
      @Composable
      fun Test(
        modifier: Modifier = Modifier,
      ) {
        Column(modifier) {
          Extra { modifier ->
            Text("Hi", modifier)
          }
        }
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("error on modifier inside a lambda whose parameter has another name") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Extra(a: @Composable (Modifier) -> Unit) {}
      @Composable
      fun Test(
        modifier: Modifier = Modifier,
      ) {
        Column(modifier) {
          Extra { other ->
            Text("Hi", modifier)
          }
        }
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.single().message shouldBe
      "Composable uses \"modifier\" on the wrong level, non-direct children should use \"Modifier\""
    findings.single().shouldStartAt(code, "Text(\"Hi\", modifier)")
  }

  should("not error on a local val or a destructured val shadowing modifier") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(modifier: Modifier = Modifier, pair: Pair<Modifier, Modifier>) {
        Column(modifier) {
          Row {
            val modifier = Modifier.weight(1f)
            Text("Hi", modifier)
          }
          Row {
            val (modifier, other) = pair
            Text("Hi", modifier.fillMaxSize())
          }
        }
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("error on modifier used before a local val shadowing it") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(modifier: Modifier = Modifier) {
        Column(modifier) {
          Text("Hi", modifier)
          val modifier = Modifier.weight(1f)
        }
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.single().message shouldBe
      "Composable uses \"modifier\" on the wrong level, non-direct children should use \"Modifier\""
    findings.single().shouldStartAt(code, "Text(\"Hi\", modifier)")
  }

  should("not error on a for-loop variable shadowing modifier") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(modifier: Modifier = Modifier, modifiers: List<Modifier>) {
        Column(modifier) {
          for (modifier in modifiers) {
            Text("Hi", modifier)
          }
        }
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("error on modifier used through a local val derived from it") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(modifier: Modifier = Modifier) {
        Column(modifier) {
          Row {
            val modifier = modifier.padding(4.dp)
            Text("Hi", modifier)
          }
        }
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.single().message shouldBe
      "Composable uses \"modifier\" on the wrong level, non-direct children should use \"Modifier\""
    findings.single().shouldStartAt(code, "Text(\"Hi\", modifier)")
  }

  should("not error on modifier declared by a when subject or a local object") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(modifier: Modifier = Modifier) {
        Column(modifier) {
          when (val modifier = Modifier.weight(1f)) {
            else -> Text("Hi", modifier)
          }
          val holder = object {
            @Composable
            fun Show() {
              Text("Hi", modifier)
            }

            val modifier = Modifier.weight(1f)
          }
        }
      }
      """.trimIndent(),
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }
})

private fun createRule() = ReusedModifierInstance(modifierClassPackage = "ru.kode.detekt.rule")
