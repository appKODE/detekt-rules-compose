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

class NonImmutableDataClassParameterTest : ShouldSpec({
  val environment = createEnvironment()

  fun lint(code: String, config: Config = Config.empty) =
    NonImmutableDataClassParameter(config).lintWithContext(environment, code)

  should("report data class parameters without a stability annotation") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      data class Profile(val name: String)
      typealias ProfileAlias = Profile

      @Composable
      fun Test(profile: Profile, optional: Profile?, alias: ProfileAlias, name: String) {}
      """,
    )

    val findings = lint(code)

    findings.map { it.message } shouldBe listOf(
      "Parameter \"profile\" is a data class \"Profile\" which is not marked with @Immutable or @Stable",
      "Parameter \"optional\" is a data class \"Profile\" which is not marked with @Immutable or @Stable",
      "Parameter \"alias\" is a data class \"Profile\" which is not marked with @Immutable or @Stable",
    )
    findings[0].shouldStartAt(code, "profile: Profile")
    findings[1].shouldStartAt(code, "optional: Profile?")
    findings[2].shouldStartAt(code, "alias: ProfileAlias")
  }

  should("report at the name of an annotated parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
      annotation class Marker
      data class Profile(val name: String)

      @Composable
      fun Test(@Marker profile: Profile) {}
      """,
    )

    lint(code).single().shouldStartAt(code, "profile: Profile")
  }

  should("not report vararg parameters") {
    // language=kotlin
    val code = composeSnippet(
      """
      data class Profile(val name: String)

      @Composable
      fun Test(vararg profiles: Profile) {}
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report data classes marked with @Immutable or @Stable") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Immutable
      data class Profile(val name: String)

      @Stable
      data class Settings(val dark: Boolean)

      @Composable
      fun Test(profile: Profile, settings: Settings) {}
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report parameters which are not data classes") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      class Plain(val name: String)
      enum class Kind { A }
      data class Profile(val name: String)

      @Composable
      fun <T> Test(plain: Plain, kind: Kind, generic: T, plains: List<Plain>, onClick: (Profile) -> Unit) {}
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("report data classes in the type arguments") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      data class Profile(val name: String)
      data class Page<T>(val items: List<T>)
      @Immutable data class Marked(val name: String)
      typealias Profiles = List<Profile>

      @Composable
      fun Test(
        profiles: List<Profile>,
        nested: Map<String, List<Profile?>>?,
        page: Page<Profile>,
        same: Page<Page<Profile>>,
        marked: List<Marked>,
        star: List<*>,
        pair: Pair<Profile, Marked>,
        onClick: (List<Profile>) -> Unit,
        alias: Profiles,
      ) {}
      """,
    )

    val findings = lint(code)

    findings.map { it.message } shouldBe listOf(
      "Parameter \"profiles\" uses a data class \"Profile\" which is not marked with @Immutable or @Stable",
      "Parameter \"nested\" uses a data class \"Profile\" which is not marked with @Immutable or @Stable",
      "Parameter \"page\" is a data class \"Page\" which is not marked with @Immutable or @Stable",
      "Parameter \"page\" uses a data class \"Profile\" which is not marked with @Immutable or @Stable",
      "Parameter \"same\" is a data class \"Page\" which is not marked with @Immutable or @Stable",
      "Parameter \"same\" uses a data class \"Profile\" which is not marked with @Immutable or @Stable",
      "Parameter \"pair\" uses a data class \"Profile\" which is not marked with @Immutable or @Stable",
    )
    findings[0].shouldStartAt(code, "profiles: List<Profile>")
  }

  should("report the data classes of the same name in the type arguments separately") {
    // language=kotlin
    val code = composeSnippet(
      """
      data class Profile(val name: String)
      class Outer { data class Profile(val name: String) }

      @Composable
      fun Test(both: Map<Profile, Outer.Profile>, twice: Map<Profile, Profile>) {}
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "Parameter \"both\" uses a data class \"Profile\" which is not marked with @Immutable or @Stable",
      "Parameter \"both\" uses a data class \"Profile\" which is not marked with @Immutable or @Stable",
      "Parameter \"twice\" uses a data class \"Profile\" which is not marked with @Immutable or @Stable",
    )
  }

  should("not report data classes which the Compose compiler knows to be stable at compile time") {
    // language=kotlin
    val code = composeSnippet(
      """
      annotation class StabilityInferred(val parameters: Int)
      @StabilityInferred(parameters = 1) data class Stable(val name: String)
      @StabilityInferred(parameters = 0) data class Unstable(var name: String)
      @StabilityInferred(parameters = 0) data class UnstableBox<T>(var name: String)
      @StabilityInferred(parameters = 2) data class StableBox<T>(val name: String)
      @StabilityInferred(parameters = 1) data class DependentBox<T>(val value: T)
      @StabilityInferred(parameters = 2) data class Half<A, B>(val value: B)
      data class Profile(val name: String)

      @Composable
      fun Test(
        stable: Stable,
        unstable: Unstable,
        unstableBox: UnstableBox<Int>,
        stableBox: StableBox<Int>,
        dependentBox: DependentBox<Int>,
        stableBoxOfUnstable: StableBox<Profile>,
        dependentBoxOfUnstable: DependentBox<Profile>,
        independentHalf: Half<Profile, Int>,
        dependentHalf: Half<Int, Profile>,
        nested: StableBox<DependentBox<Profile>>,
        nestedDependent: DependentBox<DependentBox<Profile>>,
      ) {}
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "Parameter \"unstable\" is a data class \"Unstable\" which is not marked with @Immutable or @Stable",
      "Parameter \"unstableBox\" is a data class \"UnstableBox\" which is not marked with @Immutable or @Stable",
      // the stability of a generic class depends only on the type parameters whose bit is set
      "Parameter \"dependentBoxOfUnstable\" uses a data class \"Profile\" which is not marked with " +
        "@Immutable or @Stable",
      "Parameter \"dependentHalf\" uses a data class \"Profile\" which is not marked with @Immutable or @Stable",
      "Parameter \"nestedDependent\" uses a data class \"Profile\" which is not marked with @Immutable or @Stable",
    )
  }

  should("not report data classes matching the stable type patterns") {
    // language=kotlin
    val code = composeSnippet(
      """
      data class Profile(val name: String)
      data class Page<T>(val items: List<T>)
      class Outer { data class Inner(val name: String) }

      @Composable
      fun Test(profile: Profile, page: Page<Int>, inner: Outer.Inner) {}
      """,
    )

    fun reported(vararg patterns: String) =
      lint(code, TestConfig("stableTypePatterns" to patterns.toList())).map { it.message.substringBefore(" is a") }

    val all = listOf("Parameter \"profile\"", "Parameter \"page\"", "Parameter \"inner\"")
    reported("ru.kode.detekt.rule.Profile") shouldBe all - "Parameter \"profile\""
    reported("ru.kode.detekt.rule.Page<*>", " ru.kode.detekt.rule.Outer.Inner") shouldBe listOf("Parameter \"profile\"")
    // one star stays inside a segment, two cross the segments
    reported("ru.kode.detekt.rule.P*") shouldBe listOf("Parameter \"inner\"")
    reported("ru.kode.detekt.rule.*") shouldBe listOf("Parameter \"inner\"")
    reported("ru.kode.*") shouldBe all
    reported("ru.kode.**") shouldBe emptyList()
    reported("ru.**.Inner") shouldBe all - "Parameter \"inner\""
    // the dots are literal, the name is matched as a whole
    reported("ruXkode.**", "ru.kode.detekt.rule.Prof", "kode.detekt.rule.Profile") shouldBe all
    // a star is at least one character, like in the Compose compiler
    reported("ru.kode.detekt.rule.Profile*", "ru.kode.detekt.rule.Outer.Inner**") shouldBe all
    // blank lines and comment lines of the stability configuration file are skipped
    reported("", "  ", "// ru.kode.**", "ru.kode.detekt.rule.Profile") shouldBe all - "Parameter \"profile\""
  }

  should("check only the type arguments which a stable type pattern marks with a star") {
    // language=kotlin
    val code = composeSnippet(
      """
      data class Profile(val name: String)
      data class Both<A, B>(val first: A, val second: B)

      @Composable
      fun Test(first: Both<Profile, Int>, second: Both<Int, Profile>, list: List<Profile>) {}
      """,
    )

    fun reported(vararg patterns: String) =
      lint(code, TestConfig("stableTypePatterns" to patterns.toList())).map { it.message.substringBefore(" which") }

    val list = "Parameter \"list\" uses a data class \"Profile\""
    reported("ru.kode.detekt.rule.Both<*,_>") shouldBe
      listOf("Parameter \"first\" uses a data class \"Profile\"", list)
    reported("ru.kode.detekt.rule.Both<_,_>", "kotlin.collections.List<_>") shouldBe emptyList()
    // without the type parameters every argument is checked
    reported("ru.kode.detekt.rule.Both") shouldBe listOf(
      "Parameter \"first\" uses a data class \"Profile\"",
      "Parameter \"second\" uses a data class \"Profile\"",
      list,
    )
  }

  should("fail on an invalid stable type pattern with an error which names the option") {
    val code = composeSnippet("data class Profile(val name: String)\n@Composable fun Test(profile: Profile) {}")

    listOf("com.example.*Profile", "com.example.Profile // comment", "com/example/Profile", "(").forEach { pattern ->
      val error = shouldThrowAny { lint(code, TestConfig("stableTypePatterns" to listOf(pattern))) }

      generateSequence(error) { it.cause }.map { it.message.orEmpty() }.toList() shouldContain
        "NonImmutableDataClassParameter: stableTypePatterns \"$pattern\" is not a valid pattern"
    }
  }

  should("not report data classes of the kotlin package") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(pair: Pair<String, Int>, triple: Triple<Int, Int, Int>) {}
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report functions which are not composable") {
    // language=kotlin
    val code = composeSnippet(
      """
      data class Profile(val name: String)

      fun test(profile: Profile) {}
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report unresolved types") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(profile: Missing) {}
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report data classes of the ignored package segments") {
    // language=kotlin
    val code = composeSnippet(
      """
      data class Profile(val name: String)

      @Composable
      fun Test(profile: Profile) {}
      """,
    )

    lint(code, TestConfig("ignoredPackageSegments" to listOf("detekt"))).shouldBeEmpty()
    lint(code, TestConfig("ignoredPackageSegments" to listOf("Profile", "other"))).size shouldBe 1
  }

  should("not match the ignored package segments against outer class names") {
    // language=kotlin
    val code = composeSnippet(
      """
      class Outer { data class Inner(val name: String) }

      @Composable
      fun Test(inner: Outer.Inner) {}
      """,
    )

    lint(code, TestConfig("ignoredPackageSegments" to listOf("Outer"))).map { it.message } shouldBe listOf(
      "Parameter \"inner\" is a data class \"Inner\" which is not marked with @Immutable or @Stable",
    )
    lint(code, TestConfig("ignoredPackageSegments" to listOf("rule"))).shouldBeEmpty()
  }
})

private const val STUBS = """
      annotation class Immutable
      annotation class Stable
"""
