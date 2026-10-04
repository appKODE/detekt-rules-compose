/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize

class MissingModifierDefaultValueTest : ShouldSpec(
  {
    val checkAbstractFunctions = TestConfig("checkAbstractFunctions" to true)

    should("report if modifier parameter has no default value") {
      // language=kotlin
      val code = """
      @Composable
      @Preview
      fun Test(modifier: Modifier) {
        Text(text = "3")
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings shouldHaveSize 1
    }

    should("not report if modifier parameter has a default value") {
      // language=kotlin
      val code = """
      @Composable
      @Preview
      fun Test(modifier: Modifier = Modifier) {
        Text(text = "3")
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report if no modifier parameter present") {
      // language=kotlin
      val code = """
      @Composable
      @Preview
      fun Test(text: String) {
        Text(text = text)
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report an interface function") {
      // language=kotlin
      val code = """
      interface Screen {
        @Composable
        fun Content(modifier: Modifier)
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report an abstract function") {
      // language=kotlin
      val code = """
      abstract class Screen {
        @Composable
        abstract fun Content(modifier: Modifier)
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report an open function") {
      // language=kotlin
      val code = """
      open class Screen {
        @Composable
        open fun Content(modifier: Modifier) {}
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report overriding function when inheriting an open class") {
      // language=kotlin
      val code = """
      open class Screen {
        @Composable
        open fun Content(modifier: Modifier = Modifier) {

        }
      }

      class ScreenImpl : Screen() {
        @Composable
        override fun Content(modifier: Modifier) {
          super.Content(modifier)
        }
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report overriding function when inheriting an abstract class") {
      // language=kotlin
      val code = """
      abstract class Screen {
        @Composable
        abstract fun Content(modifier: Modifier)
      }

      class ScreenImpl : Screen() {
        @Composable
        override fun Content(modifier: Modifier) {
        }
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report overriding function when inheriting an interface") {
      // language=kotlin
      val code = """
      interface Screen {
        @Composable
        fun Content(modifier: Modifier)
      }

      class ScreenImpl : Screen {
        @Composable
        override fun Content(modifier: Modifier) {
        }
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }

    should("not report an actual") {
      // language=kotlin
      val code = """
        actual fun NativeView(
          modifier: Modifier
        ) {}
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }

    should("report an interface function when abstract functions are checked (#47)") {
      // language=kotlin
      val code = """
      interface Screen {
        @Composable
        fun Content(modifier: Modifier)
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue(checkAbstractFunctions).lint(code)

      findings shouldHaveSize 1
    }

    should("report an abstract function when abstract functions are checked (#47)") {
      // language=kotlin
      val code = """
      abstract class Screen {
        @Composable
        abstract fun Content(modifier: Modifier)
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue(checkAbstractFunctions).lint(code)

      findings shouldHaveSize 1
    }

    should("report an open function when abstract functions are checked (#47)") {
      // language=kotlin
      val code = """
      open class Screen {
        @Composable
        open fun Content(modifier: Modifier) {}
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue(checkAbstractFunctions).lint(code)

      findings shouldHaveSize 1
    }

    should("report an interface function with a body when abstract functions are checked") {
      // language=kotlin
      val code = """
      interface Screen {
        @Composable
        fun Content(modifier: Modifier) {}
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue(checkAbstractFunctions).lint(code)

      findings shouldHaveSize 1
    }

    should("not report an abstract function of a fun interface, it can't have default values") {
      // language=kotlin
      val code = """
      fun interface Screen {
        @Composable
        fun Content(modifier: Modifier)
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue(checkAbstractFunctions).lint(code)

      findings.shouldBeEmpty()
    }

    should("not report an abstract overriding function when abstract functions are checked") {
      // language=kotlin
      val code = """
      abstract class ScreenImpl : Screen {
        @Composable
        abstract override fun Content(modifier: Modifier)
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue(checkAbstractFunctions).lint(code)

      findings.shouldBeEmpty()
    }

    should("not report an overriding function declared in an interface when abstract functions are checked") {
      // language=kotlin
      val code = """
      interface DetailsScreen : Screen {
        @Composable
        override fun Content(modifier: Modifier)
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue(checkAbstractFunctions).lint(code)

      findings.shouldBeEmpty()
    }

    should("not report a composable actual when abstract functions are checked") {
      // language=kotlin
      val code = """
      @Composable
      actual fun NativeView(modifier: Modifier) {}
      """.trimIndent()

      val findings = MissingModifierDefaultValue(checkAbstractFunctions).lint(code)

      findings.shouldBeEmpty()
    }

    should("report an expect function") {
      // language=kotlin
      val code = """
      @Composable
      expect fun NativeView(modifier: Modifier)
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings shouldHaveSize 1
    }

    should("report a final function of an abstract class") {
      // language=kotlin
      val code = """
      abstract class Screen {
        @Composable
        fun Content(modifier: Modifier) {}
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings shouldHaveSize 1
    }

    should("ignore non-composable functions") {
      // language=kotlin
      val code = """
      fun Test(modifier: Modifier) {
      }
      """.trimIndent()

      val findings = MissingModifierDefaultValue().lint(code)

      findings.shouldBeEmpty()
    }
  },
)
