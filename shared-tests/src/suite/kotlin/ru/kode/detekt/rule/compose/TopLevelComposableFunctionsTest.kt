/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.detekt.rule.compose

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize

class TopLevelComposableFunctionsTest : ShouldSpec(
  {
    should("report if composable function is inside a class") {
      // language=kotlin
      val code = """
      class Screen {
        @Composable
        fun Test() {
          Text(text = "3")
        }
      }
      """.trimIndent()

      val findings = TopLevelComposableFunctions().lint(code)

      findings shouldHaveSize 1
    }

    should("not report top-level functions") {
      // language=kotlin
      val code = """
      @Composable
      fun Test() {
        Text(text = "3")
      }
      """.trimIndent()

      val findings = TopLevelComposableFunctions().lint(code)

      findings.shouldBeEmpty()
    }

    should("report functions in interface or abstract classes") {
      // language=kotlin
      val code = """
      interface Screen {
        @Composable
        fun Content(modifier: Modifier = Modifier)
      }

      abstract class ScreenAbs {
        @Composable
        abstract fun Content(modifier: Modifier = Modifier)
      }
      """.trimIndent()

      val findings = TopLevelComposableFunctions().lint(code)

      findings shouldHaveSize 2
    }

    should("not report overriding functions") {
      // language=kotlin
      val code = """
      interface Screen {
        @Composable
        fun Content(modifier: Modifier = Modifier)
      }

      class ScreenImpl : Screen {
        @Composable
        override fun Content(modifier: Modifier) {
        }
      }
      """.trimIndent()

      val findings = TopLevelComposableFunctions().lint(code)

      // for Content in interface, but not for the implemented override
      findings shouldHaveSize 1
    }

    should("report in objects if not explicitly allowed by config") {
      // language=kotlin
      val code = """
      object ButtonDefaults {
        @Composable
        fun contentColor() = Unit
      }
      """.trimIndent()

      val findings = TopLevelComposableFunctions().lint(code)

      findings shouldHaveSize 1
    }

    should("not report in objects if allowed by config") {
      // language=kotlin
      val code = """
      object ButtonDefaults {
        @Composable
        fun contentColor() = Unit
      }
      """.trimIndent()

      val findings = TopLevelComposableFunctions(TestConfig("allowInObjects" to true)).lint(code)

      findings.shouldBeEmpty()
    }

    should("report in classes if allowed in objects by config") {
      // language=kotlin
      val code = """
      class ButtonDefaults {
        @Composable
        fun contentColor() = Unit
      }
      """.trimIndent()

      val findings = TopLevelComposableFunctions(TestConfig("allowInObjects" to true)).lint(code)

      findings shouldHaveSize 1
    }

    should("not report in companion objects if allowed by config") {
      // language=kotlin
      val code = """
      class Screen {
        companion object {
          @Composable
          fun Content() = Unit
        }
      }
      """.trimIndent()

      val findings = TopLevelComposableFunctions(TestConfig("allowInObjects" to true)).lint(code)

      findings.shouldBeEmpty()
    }

    should("report in a class nested in an object if allowed in objects by config") {
      // language=kotlin
      val code = """
      object Screens {
        class Screen {
          @Composable
          fun Content() = Unit
        }
      }
      """.trimIndent()

      val findings = TopLevelComposableFunctions(TestConfig("allowInObjects" to true)).lint(code)

      findings shouldHaveSize 1
    }

    should("not report local composable functions") {
      // language=kotlin
      val code = """
      @Composable
      fun Screen() {
        @Composable
        fun Content() = Unit
      }
      """.trimIndent()

      val findings = TopLevelComposableFunctions().lint(code)

      findings.shouldBeEmpty()
    }

    // https://github.com/appKODE/detekt-rules-compose/issues/29
    should("report in interfaces unless allowed by config") {
      // language=kotlin
      val code = """
      interface OffersProvider {
        @Composable
        fun Offers(id: String, modifier: Modifier = Modifier)
      }
      """.trimIndent()

      TopLevelComposableFunctions().lint(code) shouldHaveSize 1
      TopLevelComposableFunctions(TestConfig("allowInInterfaces" to false)).lint(code) shouldHaveSize 1
      TopLevelComposableFunctions(TestConfig("allowInInterfaces" to true)).lint(code).shouldBeEmpty()
    }

    should("report in classes, abstract classes and objects if allowed in interfaces by config") {
      // language=kotlin
      val code = """
      class Screen {
        @Composable
        fun Content() = Unit
      }

      abstract class ScreenAbs {
        @Composable
        abstract fun Content(modifier: Modifier = Modifier)
      }

      object ButtonDefaults {
        @Composable
        fun contentColor() = Unit
      }
      """.trimIndent()

      val findings = TopLevelComposableFunctions(TestConfig("allowInInterfaces" to true)).lint(code)

      findings shouldHaveSize 3
    }

    should("decide on an interface companion object by allowInObjects, not allowInInterfaces") {
      // language=kotlin
      val code = """
      interface OffersProvider {
        companion object {
          @Composable
          fun Offers() = Unit
        }
      }
      """.trimIndent()

      TopLevelComposableFunctions(TestConfig("allowInInterfaces" to true)).lint(code) shouldHaveSize 1
      TopLevelComposableFunctions(TestConfig("allowInObjects" to true)).lint(code).shouldBeEmpty()
    }
  },
)
