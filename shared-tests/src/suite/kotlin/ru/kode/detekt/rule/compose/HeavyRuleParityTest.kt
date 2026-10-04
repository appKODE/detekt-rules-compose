package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.Rule
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import ru.kode.detekt.rule.compose.contract.HeavyRuleParityCase
import ru.kode.detekt.rule.compose.contract.SharedRuleContracts
import ru.kode.detekt.rule.compose.snippet.composeSnippet

private const val TEST_COMPOSE_PACKAGE = "ru.kode.detekt.rule"

class HeavyRuleParityTest : ShouldSpec({
  val environment = createEnvironment()

  SharedRuleContracts.heavyParityCases.forEach { parityCase ->
    should("shared parity: ${parityCase.name}") {
      val rule = createHeavyRule(parityCase)
      val findings = when (rule) {
        is ConditionCouldBeLifted -> rule.lintWithContext(environment, composeSnippet(parityCase.code))
        is ReusedModifierInstance -> rule.lintWithContext(environment, composeSnippet(parityCase.code))
        is UnnecessaryEventHandlerParameter -> rule.lintWithContext(environment, composeSnippet(parityCase.code))
        else -> error("Unexpected heavy rule type: ${rule::class.simpleName}")
      }
      findings.map { it.message } shouldBe parityCase.expectedMessages
    }
  }

  // Without the fake Compose declarations nothing resolves: rules must degrade gracefully, not crash.
  SharedRuleContracts.heavyParityCases.forEach { parityCase ->
    should("not crash on unresolved symbols: ${parityCase.name}") {
      val findings = when (val rule = createHeavyRule(parityCase)) {
        is ConditionCouldBeLifted -> rule.lintWithContext(environment, parityCase.code)
        is ReusedModifierInstance -> rule.lintWithContext(environment, parityCase.code)
        is UnnecessaryEventHandlerParameter -> rule.lintWithContext(environment, parityCase.code)
        else -> error("Unexpected heavy rule type: ${rule::class.simpleName}")
      }
      findings shouldHaveSize parityCase.expectedUnresolvedFindings
    }
  }
})

private fun createHeavyRule(parityCase: HeavyRuleParityCase): Rule {
  return when (parityCase.ruleId) {
    "ConditionCouldBeLifted" -> ConditionCouldBeLifted(
      config = Config.empty,
      composableAnnotationClassPackage = TEST_COMPOSE_PACKAGE,
    )

    "ReusedModifierInstance" -> ReusedModifierInstance(
      config = Config.empty,
      modifierClassPackage = TEST_COMPOSE_PACKAGE,
    )

    "UnnecessaryEventHandlerParameter" -> UnnecessaryEventHandlerParameter(Config.empty)

    else -> error("Unknown heavy rule id: ${parityCase.ruleId}")
  }
}
