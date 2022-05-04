package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class PublicComposablePreviewTest : ShouldSpec({
  should("report error if file has public preview function") {
    // language=kotlin
    val code = """
      @Composable
      @Preview
      fun Test(modifier: Modifier, value: Int) {
        Text(
          text = "3",
        )
      }
    """.trimIndent()

    val findings = PublicComposablePreview().lint(code)

    findings shouldHaveSize 1
  }

  should("report error if class has public preview function") {
    // language=kotlin
    val code = """
      class MyClass {
        @Composable
        @Preview
        fun Test(modifier: Modifier, value: Int) {
          Text(
            text = "3",
          )
        }
      }
    """.trimIndent()

    val findings = PublicComposablePreview().lint(code)

    findings shouldHaveSize 1
  }

  should("report error if object has public preview function") {
    // language=kotlin
    val code = """
      object MyClass {
        @Composable
        @Preview
        fun Test(modifier: Modifier, value: Int) {
          Text(
            text = "3",
          )
        }
      }
    """.trimIndent()

    val findings = PublicComposablePreview().lint(code)

    findings shouldHaveSize 1
  }

  should("not report error if file has public preview function") {
    checkAll(Exhaustive.of("internal", "private")) { modifier ->
      // language=kotlin
      val code = """
      @Composable
      @Preview
      $modifier fun Test(modifier: Modifier, value: Int) {
        Text(
          text = "3",
        )
      }
      """.trimIndent()

      val findings = PublicComposablePreview().lint(code)

      findings.shouldBeEmpty()
    }
  }

  should("not report error if class has public preview function") {
    checkAll(Exhaustive.of("internal", "private")) { modifier ->
      // language=kotlin
      val code = """
      class MyClass {
        @Composable
        @Preview
        $modifier fun Test(modifier: Modifier, value: Int) {
          Text(
            text = "3",
          )
        }
      }
      """.trimIndent()

      val findings = PublicComposablePreview().lint(code)

      findings.shouldBeEmpty()
    }
  }

  should("not report error if object has public preview function") {
    checkAll(Exhaustive.of("internal", "private")) { modifier ->
      // language=kotlin
      val code = """
      object MyClass {
        @Composable
        @Preview
        $modifier fun Test(modifier: Modifier, value: Int) {
          Text(
            text = "3",
          )
        }
      }
      """.trimIndent()

      val findings = PublicComposablePreview().lint(code)

      findings.shouldBeEmpty()
    }
  }

  should("not report error if object has public non-preview function") {
    // language=kotlin
    val code = """
      object MyClass {
        @Composable
        fun Test(modifier: Modifier, value: Int) {
          Text(
            text = "3",
          )
        }
      }

      class MyClass {
        @Composable
        fun Test(modifier: Modifier, value: Int) {
          Text(
            text = "3",
          )
        }
      }

      @Composable
      fun HelloPreviewButNotPreview() {
          Text(
            text = "3",
          )
      }
    """.trimIndent()

    val findings = PublicComposablePreview().lint(code)

    findings.shouldBeEmpty()
  }
})
