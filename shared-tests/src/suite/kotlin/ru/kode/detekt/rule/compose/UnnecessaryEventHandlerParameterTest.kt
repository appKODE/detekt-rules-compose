/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import dev.detekt.test.TestConfig
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.kode.detekt.rule.compose.snippet.composeSnippet

class UnnecessaryEventHandlerParameterTest : ShouldSpec({
  val environment = createEnvironment()

  should("report when whole class is passed") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onClick: (Data) -> Unit) {
          Button(onClick = { onClick(data) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("report when handler argument is class property of a parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          Button(onClick = { onButtonClick(data.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("report when handler argument is nested class property of a parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Nested(val id: Int)
        data class Data(val id: Int, val nested: Nested)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          Button(onClick = { onButtonClick(data.nested.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("not report when handler argument is a class property not from parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          val localData = Data(3, "hello")
          Button(onClick = { onButtonClick(localData.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("not report when handler argument name matches partially") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit, onButton1Click: (Data) -> Unit) {
          val dataUsage = Data(3, "hello")
          Button(onClick = { onButtonClick(dataUsage.id) }) { }
          Button(onClick = { onButton1Click(dataUsage) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("not report when handler argument name is passed to a non event handler call") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        fun processData(d: Int) = Unit

        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          Button(onClick = { processData(data.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("not report when accessing non-properties of handler argument") {
    // language=kotlin
    val code = composeSnippet(
      """
          data class Data(val id: Int, val title: String) {
            fun process(): Int = 0
          }

          @Composable
          fun Test(data: Data, onButtonClick: (Int) -> Unit) {
            Button(onClick = { onButtonClick(data.process()) }) { }
          }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("report an error message with proper event callback type") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int) -> Unit) {
          Button(onClick = { onButtonClick(data.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single().message shouldContain "switch \"onButtonClick\" type to \"() -> Unit\""
  }

  should("report an error message with proper event callback type when multiple arguments for first") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int, String) -> Unit) {
          Button(onClick = { onButtonClick(data.id, "hello") }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single { "\"data\" access" in it.message }.message shouldContain
      "switch \"onButtonClick\" type to \"(String) -> Unit\""
  }

  should("report an error message with proper event callback type when multiple arguments for second") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, onButtonClick: (Int, String, Int) -> Unit) {
          Button(onClick = { onButtonClick(0, data.title, 0) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single { "\"data\" access" in it.message }.message shouldContain
      "switch \"onButtonClick\" type to \"(Int, Int) -> Unit\""
  }

  should("honour @Suppress on the event handler parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
          data class State(val id: String)
          @Composable
          fun MyButton(
            state: State,
            @Suppress("UnnecessaryEventHandlerParameter") onClick: (String) -> Unit,
          ) {
            Button(onClick = { onClick(state.id) }) { Text("Click here") }
          }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("not report if parameter is a sealed class") {
    // language=kotlin
    val code = composeSnippet(
      """
          sealed class State {
              object Loading : State()
              data class Data(val id: String) : State()
          }

          @Composable
          fun MyButton(
              state: State,
              onClick: (String) -> Unit,
          ) {
              when (state) {
                  State.Loading -> Text("Loading")
                  is State.Data -> Button(onClick = { onClick(state.id) }) {}
              }
          }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("report inside if/else-if branches and nested lambdas") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        @Composable
        fun Test(data: Data, first: Boolean, onClick: (Int) -> Unit) {
          if (first) {
            Text("first")
          } else if (data.id > 0) {
            Row {
              Button(onClick = { onClick(data.id) }) {}
            }
          }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("report nullable event handler and keep it nullable in the suggested type") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        @Composable
        fun Test(data: Data, onButtonClick: ((Int) -> Unit)? = null) {
          Button(onClick = { if (onButtonClick != null) onButtonClick(data.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.first().message shouldContain "switch \"onButtonClick\" type to \"(() -> Unit)?\""
  }

  should("ignore non-composable functions") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        fun Test(data: Data, onClick: (Int) -> Unit) {
          Button(onClick = { onClick(data.id) }) {}
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  // https://github.com/appKODE/detekt-rules-compose/issues/41
  should("not report a lambda parameter shadowing a state parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
        @Composable
        fun Asdf(rotation: Float, onClick: (Float) -> Unit) {
          Column {
            Asdf(rotation, { rotation -> onClick(rotation) })
          }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("report a state parameter passed from a lambda with another parameter name") {
    // language=kotlin
    val code = composeSnippet(
      """
        @Composable
        fun Asdf(rotation: Float, onClick: (Float) -> Unit) {
          Column {
            Asdf(rotation, { foo -> onClick(rotation) })
          }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.single().message shouldBe "Unnecessary event callback arguments. Move all \"rotation\" access " +
      "to the parent composable event handler and switch \"onClick\" type to \"() -> Unit\""
    findings.single().shouldStartAt(code, "onClick: (Float) -> Unit")
  }

  should("not report a local val or a catch parameter shadowing a state parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
        @Composable
        fun Test(id: Int, error: String, values: List<Int>, onClick: (Int) -> Unit, onError: (String?) -> Unit) {
          Button(onClick = {
            try {
              values.first()
            } catch (error: Exception) {
              onError(error.message)
            }
          }) { }
          Button(onClick = {
            val id = values.first()
            onClick(id)
          }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  // https://github.com/appKODE/detekt-rules-compose/issues/3
  context("report a constant passed to an event handler") {
    withData(
      "5",
      "-1",
      "-LIMIT",
      "true",
      "null",
      "\"text\"",
      "LIMIT",
      "Holder.MAX",
      "Kind.A",
      "Back",
      "Intent.Close",
      "Intent.Refresh",
    ) { constant ->
      // language=kotlin
      val code = composeSnippet(
        """
          const val LIMIT = 1
          class Holder { companion object { const val MAX = 3 } }
          enum class Kind { A }
          object Back
          sealed class Intent {
            data object Close : Intent()
            object Refresh : Intent()
          }

          @Composable
          fun Test(onSomething: (Any?) -> Unit) {
            Button(onClick = { onSomething($constant) }) { }
          }
        """.trimIndent(),
      )

      val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

      findings shouldHaveSize 1
      findings.single().message shouldBe "Unnecessary event callback arguments. Move constant \"$constant\" " +
        "to the parent composable event handler and switch \"onSomething\" type to \"() -> Unit\""
      findings.single().shouldStartAt(code, "onSomething: (Any?) -> Unit")
    }
  }

  context("not report a non-constant passed to an event handler") {
    withData(
      "notConst",
      "local",
      "compute()",
      "Plain(1)",
      "\"id \$local\"",
      "Kind.A.id",
    ) { argument ->
      // language=kotlin
      val code = composeSnippet(
        """
          val notConst = 1
          fun compute(): Int = 1
          data class Plain(val id: Int) { companion object }
          enum class Kind { A; val id: Int get() = 0 }

          @Composable
          fun Test(onSomething: (Any?) -> Unit) {
            val local = compute()
            Button(onClick = { onSomething($argument) }) { }
          }
        """.trimIndent(),
      )

      val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

      findings.shouldBeEmpty()
    }
  }

  should("report the sealed intent of the issue with the argument dropped from the suggested type") {
    // language=kotlin
    val code = composeSnippet(
      """
        sealed class SealedClassIntent {
          data object Back : SealedClassIntent()
        }

        @Composable
        private fun Foo(title: String, onUserIntent: (SealedClassIntent) -> Unit) {
          Button(onClick = { onUserIntent(SealedClassIntent.Back) }) { Text(title) }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.single().message shouldBe "Unnecessary event callback arguments. Move constant " +
      "\"SealedClassIntent.Back\" to the parent composable event handler and switch \"onUserIntent\" type to " +
      "\"() -> Unit\""
    findings.single().shouldStartAt(code, "onUserIntent: (SealedClassIntent) -> Unit")
  }

  should("report a constant passed by every call once") {
    // language=kotlin
    val code = composeSnippet(
      """
        sealed class Intent {
          data object Back : Intent()
        }

        @Composable
        fun Test(onUserIntent: (Intent) -> Unit) {
          Button(onClick = { onUserIntent(Intent.Back) }) { }
          Button(onClick = { onUserIntent(Intent.Back) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single().message shouldBe "Unnecessary event callback arguments. Move constant \"Intent.Back\" to the " +
      "parent composable event handler and switch \"onUserIntent\" type to \"() -> Unit\""
    findings.single().shouldStartAt(code, "onUserIntent: (Intent) -> Unit")
  }

  should("report all constant arguments of an event handler in one finding") {
    // language=kotlin
    val code = composeSnippet(
      """
        @Composable
        fun Test(onZ: (Int, String) -> Unit) {
          Button(onClick = { onZ(1, "x") }) { }
          Button(onClick = { onZ(1, "x") }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single().message shouldBe "Unnecessary event callback arguments. Move constants \"1\", \"\"x\"\" to the " +
      "parent composable event handler and switch \"onZ\" type to \"() -> Unit\""
    findings.single().shouldStartAt(code, "onZ: (Int, String) -> Unit")
  }

  should("report a constant passed through invoke and keep a nullable handler nullable") {
    // language=kotlin
    val code = composeSnippet(
      """
        @Composable
        fun Test(onA: ((Int) -> Unit)?, onB: (Int) -> Unit) {
          Button(onClick = { onA?.invoke(1) }) { }
          Button(onClick = { onB.invoke(2) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.map { it.message } shouldBe listOf(
      "Unnecessary event callback arguments. Move constant \"1\" to the parent composable event handler and switch " +
        "\"onA\" type to \"(() -> Unit)?\"",
      "Unnecessary event callback arguments. Move constant \"2\" to the parent composable event handler and switch " +
        "\"onB\" type to \"() -> Unit\"",
    )
    findings[0].shouldStartAt(code, "onA: ((Int) -> Unit)?")
    findings[1].shouldStartAt(code, "onB: (Int) -> Unit")
  }

  context("not report a constant when the event handler is also used otherwise or gets different constants") {
    withData(
      nameFn = { it.first },
      "passed on as a value" to """
        @Composable fun TextField(value: String, onValueChange: (String) -> Unit, trailingIcon: @Composable () -> Unit) {}

        @Composable
        fun Search(value: String, onValueChange: (String) -> Unit) {
          TextField(value, onValueChange = onValueChange, trailingIcon = { Button(onClick = { onValueChange("") }) { } })
        }
      """,
      "passed on as a value to a trailing content" to """
        @Composable fun MenuBox(expanded: Boolean, onExpandedChange: (Boolean) -> Unit, content: @Composable () -> Unit) {}
        @Composable fun Menu(onDismissRequest: () -> Unit) {}

        @Composable
        fun Dropdown(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
          MenuBox(expanded, onExpandedChange = onExpandedChange) { Menu(onDismissRequest = { onExpandedChange(false) }) }
        }
      """,
      "called with different constants" to """
        @Composable
        fun Toggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
          Button(onClick = { onCheckedChange(true) }) { }
          Button(onClick = { onCheckedChange(false) }) { }
        }
      """,
      "invoked and passed on as a value" to """
        @Composable fun Child(onX: ((Int) -> Unit)?) {}

        @Composable
        fun Parent(onX: ((Int) -> Unit)?) {
          Child(onX = onX)
          Button(onClick = { onX?.invoke(1) }) { }
        }
      """,
      "also called with a non-constant" to """
        @Composable
        fun Tabs(count: Int, onSelect: (Int) -> Unit) {
          Button(onClick = { onSelect(0) }) { }
          (1 until count).forEach { index -> Button(onClick = { onSelect(index) }) { } }
        }
      """,
      "also called with a lambda parameter" to """
        @Composable
        fun Items(items: List<String>, onClick: (String?) -> Unit) {
          Button(onClick = { onClick(null) }) { }
          items.forEach { item -> Button(onClick = { onClick(item) }) { } }
        }
      """,
    ) { (_, snippet) ->
      val code = composeSnippet(snippet.trimIndent())

      val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

      findings.shouldBeEmpty()
    }
  }

  should("report a parenthesized constant") {
    // language=kotlin
    val code = composeSnippet(
      """
        @Composable
        fun Test(onSomething: (Int) -> Unit) {
          Button(onClick = { onSomething((1)) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single().message shouldBe "Unnecessary event callback arguments. Move constant \"1\" " +
      "to the parent composable event handler and switch \"onSomething\" type to \"() -> Unit\""
    findings.single().shouldStartAt(code, "onSomething: (Int) -> Unit")
  }

  should("not report constants with reportConstantArguments disabled, state parameters still") {
    // language=kotlin
    val code = composeSnippet(
      """
        @Composable
        fun Test(item: String, onSomething: (Int) -> Unit, onItemClick: (String) -> Unit) {
          Button(onClick = { onSomething(1) }) { }
          Button(onClick = { onItemClick(item) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter(TestConfig("reportConstantArguments" to false))
      .lintWithContext(environment, code)

    findings.single().message shouldBe "Unnecessary event callback arguments. Move all \"item\" access " +
      "to the parent composable event handler and switch \"onItemClick\" type to \"() -> Unit\""
    UnnecessaryEventHandlerParameter(TestConfig("reportConstantArguments" to true))
      .lintWithContext(environment, code) shouldHaveSize 2
  }

  // https://github.com/appKODE/detekt-rules-compose/issues/46
  context("not report a call of something else named like the event handler") {
    withData(
      nameFn = { it.first },
      "overload of the function taking the handler" to """
        @Composable
        fun Modifier.onShown(percent: Float, onShown: () -> Unit): Modifier = onShown(0, percent, onShown)

        @Composable
        fun Modifier.onShown(extraKeys: Int, percent: Float, onShown: () -> Unit): Modifier = this
      """,
      "overload of the function not taking the handler" to """
        @Composable
        fun Modifier.onShown(percent: Float, onShown: () -> Unit): Modifier = onShown(0, percent)

        @Composable
        fun Modifier.onShown(extraKeys: Int, percent: Float): Modifier = this
      """,
      "overload called on an explicit receiver" to """
        @Composable
        fun Modifier.onShown(percent: Float, onShown: () -> Unit): Modifier = this.onShown(0, percent, onShown)

        @Composable
        fun Modifier.onShown(extraKeys: Int, percent: Float, onShown: () -> Unit): Modifier = this
      """,
      "member of a state parameter" to """
        class Data(val id: Int) { fun onClick(id: Int) {} }

        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          Button(onClick = { data.onClick(data.id) }) { }
        }
      """,
      "function chosen by the argument type" to """
        fun onSelect(key: String) {}

        @Composable
        fun Test(key: String, onSelect: (Int) -> Unit) {
          Button(onClick = { onSelect(key) }) { }
        }
      """,
      "function called with named arguments" to """
        data class Data(val id: Int)
        fun onClick(id: Int, label: String) {}

        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          Button(onClick = { onClick(id = data.id, label = "x") }) { }
        }
      """,
      "lambda parameter" to """
        data class Data(val id: Int)
        @Composable fun Wrapper(content: @Composable ((Int) -> Unit) -> Unit) {}

        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          Wrapper { onClick -> Button(onClick = { onClick(data.id) }) { } }
        }
      """,
      "local function" to """
        data class Data(val id: Int)

        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          fun onClick(id: Int) {}
          Button(onClick = { onClick(data.id) }) { }
        }
      """,
      "parameter of a nested composable" to """
        data class Data(val id: Int)

        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          @Composable
          fun Inner(onClick: (Int) -> Unit) {
            Button(onClick = { onClick(data.id) }) { }
          }
        }
      """,
      "delegated local property derived from the handler" to """
        data class Data(val id: Int)
        class State<T>(val value: T)
        operator fun <T> State<T>.getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = value
        fun <T> rememberUpdatedState(value: T): State<T> = State(value)

        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          val onClick by rememberUpdatedState(onClick)
          Button(onClick = { onClick(data.id) }) { }
        }
      """,
    ) { (_, snippet) ->
      val code = composeSnippet(snippet.trimIndent())

      val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

      findings.shouldBeEmpty()
    }
  }

  should("report an event handler called with a trailing lambda") {
    // language=kotlin
    val code = composeSnippet(
      """
        @Composable
        fun Test(item: String, onDone: (String, () -> Unit) -> Unit) {
          Button(onClick = { onDone(item) { } }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single().message shouldBe "Unnecessary event callback arguments. Move all \"item\" access " +
      "to the parent composable event handler and switch \"onDone\" type to \"(() -> Unit) -> Unit\""
  }

  // https://github.com/appKODE/detekt-rules-compose/issues/49
  context("not report a state parameter which is not passed by every call or when the handler is used otherwise") {
    withData(
      nameFn = { it.first },
      "also called with lambda parameters" to """
        @Composable fun Foo(onVisibleItemToTrack: (Boolean, Int) -> Unit) {}
        @Composable fun Bar(onShown: () -> Unit) {}

        @Composable
        fun Test(bool: Boolean, onShown: (Boolean, Int) -> Unit) {
          when (bool) {
            true -> Foo(onVisibleItemToTrack = { id: Boolean, position: Int -> onShown(id, position) })
            false -> Bar(onShown = { onShown(bool, 0) })
          }
        }
      """,
      "called with different state parameters" to """
        data class Data(val id: Int)

        @Composable
        fun Test(a: Data, b: Data, onClick: (Int) -> Unit) {
          Button(onClick = { onClick(a.id) }) { }
          Button(onClick = { onClick(b.id) }) { }
        }
      """,
      "called with different properties of a state parameter" to """
        data class Data(val id: String, val title: String)

        @Composable
        fun Test(data: Data, onClick: (String) -> Unit) {
          Button(onClick = { onClick(data.id) }) { }
          Button(onClick = { onClick(data.title) }) { }
        }
      """,
      "also called with a constant" to """
        @Composable
        fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
          Button(onClick = { onSelect(0) }) { }
          Button(onClick = { onSelect(selected) }) { }
        }
      """,
      "also passed on as a value" to """
        data class Data(val id: Int)
        @Composable fun Child(onClick: (Int) -> Unit) {}

        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          Child(onClick = onClick)
          Button(onClick = { onClick(data.id) }) { }
        }
      """,
      "passed negated" to """
        @Composable
        fun Toggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
          Button(onClick = { onCheckedChange(!checked) }) { }
        }
      """,
    ) { (_, snippet) ->
      val code = composeSnippet(snippet.trimIndent())

      UnnecessaryEventHandlerParameter().lintWithContext(environment, code).shouldBeEmpty()
      UnnecessaryEventHandlerParameter(TestConfig("reportConstantArguments" to false))
        .lintWithContext(environment, code)
        .shouldBeEmpty()
    }
  }

  should("report a state parameter passed by every call once") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          Button(onClick = { onClick(data.id) }) { }
          Button(onClick = { onClick(data.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single().message shouldBe "Unnecessary event callback arguments. Move all \"data\" access " +
      "to the parent composable event handler and switch \"onClick\" type to \"() -> Unit\""
  }

  should("report all state arguments of an event handler in one finding") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int, val title: String)
        @Composable
        fun Test(data: Data, count: Int, onClick: (Int, String, Int) -> Unit) {
          Button(onClick = { onClick(data.id, data.title, count) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single().message shouldBe "Unnecessary event callback arguments. Move all \"data\", \"count\" access " +
      "to the parent composable event handler and switch \"onClick\" type to \"() -> Unit\""
  }

  should("report a state parameter passed through invoke and keep a nullable handler nullable") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        @Composable
        fun Test(data: Data, onA: ((Int) -> Unit)?, onB: (Int) -> Unit) {
          Button(onClick = { onA?.invoke(data.id) }) { }
          Button(onClick = { onB.invoke(data.id) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.map { it.message } shouldBe listOf(
      "Unnecessary event callback arguments. Move all \"data\" access to the parent composable event handler and " +
        "switch \"onA\" type to \"(() -> Unit)?\"",
      "Unnecessary event callback arguments. Move all \"data\" access to the parent composable event handler and " +
        "switch \"onB\" type to \"() -> Unit\"",
    )
  }

  should("report a parenthesized state parameter access") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          Button(onClick = { onClick((data.id)) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("report a constant passed to a handler which is checked for null") {
    // language=kotlin
    val code = composeSnippet(
      """
        @Composable
        fun Test(onA: ((Int) -> Unit)? = null) {
          Button(onClick = { if (onA != null) onA(1) }) { }
        }
      """.trimIndent(),
    )

    val findings = UnnecessaryEventHandlerParameter().lintWithContext(environment, code)

    findings.single().message shouldBe "Unnecessary event callback arguments. Move constant \"1\" to the parent " +
      "composable event handler and switch \"onA\" type to \"(() -> Unit)?\""
  }

  should("not crash on calls with different argument counts") {
    // language=kotlin
    val code = composeSnippet(
      """
        data class Data(val id: Int)
        @Composable
        fun Test(data: Data, onClick: (Int) -> Unit) {
          Button(onClick = { onClick(data.id) }) { }
          Button(onClick = { onClick() }) { }
        }
      """.trimIndent(),
    )

    UnnecessaryEventHandlerParameter().lintWithContext(environment, code)
  }
})
