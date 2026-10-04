package ru.kode.detekt.rule.compose

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ComposableParametersOrderingTest : ShouldSpec() {
  init {
    context("optional/required order check") {
      should("report when optional go before required") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          enabled: Boolean = false,
          text: String,
          age: Int
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
      }

      should("report when optional and required are mixed") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          enabled: Boolean = false,
          text: String,
          age: Int = 8
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
      }

      should("not report if all required") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          text: String,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("not report if all optional") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String = "",
          text: String = "",
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("not report in presence of a required composable slot") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          subtitle: String? = null,
          content: @Composable () -> Unit,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        // See NOTE_ALLOWING_REQUIRED_TRAILING_SLOT_SPECIAL_CASE for details
        findings.shouldBeEmpty()
      }
    }

    context("modifier position") {
      should("report when incorrect modifier position with all optional parameters") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          verticalAlignment: Alignment = Alignment.CenterVertically,
          modifier: Modifier = Modifier.height(24.dp),
          enabled: Boolean = false,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.first().message shouldContain "before \"verticalAlignment\""
      }

      should("not report when modifier in first position with all optional parameters") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          modifier: Modifier = Modifier.height(24.dp),
          verticalAlignment: Alignment = Alignment.CenterVertically,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("not report when modifier is the single parameter") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          modifier: Modifier = Modifier.height(24.dp),
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("report incorrect modifier position with a mix of required and optional parameters") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          verticalAlignment: Alignment = Alignment.CenterVertically,
          modifier: Modifier = Modifier.height(24.dp),
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.first().message shouldContain "after \"text\""
      }

      should("not report when modifier is optional parameter") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          modifier: Modifier = Modifier.height(24.dp),
          verticalAlignment: Alignment = Alignment.CenterVertically,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("not report when modifier is last and no optional parameters") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          modifier: Modifier = Modifier.height(24.dp),
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("report incorrect modifier position when no optional parameters and modifier is not optional") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          modifier: Modifier,
          verticalAlignment: Alignment,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.first().message shouldContain "after \"verticalAlignment\""
      }

      should("not report incorrect modifier position when no optional parameters non-optional modifier is last") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          verticalAlignment: Alignment,
          modifier: Modifier,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("report when optional parameters and event handlers and modifier is not the first optional") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          onClick: () -> Unit,
          verticalAlignment: Alignment = Alignment.Center,
          modifier: Modifier = Modifier,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
      }

      should("report incorrect modifier position when last parameter is a required composable lambda") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          verticalAlignment: Alignment = Alignment.CenterVertically,
          modifier: Modifier = Modifier,
          content: @Composable () -> Unit,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.first().message shouldContain "after \"text\""
      }

      should("report incorrect modifier position when last parameter is a composable slot with a scoped receiver") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          modifier: Modifier,
          verticalAlignment: Alignment,
          content: @Composable ColumnScope.() -> Unit,
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.first().message shouldContain "after \"verticalAlignment\""
      }

      should("report incorrect modifier position when incorrect position with composable lambda with default value") {
        // language=kotlin
        val code = """
      @Composable
      fun Test(
          text: String,
          modifier: Modifier,
          verticalAlignment: Alignment,
          content: @Composable () -> Unit = {},
      ) {
          Text(text = props.title)
      }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.first().message shouldContain "after \"verticalAlignment\""
      }
    }

    context("with slots") {
      should("report incorrect order with trailing required composable slot") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          subtitle: String? = null,
          title: String,
          content: @Composable () -> Unit,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)
        findings shouldHaveSize 1
      }

      should("report incorrect order with multiple trailing required composable slot") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          subtitle: String? = null,
          title: String,
          content1: @Composable () -> Unit,
          content2: @Composable () -> Unit,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)
        findings shouldHaveSize 1
      }

      should("not report a required composable slot among required parameters") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          icon: @Composable () -> Unit,
          subtitle: String? = null,
          description: String? = null,
          content: @Composable () -> Unit,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("report a required composable slot after optional parameters which is not the last one") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          subtitle: String? = null,
          content1: @Composable () -> Unit,
          content2: @Composable (() -> Unit)? = null,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.single().message shouldBe
          "Required composable slot \"content1\" after optional parameters should be the last parameter"
        findings.single().shouldStartAt(code, "content1:")
      }

      should("not report trailing event handler") {
        // language=kotlin
        val code = """
        @Composable
        fun OnBackPressedHandler(
          enabled: Boolean = false,
          onBack: () -> Unit,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("not report required event handlers") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          onClick: () -> Unit,
          text: String
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("report if required slots are mixed with non-slot required parameters") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          content: @Composable () -> Unit,
          subtitle: String,
          contentOptional: @Composable () -> Unit,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.single().message shouldBe "Slot \"content\" should be the last parameter"
        findings.single().shouldStartAt(code, "content:")
      }

      should("report if optional slots are mixed with non-slot optional parameters") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          content: @Composable () -> Unit,
          contentOptional: @Composable (() -> Unit)? = null,
          subtitle: String? = null,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
      }

      should("report misplaced nullable composable slot") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          content: (@Composable () -> Unit)? = null,
          subtitle: String? = null,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
      }
    }

    context("trailing slots") {
      should("not report composable slots which are not forced to the end (#40)") {
        // language=kotlin
        val code = """
        @Composable
        fun Foo(
          state: FooState,
          toolbar: @Composable () -> Unit,
          modifier: Modifier = Modifier,
          bottomBar: @Composable () -> Unit = {},
          showFab: Boolean = false,
          content: @Composable () -> Unit,
        ) {
        }

        @Composable
        fun User(
          name: String,
          followers: Int,
          avatar: @Composable () -> Unit,
          modifier: Modifier = Modifier,
        ) {
        }

        @Composable
        fun AlertDialog(
          onDismissRequest: () -> Unit,
          buttons: @Composable () -> Unit,
          modifier: Modifier = Modifier,
          title: (@Composable () -> Unit)? = null,
          text: (@Composable () -> Unit)? = null,
          shape: Shape = MaterialTheme.shapes.medium,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("report a trailing slot name which is not the last parameter") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          content: @Composable () -> Unit,
          modifier: Modifier = Modifier,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.single().message shouldBe "Slot \"content\" should be the last parameter"
        findings.single().shouldStartAt(code, "content:")
      }

      should("report a non-composable trailing slot name which is not the last parameter") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          content: LazyListScope.() -> Unit,
          modifier: Modifier = Modifier,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.single().message shouldBe "Slot \"content\" should be the last parameter"
        findings.single().shouldStartAt(code, "content:")
      }

      should("report a nullable composable trailing slot name which is not the last parameter") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          content: (@Composable () -> Unit)?,
          modifier: Modifier = Modifier,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.single().message shouldBe "Slot \"content\" should be the last parameter"
        findings.single().shouldStartAt(code, "content:")
      }

      should("not report a non-slot parameter with a trailing slot name") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          content: String,
          modifier: Modifier = Modifier,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("use configured trailing slot names") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          mainContent: @Composable () -> Unit,
          modifier: Modifier = Modifier,
          content: @Composable () -> Unit = {},
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering(
          TestConfig("trailingSlotNames" to listOf("mainContent")),
        ).lint(code)

        findings shouldHaveSize 1
        findings.single().message shouldBe "Slot \"mainContent\" should be the last parameter"
        findings.single().shouldStartAt(code, "mainContent:")
      }

      should("report the first of several required composable slots after optional parameters") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          modifier: Modifier = Modifier,
          topBar: @Composable () -> Unit,
          body: @Composable () -> Unit,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.single().message shouldBe
          "Required composable slot \"topBar\" after optional parameters should be the last parameter"
        findings.single().shouldStartAt(code, "topBar:")
      }

      should("report a required composable slot after optional parameters which are all trailing lambdas") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          footer: (@Composable () -> Unit)? = null,
          header: @Composable () -> Unit,
          content: @Composable () -> Unit,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
        findings.single().message shouldBe
          "Required composable slot \"header\" after optional parameters should be the last parameter"
        findings.single().shouldStartAt(code, "header:")
      }

      should("not report several required composable slots when there are no optional parameters") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          title: String,
          topBar: @Composable () -> Unit,
          body: @Composable () -> Unit,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }
    }

    context("trailing event handlers") {
      val disallowed = TestConfig("allowTrailingEventHandlers" to false)

      should("not report a required trailing event handler by default (#50)") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          id: String,
          modifier: Modifier = Modifier,
          onClick: () -> Unit,
        ) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      context("report a required event handler after optional parameters when disallowed") {
        withData(
          nameFn = { it.first },
          Triple(
            "the issue snippet",
            // language=kotlin
            """
            @Composable
            fun Test(
              id: String,
              modifier: Modifier = Modifier,
              onClick: () -> Unit,
            ) {
            }
            """.trimIndent(),
            "onClick",
          ),
          Triple(
            "a handler after an optional non-modifier parameter",
            // language=kotlin
            """
            @Composable
            fun Test(
              enabled: Boolean = false,
              onBack: () -> Unit,
            ) {
            }
            """.trimIndent(),
            "onBack",
          ),
          Triple(
            "a handler before a trailing slot",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              onClick: () -> Unit,
              content: @Composable () -> Unit,
            ) {
            }
            """.trimIndent(),
            "onClick",
          ),
          Triple(
            "a suspend handler",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              onRefresh: suspend () -> Unit,
            ) {
            }
            """.trimIndent(),
            "onRefresh",
          ),
        ) { (_, code, handler) ->
          val findings = ComposableParametersOrdering(disallowed).lint(code)

          findings shouldHaveSize 1
          findings.single().message shouldBe
            "Required event handler \"$handler\" should be placed before optional parameters"
          findings.single().shouldStartAt(code, "$handler:")
        }
      }

      context("not report when disallowed") {
        withData(
          nameFn = { it.first },
          Pair(
            "an optional nullable handler before a trailing slot",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              onClick: (() -> Unit)? = null,
              content: @Composable () -> Unit,
            ) {
            }
            """.trimIndent(),
          ),
          Pair(
            "a trailing handler with a default value",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              onClick: () -> Unit = {},
            ) {
            }
            """.trimIndent(),
          ),
          Pair(
            "a handler among required parameters",
            // language=kotlin
            """
            @Composable
            fun Test(
              id: String,
              onClick: () -> Unit,
              modifier: Modifier = Modifier,
            ) {
            }
            """.trimIndent(),
          ),
          Pair(
            "a trailing lambda with a receiver named as a trailing slot",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              content: LazyListScope.() -> Unit,
            ) {
            }
            """.trimIndent(),
          ),
          Pair(
            "a handler-shaped trailing slot",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              content: () -> Unit,
            ) {
            }
            """.trimIndent(),
          ),
          Pair(
            "a trailing lambda returning a value",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              key: (Int) -> Any,
            ) {
            }
            """.trimIndent(),
          ),
          Pair(
            "a trailing lambda with a receiver",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              onDraw: DrawScope.() -> Unit,
            ) {
            }
            """.trimIndent(),
          ),
          Pair(
            "a trailing handler when there are no optional parameters",
            // language=kotlin
            """
            @Composable
            fun Test(
              id: String,
              modifier: Modifier,
              onClick: () -> Unit,
            ) {
            }
            """.trimIndent(),
          ),
        ) { (_, code) ->
          ComposableParametersOrdering(disallowed).lint(code).shouldBeEmpty()
        }
      }
    }

    context("trailing lambdas") {
      val disallowed = TestConfig("allowTrailingLambdas" to false)

      should("not report a required trailing lambda by default, also when event handlers are disallowed") {
        // language=kotlin
        val code = """
        @Composable
        fun Test(
          value: String,
          modifier: Modifier = Modifier,
          validator: (String) -> Boolean,
        ) {
        }
        """.trimIndent()

        ComposableParametersOrdering().lint(code).shouldBeEmpty()
        ComposableParametersOrdering(TestConfig("allowTrailingEventHandlers" to false)).lint(code).shouldBeEmpty()
      }

      context("report a required lambda after optional parameters when disallowed") {
        withData(
          nameFn = { it.first },
          Triple(
            "a lambda returning a value",
            // language=kotlin
            """
            @Composable
            fun Test(
              value: String,
              modifier: Modifier = Modifier,
              validator: (String) -> Boolean,
            ) {
            }
            """.trimIndent(),
            "lambda" to "validator",
          ),
          Triple(
            "a lambda with a receiver",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              onDraw: DrawScope.() -> Unit,
            ) {
            }
            """.trimIndent(),
            "lambda" to "onDraw",
          ),
          Triple(
            "a lambda before a trailing slot",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              key: (Int) -> Any,
              content: @Composable () -> Unit,
            ) {
            }
            """.trimIndent(),
            "lambda" to "key",
          ),
          Triple(
            "an event handler",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              onClick: () -> Unit,
            ) {
            }
            """.trimIndent(),
            "event handler" to "onClick",
          ),
        ) { (_, code, expected) ->
          val (kind, name) = expected
          val findings = ComposableParametersOrdering(disallowed).lint(code)

          findings shouldHaveSize 1
          findings.single().message shouldBe "Required $kind \"$name\" should be placed before optional parameters"
          findings.single().shouldStartAt(code, "$name:")
        }
      }

      context("not report when disallowed") {
        withData(
          nameFn = { it.first },
          Pair(
            "a lambda named as a trailing slot",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              content: LazyListScope.() -> Unit,
            ) {
            }
            """.trimIndent(),
          ),
          Pair(
            "a trailing composable slot",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              label: @Composable () -> Unit,
            ) {
            }
            """.trimIndent(),
          ),
          Pair(
            "a trailing lambda with a default value",
            // language=kotlin
            """
            @Composable
            fun Test(
              modifier: Modifier = Modifier,
              validator: (String) -> Boolean = { true },
            ) {
            }
            """.trimIndent(),
          ),
          Pair(
            "a lambda among required parameters",
            // language=kotlin
            """
            @Composable
            fun Test(
              validator: (String) -> Boolean,
              modifier: Modifier = Modifier,
            ) {
            }
            """.trimIndent(),
          ),
          Pair(
            "a trailing lambda when there are no optional parameters",
            // language=kotlin
            """
            @Composable
            fun Test(
              value: String,
              validator: (String) -> Boolean,
            ) {
            }
            """.trimIndent(),
          ),
        ) { (_, code) ->
          ComposableParametersOrdering(disallowed).lint(code).shouldBeEmpty()
        }
      }
    }

    context("other functions") {
      should("ignore non-composable functions") {
        // language=kotlin
        val code = """
        fun Test(enabled: Boolean = false, text: String) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("not report an overriding function, its order is set by the overridden one (#48)") {
        // language=kotlin
        val code = """
        class ScreenImpl : Screen {
          @Composable
          override fun Content(title: String, modifier: Modifier, enabled: Boolean) {
          }
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("not report an overriding function of an anonymous object (#48)") {
        // language=kotlin
        val code = """
        val screen = object : Screen {
          @Composable
          override fun Content(title: String, modifier: Modifier, enabled: Boolean) {
          }
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("not report an actual function, its order is set by the expect one") {
        // language=kotlin
        val code = """
        @Composable
        actual fun Foo(text: String, modifier: Modifier, enabled: Boolean) {
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings.shouldBeEmpty()
      }

      should("report an expect function") {
        // language=kotlin
        val code = """
        @Composable
        expect fun Foo(text: String, modifier: Modifier, enabled: Boolean)
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
      }

      should("report an interface function") {
        // language=kotlin
        val code = """
        interface Screen {
          @Composable
          fun Content(title: String, modifier: Modifier, enabled: Boolean)
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
      }

      should("report an abstract function") {
        // language=kotlin
        val code = """
        abstract class Screen {
          @Composable
          abstract fun Content(title: String, modifier: Modifier, enabled: Boolean)
        }
        """.trimIndent()

        val findings = ComposableParametersOrdering().lint(code)

        findings shouldHaveSize 1
      }
    }
  }
}
