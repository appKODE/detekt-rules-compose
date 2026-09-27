package ru.kode.detekt.rule.compose

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class ComposeRuleSetProvider : RuleSetProvider {
  override val ruleSetId: RuleSetId = RuleSetId("compose")

  override fun instance(): RuleSet {
    return RuleSet(
      ruleSetId,
      listOf(
        { config -> ModifierHeightWithText(config) },
        { config -> ReusedModifierInstance(config) },
        { config -> PublicComposablePreview(config) },
        { config -> ComposableEventParameterNaming(config) },
        { config -> UnnecessaryEventHandlerParameter(config) },
        { config -> ComposableParametersOrdering(config) },
        { config -> ModifierDefaultValue(config) },
        { config -> MissingModifierDefaultValue(config) },
        { config -> TopLevelComposableFunctions(config) },
        { config -> ComposableFunctionName(config) },
        { config -> ConditionCouldBeLifted(config) },
        { config -> UnnecessaryLayoutWrapper(config) },
      ),
    )
  }
}
