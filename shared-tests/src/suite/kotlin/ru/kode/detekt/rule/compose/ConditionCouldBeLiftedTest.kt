package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.test.TestConfig
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.exhaustive
import ru.kode.detekt.rule.compose.snippet.composeSnippet

class ConditionCouldBeLiftedTest : ShouldSpec({
  val environment = createEnvironment()

  should("report a condition on a member or named argument sharing a content lambda parameter name") {
    // language=kotlin
    val code = composeSnippet(
      """
      class PagerState(val page: Int)

      @Composable fun Pager(content: @Composable (Int) -> Unit) {}

      fun check(page: Int): Boolean = page > 0

      @Composable
      fun Test(pagerState: PagerState) {
        Pager { page ->
          if (pagerState.page == 0) Text(text = "first")
        }
        Pager { page ->
          if (check(page = 1)) Text(text = "checked")
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.map { it.message } shouldBe listOf(
      "Condition could be lifted out of \"Pager\"",
      "Condition could be lifted out of \"Pager\"",
    )
    findings[0].shouldStartAt(code, "if (pagerState.page")
    findings[1].shouldStartAt(code, "if (check(page = 1))")
  }

  should("not report a condition on a local function of a content lambda") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test() {
        Column {
          fun ok() = true
          if (ok()) Text(text = "ok")
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

      val findings = createRule().lintWithContext(environment, code)

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

      val findings = createRule().lintWithContext(environment, code)

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

      val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

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

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("report when 'else if' branch has no composable calls") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(a: Boolean, b: Boolean) {
        Column {
          if (a) {
            Text("hello")
          } else if (b) {
            println("no ui")
          }
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("report when then-branch only calls a composable slot lambda") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(visible: Boolean, icon: @Composable () -> Unit) {
        Column {
          if (visible) {
            icon()
          }
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.first().message shouldContain "Column"
  }

  should("not report when the layout also calls a composable slot outside the condition") {
    val statements = listOf(
      "trailing?.invoke()",
      "icon.invoke()",
      "icon()",
      "trailing?.let { it() }",
      "items.forEach { Text(text = it) }",
      "repeat(2) { Text(text = \"b\") }",
      "when (x) { 1 -> Text(text = \"b\") }",
    )
    val findingCounts = statements.flatMap { statement ->
      val condition = "if (visible) Text(text = \"a\")"
      listOf("$statement after" to "$condition\n$statement", "$statement before" to "$statement\n$condition")
    }.associate { (case, body) ->
      // language=kotlin
      val code = composeSnippet(
        """
        @Composable
        fun Test(
          visible: Boolean,
          icon: @Composable () -> Unit,
          trailing: (@Composable () -> Unit)? = null,
          items: List<String> = emptyList(),
          x: Int = 0,
        ) {
          Row {
            $body
          }
        }
        """,
      )
      case to createRule().lintWithContext(environment, code).size
    }

    findingCounts shouldBe findingCounts.mapValues { 0 }
  }

  should("report when then-branch only calls a nullable composable slot") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(visible: Boolean, trailing: (@Composable () -> Unit)? = null) {
        Row {
          if (visible) {
            trailing?.invoke()
          }
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.first().message shouldContain "Row"
    findings.first().shouldStartAt(code, "if (visible)")
  }

  should("report when a declaration calling a composable is next to the condition") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable fun <T> remember(calculation: () -> T): T = calculation()

      @Composable
      fun Test(visible: Boolean) {
        Row {
          val x = remember { 0 }
          if (visible) Text(text = "a")
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.single().message shouldBe "Condition could be lifted out of \"Row\""
    findings.single().shouldStartAt(code, "if (visible)")
  }

  should("not report when ignored 'modifier' argument is passed positionally") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(visible: Boolean) {
        Column(Modifier.padding(4.dp)) {
          if (visible) {
            Text("hello")
          }
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("not report when 'content' lambda is not composable") {
    // language=kotlin
    val code = composeSnippet(
      """
      interface LazyListScope {
        fun item(content: @Composable () -> Unit)
      }

      @Composable
      fun LazyColumn(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {}

      @Composable
      fun Test(visible: Boolean) {
        LazyColumn {
          if (visible) {
            item { Text("hello") }
          }
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("report when content lambda is passed positionally before a trailing parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Card(content: @Composable () -> Unit, onClick: () -> Unit) {}

      @Composable
      fun Test(visible: Boolean) {
        Card({
          if (visible) {
            Text("hello")
          }
        }) {}
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
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

    val findings = createRule().lintWithContext(environment, code)

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
    ).lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("report calls with the default-ignored 'modifier' argument when the ignore list is emptied") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test() {
        val printValue = false
        Column(modifier = Modifier) {
          if (printValue) Text(text = "3")
        }
      }
      """,
    )

    val findings = createRule(
      TestConfig("ignoreCallsWithArgumentNames" to emptyList<String>()),
    ).lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("resolve layouts imported under an alias") {
    // language=kotlin
    val code = composeSnippet(
      imports = "import ru.kode.detekt.rule.Column as Stack",
      code =
      """
      @Composable
      fun Test() {
        val printValue = false
        Stack {
          if (printValue) Text(text = "3")
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.single().message shouldBe "Condition could be lifted out of \"Stack\""
  }

  should("report content lambdas with a receiver or generic parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
      interface ItemScope
      @Composable fun Scoped(content: @Composable ItemScope.() -> Unit) {}
      @Composable fun <T> Item(item: T, content: @Composable (T) -> Unit) {}

      @Composable
      fun Test() {
        val printValue = false
        Scoped {
          if (printValue) Text(text = "scoped")
        }
        Item(1) { value ->
          if (printValue) Text(text = "generic")
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.map { it.message } shouldBe listOf(
      "Condition could be lifted out of \"Scoped\"",
      "Condition could be lifted out of \"Item\"",
    )
  }

  should("report conditions inside non-composable lambdas of a composable function") {
    // language=kotlin
    val code = composeSnippet(
      """
      fun remember(block: () -> Unit) {}

      @Composable
      fun Test() {
        val printValue = false
        remember {
          Column {
            if (printValue) Text(text = "3")
          }
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("report in an expression-bodied composable") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(printValue: Boolean) = Column {
        if (printValue) Text(text = "3")
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
  }

  should("not report in an expression-bodied composable when else-branch has composable calls") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(printValue: Boolean) = Column {
        if (printValue) Text(text = "3") else Text(text = "4")
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("ignore non-composable functions") {
    // language=kotlin
    val code = composeSnippet(
      """
      fun Test() {
        val printValue = false
        Column {
          if (printValue) Text(text = "3")
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  // https://github.com/appKODE/detekt-rules-compose/issues/31
  should("not report a condition on a local val of a content lambda with a parameter") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable fun HorizontalPager(pageCount: Int, content: @Composable (Int) -> Unit) {}

      @Composable
      fun Test(entries: List<String>) {
        HorizontalPager(pageCount = entries.size) { pageIndex ->
          val branchEntry = entries.elementAtOrNull(pageIndex)
          if (branchEntry != null) {
            Text(text = branchEntry)
          }
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("not report a condition on a content lambda parameter or its implicit it") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable fun Pager(content: @Composable (Int) -> Unit) {}
      @Composable fun Toggle(content: @Composable (Boolean) -> Unit) {}

      @Composable
      fun Test() {
        Pager { page ->
          if (page == 0) Text(text = "first")
        }
        Pager {
          if (it == 0) Text(text = "first")
        }
        Toggle { visible ->
          if (visible) Text(text = "shown")
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("report a condition whose own lambda uses it inside a content lambda") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable
      fun Test(items: List<Int>) {
        Column {
          if (items.any { it > 0 }) Text(text = "positive")
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.single().message shouldBe "Condition could be lifted out of \"Column\""
    findings.single().shouldStartAt(code, "if (items.any")
  }

  should("report a condition on a local val declared outside the content lambda") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable fun Pager(content: @Composable (Int) -> Unit) {}

      @Composable
      fun Test(entries: List<String>) {
        val first = entries.firstOrNull()
        Pager { page ->
          if (first != null) Text(text = first)
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.single().message shouldBe "Condition could be lifted out of \"Pager\""
    findings.single().shouldStartAt(code, "if (first != null)")
  }

  should("report a condition on the it of a lambda enclosing the layout call") {
    // language=kotlin
    val code = composeSnippet(
      """
      data class Item(val selected: Boolean)

      @Composable
      fun Test(items: List<Item>, flags: List<Boolean>) {
        items.forEach {
          Row {
            if (it.selected) Text(text = "row")
          }
        }
        flags.forEach {
          Column {
            if (flags.any { flag -> it == flag }) Text(text = "column")
          }
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.map { it.message } shouldBe listOf(
      "Condition could be lifted out of \"Row\"",
      "Condition could be lifted out of \"Column\"",
    )
    findings[0].shouldStartAt(code, "if (it.selected)")
    findings[1].shouldStartAt(code, "if (flags.any")
  }

  should("not report a condition on the implicit it of a content lambda with a receiver") {
    // language=kotlin
    val code = composeSnippet(
      """
      interface AnimatedContentScope

      @Composable
      fun <S> AnimatedContent(targetState: S, content: @Composable AnimatedContentScope.(S) -> Unit) {}

      @Composable
      fun Test(visible: Boolean) {
        AnimatedContent(visible) {
          if (it) Text(text = "shown")
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }

  should("report a condition whose own lambda uses it inside a content lambda with an implicit it") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Composable fun Pager(content: @Composable (Int) -> Unit) {}

      @Composable
      fun Test(items: List<Int>) {
        Pager {
          if (items.any { it > 0 }) Text(text = "positive")
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings shouldHaveSize 1
    findings.single().message shouldBe "Condition could be lifted out of \"Pager\""
    findings.single().shouldStartAt(code, "if (items.any")
  }

  should("honour @Suppress on the function") {
    // language=kotlin
    val code = composeSnippet(
      """
      @Suppress("ConditionCouldBeLifted")
      @Composable
      fun Test() {
        val printValue = false
        Column {
          if (printValue) Text(text = "3")
        }
      }
      """,
    )

    val findings = createRule().lintWithContext(environment, code)

    findings.shouldBeEmpty()
  }
})

private fun Exhaustive.Companion.composeLayoutName() = listOf("Box", "Row", "Column").exhaustive()
private fun createRule(config: Config = Config.empty) = ConditionCouldBeLifted(
  composableAnnotationClassPackage = "ru.kode.detekt.rule",
  config = config,
)
