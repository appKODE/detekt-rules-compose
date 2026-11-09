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
  /** Findings when the code is linted without the fake Compose declarations, so their symbols are unresolved. */
  val expectedUnresolvedFindings: Int = 0,
  /** Findings of detekt1 without type resolution, when nothing resolves at all. */
  val expectedNoTypeResolutionFindings: Int = expectedUnresolvedFindings,
  val name: String = ruleId,
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
    "StateReadCouldBeLowered",
    "NonImmutableDataClassParameter",
    "PartiallyUsedViewState",
    "UnwrappedValueClassImageModel",
    "UnnecessaryModifierArgument",
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
    RuleContractCase(
      ruleId = "StateReadCouldBeLowered",
      code =
      """
        class State<T>(val value: T)

        @Composable
        fun Lowered(state: State<Boolean>) {
          val visible = state.value
          Box {
            Column {
              if (visible) Text(text = "shown")
            }
          }
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("is only used inside the content of \"Column\""),
    ),
    RuleContractCase(
      ruleId = "NonImmutableDataClassParameter",
      code =
      """
        data class Profile(val name: String)

        @Composable
        fun ProfileCard(profile: Profile) {
          Text(text = profile.name)
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("is a data class \"Profile\""),
    ),
    RuleContractCase(
      ruleId = "PartiallyUsedViewState",
      code =
      """
        data class HeaderViewState(val title: String, val subtitle: String, val count: Int)

        @Composable
        fun Header(state: HeaderViewState) {
          Text(text = state.title)
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("reads only 1 of 3 \"HeaderViewState\" properties"),
    ),
    RuleContractCase(
      ruleId = "UnwrappedValueClassImageModel",
      code =
      """
        @JvmInline
        value class ImageUrl(val value: String)

        fun AsyncImage(model: Any?) {}

        @Composable
        fun Avatar(avatarUrl: ImageUrl) {
          AsyncImage(model = avatarUrl)
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("\"avatarUrl\" is a value class"),
    ),
    RuleContractCase(
      ruleId = "UnnecessaryModifierArgument",
      code =
      """
        @Composable
        fun Greeting() {
          Text("Hello", Modifier)
        }
      """.trimIndent(),
      expectedCount = 1,
      expectedMessageContains = listOf("is the default value of the \"modifier\" parameter"),
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
      // resolution is only used to tell the handler from its namesakes and to skip sealed receivers
      expectedUnresolvedFindings = 1,
    ),
    // https://github.com/appKODE/detekt-rules-compose/issues/46
    HeavyRuleParityCase(
      ruleId = "UnnecessaryEventHandlerParameter",
      name = "UnnecessaryEventHandlerParameter, function named like its event handler",
      code =
      """
        @Composable
        fun Modifier.onShown(percent: Float, onShown: () -> Unit): Modifier = onShown(0, percent, onShown)

        @Composable
        fun Modifier.onShown(extraKeys: Int, percent: Float, onShown: () -> Unit): Modifier = this
      """.trimIndent(),
      expectedMessages = emptyList(),
    ),
    HeavyRuleParityCase(
      ruleId = "StateReadCouldBeLowered",
      code =
      """
        class State<T>(val value: T)

        @Composable
        fun Lowered(state: State<Boolean>) {
          val visible = state.value
          Box {
            Column {
              if (visible) Text(text = "shown")
            }
          }
        }
      """.trimIndent(),
      expectedMessages = listOf(
        "\"visible\" is only used inside the content of \"Column\". Read the state there instead",
      ),
    ),
    HeavyRuleParityCase(
      ruleId = "NonImmutableDataClassParameter",
      code =
      """
        data class Profile(val name: String)

        @Composable
        fun ProfileCard(profile: Profile, missing: Missing) {
          Text(text = profile.name)
        }
      """.trimIndent(),
      expectedMessages = listOf(
        "Parameter \"profile\" is a data class \"Profile\" which is not marked with @Immutable or @Stable",
      ),
      // the data class resolves without the fake Compose declarations too, "Missing" never does
      expectedUnresolvedFindings = 1,
      expectedNoTypeResolutionFindings = 0,
    ),
    HeavyRuleParityCase(
      ruleId = "PartiallyUsedViewState",
      code =
      """
        data class HeaderViewState(val title: String, val subtitle: String, val count: Int)

        @Composable
        fun Header(state: HeaderViewState) {
          Text(text = state.title)
        }

        @Composable
        fun Footer(state: MissingViewState) {
          Text(text = state.title)
        }
      """.trimIndent(),
      expectedMessages = listOf(
        "Parameter \"state\" reads only 1 of 3 \"HeaderViewState\" properties (title). Pass them instead of the whole state",
      ),
      // the state class resolves without the fake Compose declarations too, "MissingViewState" never does
      expectedUnresolvedFindings = 1,
      expectedNoTypeResolutionFindings = 0,
    ),
    HeavyRuleParityCase(
      ruleId = "UnwrappedValueClassImageModel",
      code =
      """
        @JvmInline
        value class ImageUrl(val value: String)

        fun AsyncImage(model: Any?) {}

        @Composable
        fun Avatar(avatarUrl: ImageUrl, missing: Missing) {
          AsyncImage(model = avatarUrl)
          AsyncImage(model = missing)
        }
      """.trimIndent(),
      expectedMessages = listOf(
        "\"avatarUrl\" is a value class, Coil can't load an image from it. Pass its underlying value instead",
      ),
      // the value class resolves without the fake Compose declarations too, "Missing" never does
      expectedUnresolvedFindings = 1,
      expectedNoTypeResolutionFindings = 0,
    ),
    HeavyRuleParityCase(
      ruleId = "UnnecessaryModifierArgument",
      code =
      """
        @Composable
        fun Greeting() {
          Text("Hello", Modifier)
          Text("Hello", modifier = Modifier.fillMaxSize())
          Missing("Hello", Modifier)
        }
      """.trimIndent(),
      expectedMessages = listOf(
        "\"Modifier\" is the default value of the \"modifier\" parameter of \"Text\". Remove the argument",
      ),
    ),
  )
}
