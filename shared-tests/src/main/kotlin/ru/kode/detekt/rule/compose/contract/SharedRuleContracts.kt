package ru.kode.detekt.rule.compose.contract

data class RuleContractCase(
  val ruleId: String,
  val code: String,
  val expectedCount: Int,
  val expectedMessageContains: List<String>,
  val config: Map<String, Any> = emptyMap(),
)

data class HeavyRuleParityCase(
  val ruleId: String,
  val code: String,
  val expectedMessages: List<String>,
)

object SharedRuleContracts {
  val expectedRuleIds: Set<String> = setOf(
    "ModifierHeightWithText",
    "ReusedModifierInstance",
    "PublicComposablePreview",
    "ComposableEventParameterNaming",
    "UnnecessaryEventHandlerParameter",
    "ComposableParametersOrdering",
    "ModifierDefaultValue",
    "MissingModifierDefaultValue",
    "TopLevelComposableFunctions",
    "ComposableFunctionName",
    "ConditionCouldBeLifted",
    "UnnecessaryLayoutWrapper",
  )

  val allCases: List<RuleContractCase> = listOf(
    RuleContractCase(
      ruleId = "ComposableEventParameterNaming",
      code =
      """
        @Composable
        fun EventComponent(clicked: () -> Unit) {}
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("Invalid event parameter name"),
    ),
    RuleContractCase(
      ruleId = "ComposableFunctionName",
      code =
      """
        @Composable
        fun button() {}
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("should start with upper case"),
    ),
    RuleContractCase(
      ruleId = "ComposableParametersOrdering",
      code =
      """
        @Composable
        fun Header(enabled: Boolean = false, title: String) {}
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("parameters should follow this order"),
    ),
    RuleContractCase(
      ruleId = "ConditionCouldBeLifted",
      code =
      """
        @Composable
        fun Conditional() {
          val printValue = false
          Column {
            if (printValue) {
              Text(text = "3")
              Row {}
            }
          }
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("Condition could be lifted out of \"Column\""),
    ),
    RuleContractCase(
      ruleId = "MissingModifierDefaultValue",
      code =
      """
        @Composable
        fun MissingModifier(modifier: Modifier) {
          Text(text = "x")
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("Modifier parameter should have a default value"),
    ),
    RuleContractCase(
      ruleId = "ModifierDefaultValue",
      code =
      """
        @Composable
        fun InvalidDefault(modifier: Modifier = Modifier.fillMaxSize()) {
          Text(text = "x")
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("default value other than \"Modifier\""),
    ),
    RuleContractCase(
      ruleId = "ModifierHeightWithText",
      code =
      """
        @Composable
        fun HeightWithText(modifier: Modifier = Modifier) {
          Row(modifier = modifier.height(24.dp)) {
            Text(text = "hello")
          }
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("contains a Text child"),
    ),
    RuleContractCase(
      ruleId = "PublicComposablePreview",
      code =
      """
        @Preview
        @Composable
        fun ScreenPreview() {}
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("must not be public"),
    ),
    RuleContractCase(
      ruleId = "ReusedModifierInstance",
      code =
      """
        @Composable
        fun Reuse(modifier: Modifier = Modifier) {
          Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = modifier.fillMaxSize()) {}
          }
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("wrong level"),
    ),
    RuleContractCase(
      ruleId = "TopLevelComposableFunctions",
      code =
      """
        class Holder {
          @Composable
          fun Content() {}
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("top-level functions"),
    ),
    RuleContractCase(
      ruleId = "UnnecessaryEventHandlerParameter",
      code =
      """
        data class Data(val id: Int)

        @Composable
        fun EventComponent(data: Data, onClick: (Int) -> Unit) {
          Button(onClick = { onClick(data.id) }) {}
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("Unnecessary event callback arguments"),
    ),
    RuleContractCase(
      ruleId = "UnnecessaryLayoutWrapper",
      code =
      """
        @Composable
        fun Wrapped() {
          Box {
            Row(modifier = Modifier.fillMaxSize()) {}
          }
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("wraps a single \"Row\""),
    ),
  )

  val heavyParityCases: List<HeavyRuleParityCase> = listOf(
    HeavyRuleParityCase(
      ruleId = "ConditionCouldBeLifted",
      code =
      """
        @Composable
        fun Conditional() {
          val printValue = false
          Column {
            if (printValue) {
              Text(text = "3")
            }
          }
        }
      """.trimIndent(),
      expectedMessages = listOf("Condition could be lifted out of \"Column\""),
    ),
    HeavyRuleParityCase(
      ruleId = "ReusedModifierInstance",
      code =
      """
        @Composable
        fun Reuse(modifier: Modifier = Modifier) {
          Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = modifier.fillMaxSize()) {}
          }
        }
      """.trimIndent(),
      expectedMessages = listOf(
        "Composable uses \"modifier\" on the wrong level, non-direct children should use \"Modifier\"",
      ),
    ),
    HeavyRuleParityCase(
      ruleId = "UnnecessaryEventHandlerParameter",
      code =
      """
        data class Data(val id: Int)

        @Composable
        fun EventComponent(data: Data, onClick: (Int) -> Unit) {
          Button(onClick = { onClick(data.id) }) {}
        }
      """.trimIndent(),
      expectedMessages = listOf(
        "Unnecessary event callback arguments. Move all \"data\" access to the parent composable " +
          "event handler and switch \"onClick\" type to \"() -> Unit\"",
      ),
    ),
  )
}
