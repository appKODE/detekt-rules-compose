package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import ru.kode.detekt.rule.compose.contract.SharedRuleContracts
import ru.kode.detekt.rule.compose.snippet.composeSnippet

private const val TEST_COMPOSE_PACKAGE = "ru.kode.detekt.rule"

/**
 * detekt1 lets `lint` run type-resolution rules with an empty BindingContext (detekt-core itself skips them when
 * there is no classpath). Every semantic query then answers "unknown", which must not crash.
 */
class NoTypeResolutionTest : ShouldSpec({
  val rules = mapOf(
    "ConditionCouldBeLifted" to { ConditionCouldBeLifted(composableAnnotationClassPackage = TEST_COMPOSE_PACKAGE) },
    "ReusedModifierInstance" to { ReusedModifierInstance(modifierClassPackage = TEST_COMPOSE_PACKAGE) },
    "UnnecessaryEventHandlerParameter" to { UnnecessaryEventHandlerParameter() },
  )

  SharedRuleContracts.heavyParityCases.forEach { parityCase ->
    should("not crash without type resolution: ${parityCase.name}") {
      val findings = rules.getValue(parityCase.ruleId)().lint(composeSnippet(parityCase.code))

      findings shouldHaveSize parityCase.expectedUnresolvedFindings
    }
  }

  should("tell an event handler call from a same-named function call by its arguments") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        fun onClick(id: Int, label: String) {}

        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          Button(onClick = { onClick(id = data.id, label = "x") }) {}
        }
      """.trimIndent(),
    )

    UnnecessaryEventHandlerParameter().lint(code).shouldBeEmpty()
  }

  should("not take a same-named function call with another number of arguments for an event handler call") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        fun onClick(id: Int, position: Int) {}

        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          Button(onClick = { onClick(data.id, 0) }) {}
        }
      """.trimIndent(),
    )

    UnnecessaryEventHandlerParameter().lint(code).shouldBeEmpty()
  }

  should("not take a same-named function call with a named argument for an event handler call") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        fun onClick(id: Int) {}

        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          Button(onClick = { onClick(id = data.id) }) {}
        }
      """.trimIndent(),
    )

    UnnecessaryEventHandlerParameter().lint(code).shouldBeEmpty()
  }
})
