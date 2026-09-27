package ru.kode.detekt.rule.compose

import dev.detekt.test.utils.createEnvironment
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import ru.kode.detekt.rule.compose.shared.ComposeSemantic
import ru.kode.detekt.rule.compose.snippet.composeSnippet

private const val TEST_COMPOSE_PACKAGE = "ru.kode.detekt.rule"

/**
 * One set of fixtures and expected answers for every [ComposeSemantic] method, so the binding-context (detekt1)
 * and Analysis API (detekt2) implementations are verified to agree.
 */
class ComposeSemanticContractTest : ShouldSpec({
  val environment = createEnvironment()

  fun <T> answers(ask: ComposeSemantic.(KtCallExpression) -> T): Map<String, T> =
    withComposeSemantic(environment, FIXTURE) { semantic, file ->
      file.probeCalls().associate { it.text to semantic.ask(it) }
    }

  should("detect composable calls, including slot lambdas and import aliases") {
    answers { isComposableCall(it, TEST_COMPOSE_PACKAGE) } shouldBe mapOf(
      "Text(\"text\")" to true,
      "Label(\"alias\")" to true,
      "Column(Modifier) {}" to true,
      "Row(verticalAlignment = Alignment.CenterVertically) {}" to true,
      "Box(content = {})" to true,
      "Card({}) { callback() }" to true,
      "Button(onClick = {}) {}" to true,
      "ScopedRow {}" to true,
      "Aliased {}" to true,
      "Optional()" to true,
      "NotComposable {}" to false,
      "slot()" to true,
      "callback()" to false,
      "log(\"x\")" to false,
      "items()" to false,
      "items(1, 2)" to false,
    )
  }

  should("not detect composable calls for another annotation package") {
    answers { isComposableCall(it, "androidx.compose.runtime") }.values.toSet() shouldBe setOf(false)
  }

  should("list parameters that receive explicit arguments") {
    answers { explicitArgumentParameterNames(it) } shouldBe mapOf(
      "Text(\"text\")" to setOf("text"),
      "Label(\"alias\")" to setOf("text"),
      "Column(Modifier) {}" to setOf("modifier", "content"),
      "Row(verticalAlignment = Alignment.CenterVertically) {}" to setOf("verticalAlignment", "content"),
      "Box(content = {})" to setOf("content"),
      "Card({}) { callback() }" to setOf("content", "onClick"),
      "Button(onClick = {}) {}" to setOf("onClick", "content"),
      "ScopedRow {}" to setOf("content"),
      "Aliased {}" to setOf("content"),
      "Optional()" to emptySet(),
      "NotComposable {}" to setOf("content"),
      "slot()" to emptySet(),
      "callback()" to emptySet(),
      "log(\"x\")" to setOf("message"),
      "items()" to emptySet(),
      "items(1, 2)" to setOf("elements"),
    )
  }

  should("find the first composable lambda parameter") {
    answers { firstComposableLambdaParameterName(it, TEST_COMPOSE_PACKAGE) } shouldBe mapOf(
      "Text(\"text\")" to null,
      "Label(\"alias\")" to null,
      "Column(Modifier) {}" to "content",
      "Row(verticalAlignment = Alignment.CenterVertically) {}" to "content",
      "Box(content = {})" to "content",
      "Card({}) { callback() }" to "content",
      "Button(onClick = {}) {}" to "content",
      "ScopedRow {}" to "content",
      "Aliased {}" to "content",
      "Optional()" to "content",
      "NotComposable {}" to null,
      "slot()" to null,
      "callback()" to null,
      "log(\"x\")" to null,
      "items()" to null,
      "items(1, 2)" to null,
    )
  }

  should("return the argument bound to a parameter, whether named, positional or trailing") {
    answers { argumentForParameterNamed(it, "content")?.text } shouldBe mapOf(
      "Text(\"text\")" to null,
      "Label(\"alias\")" to null,
      "Column(Modifier) {}" to "{}",
      "Row(verticalAlignment = Alignment.CenterVertically) {}" to "{}",
      "Box(content = {})" to "{}",
      "Card({}) { callback() }" to "{}",
      "Button(onClick = {}) {}" to "{}",
      "ScopedRow {}" to "{}",
      "Aliased {}" to "{}",
      "Optional()" to null,
      "NotComposable {}" to "{}",
      "slot()" to null,
      "callback()" to null,
      "log(\"x\")" to null,
      "items()" to null,
      "items(1, 2)" to null,
    )
    answers { argumentForParameterNamed(it, "onClick")?.text }["Card({}) { callback() }"] shouldBe "{ callback() }"
  }

  should("detect callee parameters of a given type, including nullable ones") {
    answers { callHasParameterOfType(it, "$TEST_COMPOSE_PACKAGE.Modifier") } shouldBe mapOf(
      "Text(\"text\")" to true,
      "Label(\"alias\")" to false,
      "Column(Modifier) {}" to true,
      "Row(verticalAlignment = Alignment.CenterVertically) {}" to true,
      "Box(content = {})" to true,
      "Card({}) { callback() }" to false,
      "Button(onClick = {}) {}" to false,
      "ScopedRow {}" to false,
      "Aliased {}" to false,
      "Optional()" to true,
      "NotComposable {}" to false,
      "slot()" to false,
      "callback()" to false,
      "log(\"x\")" to false,
      "items()" to false,
      "items(1, 2)" to false,
    )
  }

  should("detect sealed receiver types and direct sealed supertypes") {
    val answers = withComposeSemantic(environment, FIXTURE) { semantic, file ->
      file.collectDescendantsOfType<KtDotQualifiedExpression>()
        .filter { it.selectorExpression?.text == "id" }
        .associate { it.receiverExpression.text to semantic.receiverHasSealedTypeOrSupertype(it.receiverExpression) }
    }

    answers shouldBe mapOf(
      "sealedBase" to true,
      "sealedChild" to true,
      "sealedGrandchild" to false,
      "sealedInterfaceChild" to true,
      "plain" to false,
      "enumEntry" to false,
      "smartCast" to true,
    )
  }
})

private fun KtFile.probeCalls(): List<KtCallExpression> = declarations.filterIsInstance<KtNamedFunction>()
  .single { it.name == "Probe" }
  .bodyBlockExpression!!
  .statements
  .filterIsInstance<KtCallExpression>()

// language=kotlin
private val FIXTURE = composeSnippet(
  imports = "import ru.kode.detekt.rule.Caption as Label",
  code =
  """
    sealed class Base { abstract val id: Int }
    data class Child(override val id: Int) : Base()
    open class Middle(override val id: Int) : Base()
    class Grandchild(id: Int) : Middle(id)
    sealed interface Event { val id: Int }
    data class Click(override val id: Int) : Event
    data class Plain(val id: Int)
    enum class Kind { A; val id: Int get() = 0 }

    interface RowScope
    typealias Slot = @Composable () -> Unit

    @Composable fun Caption(text: String) {}
    @Composable fun Card(content: @Composable () -> Unit, onClick: () -> Unit) {}
    @Composable fun ScopedRow(content: @Composable RowScope.() -> Unit) {}
    @Composable fun Aliased(content: Slot) {}
    @Composable fun Optional(modifier: Modifier? = null, content: (@Composable () -> Unit)? = null) {}
    fun NotComposable(content: () -> Unit) {}
    fun log(message: String) {}
    fun items(vararg elements: Int) {}

    @Composable
    fun Probe(slot: @Composable () -> Unit, callback: () -> Unit) {
      Text("text")
      Label("alias")
      Column(Modifier) {}
      Row(verticalAlignment = Alignment.CenterVertically) {}
      Box(content = {})
      Card({}) { callback() }
      Button(onClick = {}) {}
      ScopedRow {}
      Aliased {}
      Optional()
      NotComposable {}
      slot()
      callback()
      log("x")
      items()
      items(1, 2)
    }

    fun receivers(
      sealedBase: Base,
      sealedChild: Child,
      sealedGrandchild: Grandchild,
      sealedInterfaceChild: Click,
      plain: Plain,
      enumEntry: Kind,
      smartCast: Any,
    ) {
      sealedBase.id
      sealedChild.id
      sealedGrandchild.id
      sealedInterfaceChild.id
      plain.id
      enumEntry.id
      if (smartCast is Child) smartCast.id
    }
  """,
)
