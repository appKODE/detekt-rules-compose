package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.test.TestConfig
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import ru.kode.detekt.rule.compose.snippet.composeSnippet

class StateReadCouldBeLoweredTest : ShouldSpec({
  val environment = createEnvironment()

  fun lint(code: String, config: Config = Config.empty) = StateReadCouldBeLowered(
    config = config,
    composableAnnotationClassPackage = "ru.kode.detekt.rule",
  ).lintWithContext(environment, code)

  should("report state and composition local reads used two content lambdas deeper") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test(state: State<Int>, show: Boolean) {
        val count = state.value
        val isPortrait = LocalOrientation.current == Orientation.Portrait
        if (show) {
          Box {
            Column {
              if (isPortrait) Text(text = "portrait")
              Text(text = count.toString())
            }
          }
        }
      }
      """,
    )

    val findings = lint(code)

    findings.map { it.message } shouldBe listOf(
      "\"count\" is only used inside the content of \"Column\". Read the state there instead",
      "\"isPortrait\" is only used inside the content of \"Column\". Read the state there instead",
    )
    findings[0].shouldStartAt(code, "count = state.value")
    findings[1].shouldStartAt(code, "isPortrait = LocalOrientation")
  }

  should("report reads of state subtypes, nullable states and smart cast states") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test(mutable: MutableState<Int>, optional: State<Int>?, any: Any) {
        val count = mutable.value
        val maybe = optional?.value
        val cast = if (any is State<*>) any.value else null
        Box {
          Column {
            Text(text = count.toString() + maybe + cast)
          }
        }
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf("count", "maybe", "cast").map {
      "\"$it\" is only used inside the content of \"Column\". Read the state there instead"
    }
  }

  should("not report value and current of receivers which are not a state or a composition local") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @JvmInline value class UserId(val value: String)
      class User(val id: UserId)
      object LocalFake { val current: Int get() = 0 }
      class Other { class State(val value: Int) }

      @Composable
      fun Test(user: User, entry: Map.Entry<String, String>, lazy: Lazy<String>, other: Other.State, state: Missing) {
        val id = user.id.value
        val text = entry.value
        val lazyText = lazy.value
        val fake = LocalFake.current
        val namesake = other.value
        val unresolved = state.value
        Box { Column { Text(text = id + text + lazyText + fake + namesake + unresolved) } }
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not count a call of a function with the same name as a usage") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      fun count(): Int = 0

      @Composable
      fun Test(state: State<Int>) {
        val count = state.value
        Box { Column { Text(text = count().toString()) } }
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("report the deepest content lambda shared by all usages") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test(state: State<Int>) {
        val count = state.value
        Box {
          Column {
            Row { Text(text = count.toString()) }
            Button(onClick = { log(count) }) {}
          }
        }
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "\"count\" is only used inside the content of \"Column\". Read the state there instead",
    )
  }

  should("not report a read used in the function body or one content lambda deep") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test(state: State<Int>, other: State<Int>) {
        val count = state.value
        val total = other.value
        log(count)
        Box {
          Column { Text(text = count.toString()) }
          Text(text = total.toString())
        }
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report usages which share less content lambdas than minDepth") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test(state: State<Int>) {
        val count = state.value
        Box {
          Column { Text(text = count.toString()) }
          Row { Text(text = count.toString()) }
        }
      }
      """,
    )

    lint(code).shouldBeEmpty()
    lint(code, TestConfig("minDepth" to 1)).map { it.message } shouldBe listOf(
      "\"count\" is only used inside the content of \"Box\". Read the state there instead",
    )
  }

  should("not count lambdas which are not composable content") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test(state: State<Int>, items: List<Int>) {
        val count = state.value
        Box {
          items.forEach { Text(text = (count + it).toString()) }
          Button(onClick = { log(count) }) {}
        }
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report delegates, vars, remembered values, lambdas and other initializers") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test(state: State<Int>, plain: Int) {
        val delegated by lazy { state.value }
        var mutable = state.value
        val remembered = rememberSaved(state.value)
        val lambda = { state.value }
        val other = plain + 1
        val current = Orientation.Portrait.current
        Box {
          Column {
            log(delegated)
            log(mutable)
            log(remembered)
            log(lambda())
            log(other)
            log(current)
          }
        }
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report an unused read, a shadowed usage or a member with the same name") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      class Holder(val count: Int)

      @Composable
      fun Test(state: State<Int>, holder: Holder) {
        val unused = state.value
        val count = state.value
        Box {
          Column {
            val count = 1
            log(count)
            log(holder.count)
            log(value = 2)
          }
        }
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report when the initializer uses a name redeclared on the way down") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable fun Pager(content: @Composable (Int) -> Unit) {}

      @Composable
      fun Test(state: State<Int>, page: Int) {
        val shifted = state.value + page
        val scaled = state.value * 2
        Box {
          Pager { page ->
            log(shifted)
            log(scaled)
          }
        }
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "\"scaled\" is only used inside the content of \"Pager\". Read the state there instead",
    )
  }

  should("report a read used inside a slot which is not the first composable lambda parameter") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test(state: State<Int>) {
        val count = state.value
        val title = state.value
        Box {
          Scaffold(topBar = { Text(text = title.toString()) }) {
            Text(text = count.toString())
          }
        }
      }

      @Composable
      fun BothSlots(state: State<Int>) {
        val count = state.value
        Scaffold(topBar = { Text(text = count.toString()) }) {
          Text(text = count.toString())
        }
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "\"count\" is only used inside the content of \"Scaffold\". Read the state there instead",
      "\"title\" is only used inside the \"topBar\" slot of \"Scaffold\". Read the state there instead",
    )
  }

  should("not count the lambdas of inline composables, they are no recomposition scopes") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun OnlyInline(state: State<Int>) {
        val count = state.value
        InlineBox {
          InlineBox {
            Text(text = count.toString())
          }
        }
      }

      @Composable
      fun OneScope(state: State<Int>) {
        val count = state.value
        Box {
          InlineBox {
            Text(text = count.toString())
          }
        }
      }

      @Composable
      fun TwoScopes(state: State<Int>) {
        val count = state.value
        Box {
          InlineBox {
            Box {
              Text(text = count.toString())
            }
          }
        }
      }

      @Composable
      fun NoinlineSlot(state: State<Int>) {
        val count = state.value
        Box {
          InlineBox(kept = { Text(text = count.toString()) }) {}
        }
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "\"count\" is only used inside the content of \"Box\". Read the state there instead",
      "\"count\" is only used inside the \"kept\" slot of \"InlineBox\". Read the state there instead",
    )
    lint(code, TestConfig("minDepth" to 1)).size shouldBe 3
  }

  should("count a crossinline lambda, it is inlined into a lambda of the callee and not into the caller") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      interface LazyListScope { fun items(count: Int, itemContent: @Composable (Int) -> Unit) }
      @Composable fun LazyColumn(content: LazyListScope.() -> Unit) {}
      inline fun <T> LazyListScope.items(items: List<T>, crossinline itemContent: @Composable (T) -> Unit) =
        items(items.size) { itemContent(items[it]) }

      @Composable
      fun LazyItems(state: State<Int>, list: List<Int>) {
        val count = state.value
        LazyColumn {
          items(list) { Text(text = (it + count).toString()) }
        }
      }

      @Composable
      fun NestedInItem(state: State<Int>, list: List<Int>) {
        val count = state.value
        Box {
          LazyColumn {
            items(list) { Text(text = (it + count).toString()) }
          }
        }
      }

      @Composable
      fun InlinedInItem(state: State<Int>, list: List<Int>) {
        val count = state.value
        LazyColumn {
          items(list) {
            InlineBox { Text(text = (it + count).toString()) }
          }
        }
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf(
      "\"count\" is only used inside the \"itemContent\" slot of \"items\". Read the state there instead",
    )
    lint(code, TestConfig("minDepth" to 1)).map { it.message } shouldBe listOf(
      "\"count\" is only used inside the \"itemContent\" slot of \"items\". Read the state there instead",
      "\"count\" is only used inside the \"itemContent\" slot of \"items\". Read the state there instead",
      "\"count\" is only used inside the content of \"InlineBox\". Read the state there instead",
    )
  }

  should("not report in a function which is not composable or has an expression body") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      fun Plain(state: State<Int>) {
        val count = state.value
        run { run { log(count) } }
      }

      @Composable
      fun Expression(state: State<Int>) = Box { Column { Text(text = state.value.toString()) } }
      """,
    )

    lint(code).shouldBeEmpty()
  }
})

private const val STUBS = """
      open class State<T>(val value: T)
      class MutableState<T>(value: T) : State<T>(value)
      open class CompositionLocal<T>(val current: T)
      class ProvidableCompositionLocal<T>(current: T) : CompositionLocal<T>(current)
      enum class Orientation { Portrait; val current: Int get() = 0 }
      val LocalOrientation = ProvidableCompositionLocal(Orientation.Portrait)
      fun <T> rememberSaved(value: T): T = value
      fun log(value: Int) {}
      @Composable fun Scaffold(topBar: @Composable () -> Unit, content: @Composable () -> Unit) {}
      @Composable inline fun InlineBox(noinline kept: @Composable () -> Unit = {}, content: @Composable () -> Unit) {}
"""
