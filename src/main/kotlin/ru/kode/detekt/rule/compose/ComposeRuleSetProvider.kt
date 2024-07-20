package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class ComposeRuleSetProvider : RuleSetProvider {
  override val ruleSetId = "compose"

  override fun instance(config: Config): RuleSet {
    return RuleSet(
      ruleSetId,
      listOf(
        ModifierHeightWithText(config),
        ReusedModifierInstance(config),
        PublicComposablePreview(config),
        ModifierParameterPosition(config),
        ComposableEventParameterNaming(config),
        UnnecessaryEventHandlerParameter(config),
        ComposableParametersOrdering(config),
        ModifierDefaultValue(config),
        MissingModifierDefaultValue(config),
        TopLevelComposableFunctions(config),
        ComposableFunctionName(config),
        ConditionCouldBeLifted(config),
      ),
    )
  }
}
