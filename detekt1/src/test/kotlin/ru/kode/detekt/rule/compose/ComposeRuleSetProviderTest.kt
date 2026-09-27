package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.detekt.rule.compose.contract.SharedRuleContracts
import java.util.ServiceLoader

class ComposeRuleSetProviderTest : ShouldSpec({
  should("expose all compose rules") {
    val ruleSet = ComposeRuleSetProvider().instance(TestConfig())

    ruleSet.id shouldBe "compose"
    ruleSet.rules.size shouldBe SharedRuleContracts.expectedRuleIds.size
    ruleSet.rules.map { it::class.simpleName!! }.toSet() shouldBe SharedRuleContracts.expectedRuleIds
  }

  should("be discoverable through service loader") {
    val providers = ServiceLoader.load(RuleSetProvider::class.java).toList()
    providers.any { it is ComposeRuleSetProvider } shouldBe true
  }
})
