package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
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
  // UnnecessaryEventHandlerParameter only uses resolution to skip sealed receivers, so it keeps reporting
  val expectedFindings = mapOf(
    "ConditionCouldBeLifted" to 0,
    "ReusedModifierInstance" to 0,
    "UnnecessaryEventHandlerParameter" to 1,
  )

  SharedRuleContracts.heavyParityCases.forEach { parityCase ->
    should("not crash without type resolution: ${parityCase.ruleId}") {
      val findings = rules.getValue(parityCase.ruleId)().lint(composeSnippet(parityCase.code))

      findings shouldHaveSize expectedFindings.getValue(parityCase.ruleId)
    }
  }
})
