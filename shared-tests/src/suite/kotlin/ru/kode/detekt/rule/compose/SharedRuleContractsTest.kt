package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.kode.detekt.rule.compose.contract.RuleContractCase
import ru.kode.detekt.rule.compose.contract.SharedRuleContracts
import ru.kode.detekt.rule.compose.snippet.composeSnippet

private const val TEST_COMPOSE_PACKAGE = "ru.kode.detekt.rule"

class SharedRuleContractsTest : ShouldSpec({
  val environment = createEnvironment()

  fun lint(contract: RuleContractCase, code: String): List<Finding> = when (val rule = createRule(contract)) {
    is ConditionCouldBeLifted -> rule.lintWithContext(environment, code)
    is ReusedModifierInstance -> rule.lintWithContext(environment, code)
    is UnnecessaryEventHandlerParameter -> rule.lintWithContext(environment, code)
    else -> rule.lint(code)
  }

  SharedRuleContracts.allCases.forEach { contract ->
    should("shared contract: ${contract.ruleId}") {
      val findings = lint(contract, composeSnippet(contract.code))

      findings shouldHaveSize contract.expectedCount
      val allMessages = findings.joinToString("\n") { it.message }
      contract.expectedMessageContains.forEach { expected ->
        allMessages shouldContain expected
      }
    }
  }

  SharedRuleContracts.allCases.forEach { contract ->
    should("report exact message at exact position: ${contract.ruleId}") {
      val code = composeSnippet(contract.code)
      val (anchor, message) = expectedFindings.getValue(contract.ruleId)

      val finding = lint(contract, code).single()

      finding.message shouldBe message
      finding.shouldStartAt(code, anchor)
    }

    should("report nothing for an empty file: ${contract.ruleId}") {
      lint(contract, "").shouldBeEmpty()
    }

    should("honour file-level @Suppress: ${contract.ruleId}") {
      val code = "@file:Suppress(\"${contract.ruleId}\")\n\n" + composeSnippet(contract.code)

      lint(contract, code).shouldBeEmpty()
    }
  }
})

// Anchor (start of the reported element) and full message for each shared contract case
private val expectedFindings = mapOf(
  "ComposableEventParameterNaming" to (
    "clicked: () -> Unit" to
      "Invalid event parameter name \"clicked\". Use names like \"onClick\", \"onValueChange\" etc"
    ),
  "ComposableFunctionName" to (
    "button() {}" to
      "Composable function 'button' should start with upper case"
    ),
  "ComposableParametersOrdering" to (
    "enabled: Boolean = false" to
      "Composable function parameters should follow this order: required parameters, modifier parameter, optional parameters, composable slots"
    ),
  "ConditionCouldBeLifted" to (
    "if (printValue)" to
      "Condition could be lifted out of \"Column\""
    ),
  "MissingModifierDefaultValue" to (
    "modifier: Modifier) {" to
      "Modifier parameter should have a default value: \"modifier = Modifier\""
    ),
  "ModifierDefaultValue" to (
    "modifier: Modifier = Modifier.fillMaxSize()" to
      "Modifier parameter should not have a default value other than \"Modifier\""
    ),
  "ModifierHeightWithText" to (
    "height(24.dp)" to
      "Composable uses \"height\" modifier and contains a Text child. Use heightIn(min = N.dp) instead"
    ),
  "PublicComposablePreview" to (
    "@Preview\n@Composable" to
      "Preview composable must not be public"
    ),
  "ReusedModifierInstance" to (
    "Column(modifier = modifier.fillMaxSize()) {}" to
      "Composable uses \"modifier\" on the wrong level, non-direct children should use \"Modifier\""
    ),
  "TopLevelComposableFunctions" to (
    "@Composable\n  fun Content" to
      "Composable functions should be defined as top-level functions"
    ),
  "UnnecessaryEventHandlerParameter" to (
    "onClick: (Int) -> Unit" to
      "Unnecessary event callback arguments. Move all \"data\" access to the parent composable event handler and switch \"onClick\" type to \"() -> Unit\""
    ),
)

private fun createRule(contract: RuleContractCase): Rule {
  val config = if (contract.config.isEmpty()) {
    Config.empty
  } else {
    TestConfig(*contract.config.map { it.key to it.value }.toTypedArray())
  }
  return when (contract.ruleId) {
    "ComposableEventParameterNaming" -> ComposableEventParameterNaming(config)

    "ComposableFunctionName" -> ComposableFunctionName(config)

    "ComposableParametersOrdering" -> ComposableParametersOrdering(config)

    "ConditionCouldBeLifted" -> ConditionCouldBeLifted(
      config = config,
      composableAnnotationClassPackage = TEST_COMPOSE_PACKAGE,
    )

    "MissingModifierDefaultValue" -> MissingModifierDefaultValue(config)

    "ModifierDefaultValue" -> ModifierDefaultValue(config)

    "ModifierHeightWithText" -> ModifierHeightWithText(config)

    "PublicComposablePreview" -> PublicComposablePreview(config)

    "ReusedModifierInstance" -> ReusedModifierInstance(
      config = config,
      modifierClassPackage = TEST_COMPOSE_PACKAGE,
    )

    "TopLevelComposableFunctions" -> TopLevelComposableFunctions(config)

    "UnnecessaryEventHandlerParameter" -> UnnecessaryEventHandlerParameter(config)

    else -> error("Unknown rule id: ${contract.ruleId}")
  }
}
