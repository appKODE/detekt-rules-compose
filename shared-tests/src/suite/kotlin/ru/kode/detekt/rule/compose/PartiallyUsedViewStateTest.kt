package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.test.TestConfig
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import ru.kode.detekt.rule.compose.snippet.composeSnippet

class PartiallyUsedViewStateTest : ShouldSpec({
  val environment = createEnvironment()

  fun lint(code: String, config: Config = Config.empty) =
    PartiallyUsedViewState(config).lintWithContext(environment, code)

  should("report a state with fewer read properties than the threshold") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      @Composable
      fun Test(state: ViewState, enabled: Boolean) {
        Column {
          Text(text = state.title)
          if (enabled) Text(text = state.subtitle + state.title)
        }
      }
      """,
    )

    val findings = lint(code)

    findings.map { it.message } shouldBe listOf(
      "Parameter \"state\" reads only 2 of 6 \"ViewState\" properties (title, subtitle). " +
        "Pass them instead of the whole state",
    )
    findings.single().shouldStartAt(code, "state: ViewState, enabled")
  }

  should("report a nullable state and a state of an expression body function") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      @Composable
      fun Test(state: ViewState?) = Text(text = state?.title.orEmpty())
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "Parameter \"state\" reads only 1 of 6 \"ViewState\" properties (title). Pass them instead of the whole state",
    )
  }

  should("report a state which is compared with null, asserted to be not null or parenthesized") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      @Composable
      fun Checked(state: ViewState?) {
        if (state == null) return
        if (null !== state) Text(text = state.title)
      }

      @Composable
      fun Asserted(state: ViewState?) {
        Text(text = state!!.title + (state).subtitle + (state!!)!!.title)
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "Parameter \"state\" reads only 1 of 6 \"ViewState\" properties (title). Pass them instead of the whole state",
      "Parameter \"state\" reads only 2 of 6 \"ViewState\" properties (title, subtitle). " +
        "Pass them instead of the whole state",
    )
  }

  should("not report a state which is only compared with null or compared with another value") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      @Composable
      fun Checked(state: ViewState?) {
        if (state != null) Text(text = "loaded")
      }

      @Composable
      fun Compared(state: ViewState, other: ViewState) {
        if (state == other) Text(text = state.title)
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report a function whose signature is not its own") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      interface Screen {
        @Composable fun Content(state: ViewState)
        @Composable fun Header(state: ViewState) { Text(text = state.title) }
      }
      abstract class Base {
        @Composable open fun Body(state: ViewState) { Text(text = state.title) }
      }
      class Impl : Screen {
        @Composable override fun Content(state: ViewState) { Text(text = state.title) }
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("fail on an invalid pattern with an error which names the option") {
    val code = composeSnippet(STATE + "@Composable fun Test(state: ViewState) { Text(text = state.title) }")

    val error = shouldThrowAny { lint(code, TestConfig("stateClassPattern" to "(")) }

    generateSequence(error) { it.cause }.map { it.message.orEmpty() }.toList() shouldContain
      "PartiallyUsedViewState: stateClassPattern \"(\" is not a valid regex"
  }

  should("report a state read through a scope function") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      @Composable
      fun With(state: ViewState, title: String) {
        with(state) {
          val subtitle = 1
          // the local declarations win over the properties of the receiver
          Text(text = title + subtitle.toString() + count)
          onClick()
        }
      }

      @Composable
      fun Run(state: ViewState?) {
        state?.run { Text(text = title) }
        state!!.run { Text(text = id) }
      }

      @Composable
      fun Apply(state: ViewState) {
        state.apply { Text(text = title) }
        Text(text = state.subtitle)
      }

      @Composable
      fun Branch(state: ViewState, enabled: Boolean) {
        // the value of a branch of a statement is not used
        if (enabled) { state.apply { Text(text = title) } } else Text(text = state.subtitle)
        when { enabled -> { state.apply { Text(text = title) } } }
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "Parameter \"state\" reads only 2 of 6 \"ViewState\" properties (count, onClick). " +
        "Pass them instead of the whole state",
      "Parameter \"state\" reads only 2 of 6 \"ViewState\" properties (title, id). " +
        "Pass them instead of the whole state",
      "Parameter \"state\" reads only 2 of 6 \"ViewState\" properties (title, subtitle). " +
        "Pass them instead of the whole state",
      "Parameter \"state\" reads only 2 of 6 \"ViewState\" properties (title, subtitle). " +
        "Pass them instead of the whole state",
    )
  }

  should("count a property read inside a nested scope function for the state of that function") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      fun log(value: Any) {}

      @Composable
      fun Test(state: ViewState, other: ViewState) {
        with(state) {
          with(other) { Text(text = title + subtitle) }
          Text(text = count.toString())
        }
        log(other)
      }

      @Composable
      fun Unread(state: ViewState, other: ViewState) {
        with(state) { with(other) { Text(text = title + subtitle) } }
        log(other)
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "Parameter \"state\" reads only 1 of 6 \"ViewState\" properties (count). Pass them instead of the whole state",
    )
  }

  should("report a destructured state and an inherited property") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      @Composable
      fun Destructured(state: ViewState) {
        val (title, _, count) = state
        Text(text = title + count)
      }

      @Composable
      fun Inherited(state: ViewState) {
        Text(text = state.title + state.id)
      }

      class PlainViewState(val title: String, val subtitle: String, val count: Int) {
        operator fun component1() = title
      }

      @Composable
      fun NotData(state: PlainViewState) {
        val (title) = state
        Text(text = title)
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "Parameter \"state\" reads only 2 of 6 \"ViewState\" properties (title, count). " +
        "Pass them instead of the whole state",
      "Parameter \"state\" reads only 2 of 6 \"ViewState\" properties (title, id). " +
        "Pass them instead of the whole state",
    )
  }

  should("not report a state with enough read properties") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      @Composable
      fun Test(state: ViewState) {
        Text(text = state.title + state.subtitle + state.count)
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report a state which is used as a whole") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      fun log(value: Any) {}

      @Composable
      fun Passed(state: ViewState) {
        Text(text = state.title)
        log(state)
      }

      @Composable
      fun Copied(state: ViewState, onChange: (ViewState) -> Unit) {
        Button(onClick = { onChange(state.copy(count = 1)) }) { Text(text = state.title) }
      }

      @Composable
      fun Called(state: ViewState) {
        with(state) { Text(text = title + describe()) }
      }

      @Composable
      fun Extended(state: ViewState) {
        state.run { Text(text = title + summary()) }
      }

      @Composable
      fun Itself(state: ViewState) {
        with(state) {
          Text(text = title)
          log(this)
        }
      }

      @Composable
      fun Applied(state: ViewState) {
        log(state.apply { Text(text = title) })
      }

      @Composable
      fun Returned(state: ViewState, items: List<Int>) {
        items.map { state.apply { log(title) } }
      }

      @Composable
      fun Decorated(state: ViewState) {
        // a member extension needs the state as its dispatch receiver
        with(state) { Text(text = "x".decorate() + title) }
      }

      @Composable
      fun IfBranch(state: ViewState, enabled: Boolean) {
        val applied = if (enabled) { state.apply { log(title) } } else 1
        log(applied)
      }

      @Composable
      fun WhenBranch(state: ViewState, enabled: Boolean) {
        log(when { enabled -> { state.apply { log(title) } } else -> 1 })
      }

      @Composable
      fun TryBranch(state: ViewState) {
        log(try { state.apply { log(title) } } catch (e: Exception) { 1 })
      }

      @Composable
      fun Let(state: ViewState) {
        state.let { Text(text = it.title) }
      }

      @Composable
      fun Enough(state: ViewState) {
        val (title, subtitle) = state
        with(state) { Text(text = title + subtitle + count) }
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report an unused state") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      @Composable
      fun Test(state: ViewState) {}

      @Composable
      fun Shadowed(state: ViewState, items: List<Holder>) {
        items.forEach { state -> Text(text = state.title) }
        Text(text = items.first().state.title)
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report a class which declares fewer properties than the threshold") {
    // language=kotlin
    val code = composeSnippet(
      """
      data class ViewState(val title: String, val subtitle: String)

      @Composable
      fun Test(state: ViewState) {
        Text(text = state.title)
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not count the private and the extension properties of a class") {
    // language=kotlin
    val code = composeSnippet(
      """
      data class ViewState(val title: String, val subtitle: String, private val count: Int) {
        val String.extended: String get() = this
      }
      data class WideViewState(val title: String, val subtitle: String, internal val count: Int, private val id: Int)

      @Composable
      fun Test(state: ViewState, wide: WideViewState) {
        Text(text = state.title + wide.title)
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "Parameter \"wide\" reads only 1 of 3 \"WideViewState\" properties (title). Pass them instead of the whole state",
    )
  }

  should("not report a class which does not match the pattern, an unresolved one and a regular function") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      data class Model(val title: String, val subtitle: String, val count: Int)

      @Composable
      fun Test(model: Model, missing: MissingViewState) {
        Text(text = model.title + missing.title)
      }

      fun test(state: ViewState) = state.title
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("use the configured pattern and threshold") {
    // language=kotlin
    val code = composeSnippet(
      STATE + """
      data class Model(val title: String, val subtitle: String, val count: Int)

      @Composable
      fun Test(model: Model, state: ViewState) {
        Text(text = model.title + state.title + state.subtitle)
      }
      """,
    )

    lint(code, TestConfig("stateClassPattern" to "Mod.l")).map { it.message } shouldBe listOf(
      "Parameter \"model\" reads only 1 of 3 \"Model\" properties (title). Pass them instead of the whole state",
    )
    lint(code, TestConfig("usedFieldsThreshold" to 2)).shouldBeEmpty()
    lint(code, TestConfig("usedFieldsThreshold" to 4)).size shouldBe 1
    lint(code, TestConfig("usedFieldsThreshold" to 7)).shouldBeEmpty()
  }
})

private const val STATE = """
      abstract class Identified { val id: String = "" }
      data class ViewState(
        val title: String,
        val subtitle: String,
        val count: Int,
        val isLoading: Boolean,
        val onClick: () -> Unit = {},
      ) : Identified() {
        fun describe() = title
        fun String.decorate() = this + title
      }
      fun ViewState.summary() = title
      class Holder(val state: ViewState)
"""
