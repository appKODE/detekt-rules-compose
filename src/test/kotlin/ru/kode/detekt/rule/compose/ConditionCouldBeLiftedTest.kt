package ru.kode.detekt.rule.compose

import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.exhaustive
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import ru.kode.detekt.rule.compose.snippet.composeSnippet

class ConditionCouldBeLiftedTest : ShouldSpec({
  val envWrapper = createEnvironment()
  val env: KotlinCoreEnvironment = envWrapper.env

  afterSpec { envWrapper.dispose() }

  should("report simple non-compliant case") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test() {
        val printValue = false
        Column {
          if (printValue) {
            Text(text = "3")
            Row {}
          }
        }
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings shouldHaveSize 1
    // 'condition can be lifted out of "Column"'
    findings.first().message shouldContain "Column"
  }

  should("report simple non-compliant case with call expression in then-branch") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test() {
        val printValue = false
        Column {
          if (printValue) Text(text = "3")
        }
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings shouldHaveSize 1
    // 'condition can be lifted out of "Column"'
    findings.first().message shouldContain "Column"
  }

  should("report simple non-compliant case with named argument expression") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test() {
        val printValue = false
        Column(content = {
            if (printValue) {
              Text(text = "3")
              Row {}
            }
          }
        )
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings shouldHaveSize 1
    // 'condition can be lifted out of "Column"'
    findings.first().message shouldContain "Column"
  }

  should("not crash with composable expression value argument") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(content: @Composable () -> Unit) {
        Column(content = content)
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }

  should("report nested non-compliant case") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test() {
        val printValue = false
        Column {
          if (printValue) {
            Text(text = "3")
            val foo = true
            Row {
              if (foo) {
                 Text("hello")
              }
            }
          }
        }
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings shouldHaveSize 2
    // 'condition can be lifted out of "Column"'
    findings[0].message shouldContain "Column"
    // 'condition can be lifted out of "Row"'
    findings[1].message shouldContain "Row"
  }

  should("not report if composable call has non-composable lambda") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test() {
        @Composable fun BoxImpostor(modifier: Modifier = Modifier, nonComposable: () -> Unit) {}
        val printValue = false
        BoxImpostor {
          if (printValue) {
            Text(text = "3")
            Row {}
          }
        }
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }

  should("not check content calls not present in actual call expression") {
    // it used to do this because these exprs are visible through type resolution

    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun CloseButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit = { if (true) { Text("hello") } }
      ) {

      }

      @Composable
      fun Test() {
        CloseButton(modifier = Modifier.padding(10.dp), onClick = {})
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }

  should("not report when 'if' contains an 'else' branch with a Composable call") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test() {
        val printValue = false
        Column {
          if (printValue) {
            Text(text = "3")
            Row {}
          } else {
            Text(text = "4")
          }
        }
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }

  should("report when 'if' contains an 'else' branch without a Composable call") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test() {
        val printValue = false
        Column {
          if (printValue) {
            Text(text = "3")
            Row {}
          } else {
            println("hello")
          }
        }
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings shouldHaveSize 1
  }

  should("report when non-compliant body contains non-conditional expressions") {
    checkAll(Exhaustive.composeLayoutName()) { layoutName ->
      // language=kotlin
      val code = composeSnippet(
        """
      @Composable
      fun Test() {
        val printValue = false
        $layoutName {
          val x = 3
          if (printValue) {
            Text(text = "3")
            val y = 4
            Row {}
          }
          val z = 5
        }
      }
      """,
      )

      val findings = createRule().compileAndLintWithContext(env, code)

      findings shouldHaveSize 1
      findings[0].message shouldContain layoutName
    }
  }

  should("report when non-compliant body contains non-composable function calls") {
    checkAll(Exhaustive.composeLayoutName()) { layoutName ->
      // language=kotlin
      val code = composeSnippet(
        """
      fun foo() = Unit
      fun bar() = Unit
      fun baz() = Unit
      @Composable
      fun Test() {
        val printValue = false
        foo()
        $layoutName {
          if (printValue) {
            bar()
            Text(text = "3")
            Row {}
          }
          baz()
        }
      }
      """,
      )

      val findings = createRule().compileAndLintWithContext(env, code)

      findings shouldHaveSize 1
      findings[0].message shouldContain layoutName
    }
  }

  should("not report when conditional is not a single child of layout") {
    checkAll(Exhaustive.composeLayoutName()) { layoutName ->
      // language=kotlin
      val code = composeSnippet(
        """
      @Composable
      fun Test() {
        val printValue = false
        $layoutName {
          Text(text = "4")
          if (printValue) {
            Text(text = "3")
            Row {}
          }
        }
      }
      """,
      )

      val findings = createRule().compileAndLintWithContext(env, code)

      findings.shouldBeEmpty()
    }
  }

  should("not report when content contains other composable calls") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun BadgedBox(
        icon: @Composable () -> Unit,
        badge: String?,
        modifier: Modifier = Modifier
      ) {
        Box(modifier = modifier) {
          icon()
          if (badge != null) {
            Text(text = badge)
          }
        }
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }

  should("not report when composable lambda is not named 'content'") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun CustomContainer(icon: @Composable () -> Unit) = Unit

      @Composable
      fun Foo(test: Boolean) {
        CustomContainer {
          if (test) {
            Text("hello")
          }
        }
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }

  should("not report when content lambda contains if/else") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Foo(icon: @Composable (() -> Unit)?, isProgressBarVisible: Boolean) {
        Box {
          if (isProgressBarVisible) {
            Text("hello")
          } else if (icon != null) {
            icon()
          }
        }
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }

  should("not report when ignored by default 'modifier' argument is present") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test() {
        val printValue = false
        Column(modifier = Modifier.padding(55.dp)) {
          if (printValue) {
            Text(text = "3")
            Row {}
          }
        }
      }
      """,
    )

    val findings = createRule().compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }

  should("not report when ignored argument name is present") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test() {
        val printValue = false
        Column(verticalAlignment = Alignment.CenterVertically) {
          if (printValue) {
            Text(text = "3")
            Row {}
          }
        }
      }
      """,
    )

    val findings = createRule(
      TestConfig("ignoreCallsWithArgumentNames" to listOf("verticalAlignment")),
    ).compileAndLintWithContext(env, code)

    findings.shouldBeEmpty()
  }
})

private fun Exhaustive.Companion.composeLayoutName() = listOf("Box", "Row", "Column").exhaustive()
private fun createRule(config: Config = Config.empty) = ConditionCouldBeLifted(
  composableAnnotationClassPackage = "ru.kode.detekt.rule",
  config = config,
)
