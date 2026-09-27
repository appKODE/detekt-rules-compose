package ru.kode.detekt.rule.compose

import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.detekt.rule.compose.contract.SharedRuleContracts
import java.util.ServiceLoader

class ComposeRuleSetProviderTest : ShouldSpec({
  should("expose all compose rules") {
    val provider = ComposeRuleSetProvider()
    val ruleSet = provider.instance()

    provider.ruleSetId shouldBe RuleSetId("compose")
    ruleSet.rules.size shouldBe SharedRuleContracts.expectedRuleIds.size
    ruleSet.rules.keys.map { it.value }.toSet() shouldBe SharedRuleContracts.expectedRuleIds
  }

  should("be discoverable through service loader") {
    val providers = ServiceLoader.load(RuleSetProvider::class.java).toList()
    providers.any { it is ComposeRuleSetProvider } shouldBe true
  }
})
