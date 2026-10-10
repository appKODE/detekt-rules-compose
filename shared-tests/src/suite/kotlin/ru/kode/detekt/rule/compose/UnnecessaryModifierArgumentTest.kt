package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import ru.kode.detekt.rule.compose.snippet.composeSnippet

private const val TEST_COMPOSE_PACKAGE = "ru.kode.detekt.rule"

class UnnecessaryModifierArgumentTest : ShouldSpec({
  val environment = createEnvironment()

  fun lint(code: String) = UnnecessaryModifierArgument(Config.empty, modifierClassPackage = TEST_COMPOSE_PACKAGE)
    .lintWithContext(environment, code)

  fun message(parameter: String, function: String, argument: String = "Modifier") =
    "\"$argument\" is the default value of the \"$parameter\" parameter of \"$function\". Remove the argument"

  fun messageWithFollowers(parameter: String, function: String) =
    message(parameter, function) + " and pass the following arguments by name"

  should("report a positional, a named and a reordered argument") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test() {
        Foo("positional", Modifier)
        Foo("named", modifier = Modifier)
        Foo(modifier = Modifier, text = "reordered")
      }
      """,
    )

    val findings = lint(code)

    findings.map { it.message } shouldBe List(3) { message("modifier", "Foo") }
    findings[0].shouldStartAt(code, "Modifier)\n  Foo(\"named\"")
    findings[1].shouldStartAt(code, "modifier = Modifier)")
    findings[2].shouldStartAt(code, "modifier = Modifier, text")
  }

  should("report a fully qualified and a parenthesized argument") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test() {
        Foo("qualified", ru.kode.detekt.rule.Modifier)
        Foo("parenthesized", (Modifier))
        Qualified(Modifier)
      }

      @Composable
      fun Qualified(modifier: Modifier = (ru.kode.detekt.rule.Modifier)) {}
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      message("modifier", "Foo", argument = "ru.kode.detekt.rule.Modifier"),
      message("modifier", "Foo", argument = "(Modifier)"),
      message("modifier", "Qualified"),
    )
  }

  should("report a parameter with another name, every such argument of a call") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Card(modifier: Modifier = Modifier, contentModifier: Modifier = Modifier) {}

      @Composable
      fun Test() {
        Card(contentModifier = Modifier)
        Card(Modifier, Modifier)
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      message("contentModifier", "Card"),
      messageWithFollowers("modifier", "Card"),
      message("contentModifier", "Card"),
    )
  }

  should("report member, extension, local and constructor calls") {
    // language=kotlin
    val code = composeSnippet(
      """
      class Scope(val modifier: Modifier = Modifier) {
        fun Member(modifier: Modifier = Modifier) {}
      }
      fun Scope.Extension(modifier: Modifier = Modifier) {}

      fun test() {
        fun Local(modifier: Modifier = Modifier) {}
        Local(Modifier)
        Scope(Modifier).Member(Modifier)
        Scope().Extension(modifier = Modifier)
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf("Local", "Scope", "Member", "Extension").map {
      message("modifier", it)
    }
  }

  should("not report another argument or the default value left out") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      val Padded = Modifier.padding(4.dp)

      @Composable
      fun Test(modifier: Modifier = Modifier, stored: Modifier.Companion = Modifier) {
        Foo("default")
        Foo("chain", Modifier.padding(4.dp))
        Foo("variable", modifier)
        Foo("derived", modifier = modifier.fillMaxSize())
        Foo("property", Padded)
        Foo("stored", stored)
        Foo("unresolved", missing)
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report a parameter without a default value or with another one") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable fun Required(modifier: Modifier) {}
      @Composable fun Filled(modifier: Modifier = Modifier.fillMaxSize()) {}
      @Composable fun Optional(modifier: Modifier? = null) {}
      @Composable fun Any(value: Any = Modifier.Companion) {}

      abstract class Base { abstract fun Draw(modifier: Modifier = Modifier) }
      class Child : Base() { override fun Draw(modifier: Modifier) {} }

      @Composable
      fun Test(child: Child, slot: @Composable (Modifier) -> Unit) {
        Required(Modifier)
        Filled(Modifier)
        Optional(Modifier)
        Any(Modifier)
        child.Draw(Modifier)
        slot(Modifier)
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report a Modifier of another package") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test() {
        Foo("Hello", Modifier)
      }
      """,
    )

    UnnecessaryModifierArgument(Config.empty).lintWithContext(environment, code).shouldBeEmpty()
  }

  should("not report a compiled or an unresolved function") {
    // language=kotlin
    val code = composeSnippet(
      """
      fun test() {
        listOf(Modifier)
        lazyOf(value = Modifier)
        Modifier.let { setOf(Modifier, it) }
        Missing(Modifier)
        Missing(modifier = Modifier)
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("check the parameter of the called overload") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable fun Label(text: String, modifier: Modifier = Modifier) {}
      @Composable fun Label(count: Int, modifier: Modifier) {}
      @Composable fun Label(flag: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {}
      @Composable fun Label(value: Float, decoration: Modifier = Modifier, modifier: Modifier = Modifier.weight(1f)) {}

      @Composable
      fun Test() {
        Label("text", Modifier)
        Label(1, Modifier)
        Label(true, Modifier)
        Label(1f, Modifier, Modifier)
      }
      """,
    )

    val findings = lint(code)

    findings.map { it.message } shouldBe
      listOf(message("modifier", "Label"), messageWithFollowers("decoration", "Label"))
    findings[0].shouldStartAt(code, "Modifier)\n  Label(1, ")
    findings[1].shouldStartAt(code, "Modifier, Modifier)")
  }

  should("tell to name the arguments which follow a positional one") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Card(
        title: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        contentModifier: Modifier = Modifier,
        content: @Composable () -> Unit = {},
      ) {}

      @Composable
      fun Test() {
        Card("positional follower", Modifier, false)
        Card("positional lambda", Modifier, false, Modifier, {})
        Card("named in between", Modifier, enabled = false, Modifier)
        Card("trailing lambda", Modifier) { Unit }
        Card("named follower", Modifier, enabled = true)
        Card("named itself", modifier = Modifier, enabled = false)
      }
      """,
    )

    val findings = lint(code)

    findings.map { it.message } shouldBe listOf(
      messageWithFollowers("modifier", "Card"),
      messageWithFollowers("modifier", "Card"),
      messageWithFollowers("contentModifier", "Card"),
      messageWithFollowers("modifier", "Card"),
      message("contentModifier", "Card"),
      message("modifier", "Card"),
      message("modifier", "Card"),
      message("modifier", "Card"),
    )
    findings[0].shouldStartAt(code, "Modifier, false)")
    findings[5].shouldStartAt(code, "Modifier) { Unit }")
    findings[6].shouldStartAt(code, "Modifier, enabled = true)")
    findings[7].shouldStartAt(code, "modifier = Modifier, enabled = false)")
  }

  should("report the argument although another overload has no such parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable fun Title(text: String) {}
      @Composable fun Title(text: String, modifier: Modifier = Modifier) {}

      @Composable
      fun Test() {
        Title("Hello", Modifier)
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(message("modifier", "Title"))
  }

  should("report inherited and delegated members") {
    // language=kotlin
    val code = composeSnippet(
      """
      open class Base { fun Draw(modifier: Modifier = Modifier) {} }
      class Child : Base()
      open class Generic<T> { fun Paint(item: T? = null, modifier: Modifier = Modifier) {} }
      class GenericChild : Generic<String>()
      interface Shape { fun Outline(modifier: Modifier = Modifier) }
      class Delegate(shape: Shape) : Shape by shape

      fun test(child: Child, generic: GenericChild, delegate: Delegate) {
        child.Draw(Modifier)
        generic.Paint(null, modifier = Modifier)
        delegate.Outline(Modifier)
      }
      """,
    )

    lint(code).map { it.message } shouldBe
      listOf(message("modifier", "Draw"), message("modifier", "Paint"), message("modifier", "Outline"))
  }

  should("name a constructor called through a type alias after the class") {
    // language=kotlin
    val code = composeSnippet(
      """
      class AppCard(val modifier: Modifier = Modifier)
      typealias Card = AppCard

      fun test() {
        Card(Modifier)
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(message("modifier", "AppCard"))
  }

  should("not report a generated function or a call with errors") {
    // language=kotlin
    val code = composeSnippet(
      """
      data class Style(val modifier: Modifier = Modifier, val size: Int = 0)
      @Composable fun Card(title: String, modifier: Modifier = Modifier) {}

      fun test(style: Style) {
        style.copy(modifier = Modifier)
        style.copy(Modifier)
        Card(1, Modifier)
        Card("extra argument", Modifier, 3)
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }
})

private const val STUBS = """
      @Composable fun Foo(text: String, modifier: Modifier = Modifier) {}
"""
