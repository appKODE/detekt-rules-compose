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
        ModifierOnWrongLevel(config),
        PublicComposablePreview(config),
        ModifierArgumentPosition(config),
        ModifierParameterPosition(config),
        ComposableEventParameterNaming(config),
        UnnecessaryEventHandlerParameter(config)
      )
    )
  }
}
