package ru.kode.detekt.rule.compose

import dev.detekt.test.utils.createEnvironment
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import ru.kode.detekt.rule.compose.shared.ComposableLambdaParameter
import ru.kode.detekt.rule.compose.shared.ComposeSemantic
import ru.kode.detekt.rule.compose.shared.ResolvedClass
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

  should("list the composable lambda parameters, inlined ones marked") {
    val notInlined = listOf(ComposableLambdaParameter("content", isInlined = false))
    answers { composableLambdaParameters(it, TEST_COMPOSE_PACKAGE) } shouldBe mapOf(
      "Text(\"text\")" to emptyList(),
      "Label(\"alias\")" to emptyList(),
      "Column(Modifier) {}" to notInlined,
      "Row(verticalAlignment = Alignment.CenterVertically) {}" to notInlined,
      "Box(content = {})" to notInlined,
      "Card({}) { callback() }" to notInlined,
      "Button(onClick = {}) {}" to notInlined,
      "ScopedRow {}" to notInlined,
      "Aliased {}" to notInlined,
      "Optional()" to notInlined,
      "NotComposable {}" to emptyList(),
      "slot()" to emptyList(),
      "callback()" to emptyList(),
      "log(\"x\")" to emptyList(),
      "items()" to emptyList(),
      "items(1, 2)" to emptyList(),
    )

    val slots = withComposeSemantic(environment, FIXTURE) { semantic, file ->
      file.declarations.filterIsInstance<KtNamedFunction>()
        .single { it.name == "Slots" }
        .collectDescendantsOfType<KtCallExpression>()
        .associate { call ->
          call.calleeExpression!!.text to
            semantic.composableLambdaParameters(call, TEST_COMPOSE_PACKAGE).map { it.name to it.isInlined }
        }
    }
    slots shouldBe mapOf(
      "Scaffold" to listOf("topBar" to false, "content" to false),
      // a `noinline`, a `crossinline` and a nullable parameter of an inline function are not inlined into the caller
      "InlineLayout" to listOf("content" to true, "kept" to false, "optional" to false, "crossed" to false),
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

  should("detect lambdas with an implicit it parameter, including stdlib calls") {
    val answers = withComposeSemantic(environment, FIXTURE) { semantic, file ->
      file.declarations.filterIsInstance<KtNamedFunction>()
        .single { it.name == "lambdas" }
        .collectDescendantsOfType<KtLambdaExpression>()
        .associate { lambda ->
          lambda.parentsWithSelf.first { it.parent is KtBlockExpression }.text to semantic.lambdaHasImplicitIt(lambda)
        }
    }

    answers shouldBe mapOf(
      "Pager { slot() }" to true,
      "Pager { page -> slot() }" to false,
      "Pager { -> slot() }" to false,
      "ScopedRow { slot() }" to false,
      "Box { slot() }" to false,
      "items.forEach { log(\"x\") }" to true,
      "items.forEach { item -> log(\"x\") }" to false,
      "run { log(\"x\") }" to false,
    )
  }

  should("detect references to const vals, enum entries and objects") {
    val answers = withComposeSemantic(environment, FIXTURE) { semantic, file ->
      file.declarations.filterIsInstance<KtNamedFunction>()
        .single { it.name == "constants" }
        .collectDescendantsOfType<KtCallExpression>()
        .map { it.valueArguments.single().getArgumentExpression()!! }
        .associate { argument ->
          val reference = (argument as? KtDotQualifiedExpression)?.selectorExpression ?: argument
          argument.text to semantic.isConstantReference(reference as KtNameReferenceExpression)
        }
    }

    answers shouldBe mapOf(
      "LIMIT" to true,
      "Holder.MAX" to true,
      "Holder.Companion" to true,
      // Kotlin constants only: a Java `static final` field is not one, a Java enum entry is
      "Integer.MAX_VALUE" to false,
      "java.util.concurrent.TimeUnit.SECONDS" to true,
      "Kind.A" to true,
      "Back" to true,
      "Intent.Close" to true,
      "notConst" to false,
      "local" to false,
      "value" to false,
      "Kind.A.ordinal" to false,
    )
  }

  should("tell whether a reference targets a parameter, null when it is unresolved") {
    val answers = withComposeSemantic(environment, FIXTURE) { semantic, file ->
      val function = file.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "targets" }
      function.collectDescendantsOfType<KtNameReferenceExpression>()
        .filter { it.getReferencedName() in setOf("onEvent", "missing") }
        .associate { reference ->
          reference.parentsWithSelf.first { it.parent is KtBlockExpression }.text to
            semantic.referenceTargetsParameter(reference, function.valueParameters.single())
        }
    }

    answers shouldBe mapOf(
      "onEvent(1)" to true,
      "onEvent(1, 2)" to false,
      "onEvent.invoke(3)" to true,
      "onEvent(4)" to false,
      "take(onEvent)" to true,
      "take(missing)" to null,
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

  should("detect expressions of a value class type") {
    val answers = withComposeSemantic(environment, FIXTURE) { semantic, file ->
      file.collectDescendantsOfType<KtQualifiedExpression>()
        .filter { it.selectorExpression?.text == "value" }
        .associate { it.receiverExpression.text to semantic.isValueClassInstance(it.receiverExpression) }
    }

    answers shouldBe mapOf(
      "url" to true,
      "nullableUrl" to true,
      "aliasUrl" to true,
      "smartUrl" to true,
      "Url(\"a\")" to true,
      "wrapper" to false,
      "unknownUrl" to false,
    )
  }

  should("detect a value class type of a smart cast argument, but not the value classes of the kotlin package") {
    val answers = withComposeSemantic(environment, FIXTURE) { semantic, file ->
      file.declarations.filterIsInstance<KtNamedFunction>()
        .single { it.name == "smartCasts" }
        .collectDescendantsOfType<KtCallExpression>()
        .map { it.valueArguments.single().getArgumentExpression()!! }
        .associate { it.text to semantic.isValueClassInstance(it) }
    }

    answers shouldBe mapOf(
      "smartArgument" to true,
      "smartNullableArgument" to true,
      "notCastArgument" to false,
      "unsigned" to false,
      "result" to false,
      "duration" to false,
    )
  }

  should("detect expressions of a class or its subtypes, through smart casts and type aliases") {
    val answers = withComposeSemantic(environment, FIXTURE) { semantic, file ->
      file.collectDescendantsOfType<KtQualifiedExpression>()
        .filter { it.selectorExpression?.text == "payload" }
        .associate {
          it.receiverExpression.text to semantic.isInstanceOf(it.receiverExpression, "$TEST_COMPOSE_PACKAGE.Holder1")
        }
    }

    answers shouldBe mapOf(
      "holder" to true,
      "nullableHolder" to true,
      "aliasHolder" to true,
      "directChild" to true,
      "indirectChild" to true,
      "smartHolder" to true,
      "HolderGrandchild()" to true,
      "namesake" to false,
      "unknownHolder" to false,
    )
  }

  should("find the class which declares the callee, null for a function which is not a member") {
    val answers = withComposeSemantic(environment, FIXTURE) { semantic, file ->
      file.declarations.filterIsInstance<KtNamedFunction>()
        .single { it.name == "callees" }
        .collectDescendantsOfType<KtCallExpression>()
        .filter { it.calleeExpression?.text in setOf("data", "tagged") }
        .associate { it.valueArguments.single().text to semantic.calleeDeclaringClass(it) }
    }

    answers shouldBe mapOf(
      "1" to "$TEST_COMPOSE_PACKAGE.Request",
      "2" to "$TEST_COMPOSE_PACKAGE.Request.Builder",
      "3" to "$TEST_COMPOSE_PACKAGE.Request",
      "4" to "$TEST_COMPOSE_PACKAGE.Request",
      "5" to null,
      "6" to "$TEST_COMPOSE_PACKAGE.Request.Builder",
      "7" to null,
      "8" to null,
    )
  }

  should("find the declared parameter an argument is passed to, without one for a compiled or a generated callee") {
    val answers = withComposeSemantic(environment, FIXTURE) { semantic, file ->
      file.declarations.filterIsInstance<KtNamedFunction>()
        .filter { it.name in setOf("parameters", "inheritedParameters") }
        .flatMap { it.collectDescendantsOfType<KtCallExpression>() }
        .flatMap { call -> call.valueArguments.map { call to it.getArgumentExpression()!! } }
        .associate { (call, argument) ->
          // the path of the declaration, a constructor has the name of its class
          argument.text to semantic.parameterOfArgument(call, argument)?.let { parameter ->
            parameter.parentsWithSelf.filterIsInstance<KtNamedDeclaration>().mapNotNull { it.name }.toList()
              .reversed().joinToString(".")
          }
        }
    }

    answers shouldBe mapOf(
      "1" to "painted.shade",
      "2" to "painted.shade",
      "3" to "painted.shade",
      "31" to "painted.extras",
      "32" to "painted.extras",
      "{ 4 }" to "painted.block",
      "(5)" to "painted.shade",
      "6" to "Painted.Painted.shade",
      "7" to "Painted.member.shade",
      "8" to "Painted.Nested.Nested.shade",
      "9" to "parameters.local.shade",
      "10" to "parameters.Local.Local.shade",
      "11" to null,
      "12" to null,
      "13" to null,
      "14" to null,
      "15" to null,
      "16" to "PaintedBase.inherited.shade",
      "17" to "PaintedGeneric.generic.shade",
      "18" to "Paintable.delegated.shade",
      "19" to "Painted.Painted.shade",
      "\"20\"" to null,
      "21" to "PaintedOverride.inherited.shade",
    )
  }

  should("tell whether a reference is on the implicit receiver of a lambda") {
    val answers = withComposeSemantic(environment, FIXTURE) { semantic, file ->
      val function = file.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "scopes" }
      val (outer, inner) = function.collectDescendantsOfType<KtFunctionLiteral>().sortedByDescending { it.textLength }
      function.collectDescendantsOfType<KtNameReferenceExpression>()
        .filter { it.parentsWithSelf.any { parent -> parent is KtLambdaExpression } }
        .filterNot { it.getReferencedName() in setOf("take", "scoped", "other", "with", "run") }
        .associate { reference ->
          reference.parentsWithSelf.first { it.parent is KtBlockExpression }.text to
            listOf(outer, inner).map { semantic.isOnImplicitReceiverOf(reference, it) }
        }
    }

    val onOuter = listOf(true, false)
    val onInner = listOf(false, true)
    val onNone = listOf(false, false)
    answers shouldBe mapOf(
      "take(title)" to onOuter,
      "take(inherited)" to onOuter,
      "onClick()" to onOuter,
      "member()" to onOuter,
      "extension()" to onOuter,
      "take(extended)" to onOuter,
      // a member extension has the receiver of the lambda as its dispatch receiver
      "\"x\".decorate()" to onOuter,
      "take(outer)" to onNone,
      "take(top)" to onNone,
      "take(local)" to onNone,
      "take(missing)" to onNone,
      "log(\"x\")" to onNone,
      // the receiver of the nearest lambda, not any receiver of the class
      "take(subtitle)" to onInner,
      "scoped.member()" to onNone,
    )
  }

  should("resolve the class of a type reference, through nullability and type aliases") {
    val answers = withComposeSemantic(environment, FIXTURE) { semantic, file ->
      file.declarations.filterIsInstance<KtNamedFunction>()
        .single { it.name == "types" }
        .valueParameters
        .associate { it.name to semantic.resolveClass(it.typeReference!!) } +
        file.collectDescendantsOfType<KtTypeReference> { it.text == "Plain" && it.parent is KtTypeProjection }
          .associate { "argument" to semantic.resolveClass(it) }
    }

    val library = setOf("list", "function", "text", "javaFile")
    fun resolved(name: String, isData: Boolean, properties: Set<String>, components: List<String> = emptyList()) =
      ResolvedClass("$TEST_COMPOSE_PACKAGE.$name", TEST_COMPOSE_PACKAGE, isData, emptySet(), properties, components)
    val plain = resolved("Plain", isData = true, setOf("id"), listOf("id"))
    answers.filterKeys { it !in library } shouldBe mapOf(
      "plain" to plain,
      "nullable" to plain,
      "alias" to plain,
      "marked" to resolved("Marked", isData = true, setOf("id", "name", "label"), listOf("id", "name"))
        .copy(annotationNames = setOf("Immutable")),
      "child" to resolved("Child", isData = true, setOf("id"), listOf("id")),
      // inherited properties are listed, private, protected and extension ones are not
      "grandchild" to resolved("Grandchild", isData = false, setOf("id", "visible")),
      // the type reference of a vararg parameter names the element, not the array
      "rest" to plain,
      "kind" to resolved("Kind", isData = false, setOf("id", "name", "ordinal")),
      "nested" to resolved("Holder.Companion", isData = false, setOf("MAX")),
      // a type argument is resolved like the type of the parameter
      "argument" to plain,
      "inferred" to resolved("Inferred", isData = false, emptySet())
        .copy(annotationNames = setOf("StabilityInferred"), stabilityInferredParameters = 2, typeParameterCount = 1),
      "generic" to null,
      "missing" to null,
    )
    // library classes: only what does not depend on how the engine loads them
    answers.filterKeys { it in library }
      .mapValues { it.value?.copy(annotationNames = emptySet(), propertyNames = emptySet()) } shouldBe mapOf(
      "list" to
        ResolvedClass("kotlin.collections.List", "kotlin.collections", false, emptySet(), emptySet(), emptyList())
          .copy(typeParameterCount = 1),
      "function" to ResolvedClass("kotlin.Function0", "kotlin", false, emptySet(), emptySet(), emptyList())
        .copy(typeParameterCount = 1),
      "text" to ResolvedClass("kotlin.String", "kotlin", false, emptySet(), emptySet(), emptyList()),
      "javaFile" to ResolvedClass("java.io.File", "java.io", false, emptySet(), emptySet(), emptyList()),
    )
    // the fields and the getters of a Java class are not properties
    answers["javaFile"]?.propertyNames shouldBe emptySet()
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
    open class Middle(override val id: Int) : Base() { private val hidden: Int = 0 }
    class Grandchild(id: Int) : Middle(id) {
      private val own: Int = 0
      protected val guarded: Int = 0
      internal val visible: Int = 0
      val String.extended: Int get() = 0
    }
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
    @Composable fun Pager(content: @Composable (Int) -> Unit) {}

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

    @Composable
    fun Scaffold(topBar: @Composable () -> Unit, onClick: () -> Unit = {}, content: @Composable () -> Unit) {}
    @Composable inline fun InlineLayout(
      content: @Composable () -> Unit,
      noinline kept: @Composable () -> Unit = {},
      optional: (@Composable () -> Unit)? = null,
      crossinline crossed: @Composable () -> Unit = {},
    ) {}

    @Composable
    fun Slots() {
      Scaffold(topBar = {}) {}
      InlineLayout(content = {})
    }

    @Composable
    fun lambdas(slot: @Composable () -> Unit, items: List<Int>) {
      Pager { slot() }
      Pager { page -> slot() }
      Pager { -> slot() }
      ScopedRow { slot() }
      Box { slot() }
      items.forEach { log("x") }
      items.forEach { item -> log("x") }
      run { log("x") }
    }

    const val LIMIT = 1
    val notConst = 2
    class Holder { companion object { const val MAX = 3 } }
    object Back
    sealed class Intent { data object Close : Intent() }
    fun take(value: Any?) {}

    fun constants(value: Int) {
      val local = 1
      take(LIMIT)
      take(Holder.MAX)
      take(Holder.Companion)
      take(Integer.MAX_VALUE)
      take(java.util.concurrent.TimeUnit.SECONDS)
      take(Kind.A)
      take(Back)
      take(Intent.Close)
      take(notConst)
      take(local)
      take(value)
      take(Kind.A.ordinal)
    }

    fun onEvent(first: Int, second: Int) {}
    fun wrap(block: ((Int) -> Unit) -> Unit) {}

    fun targets(onEvent: (Int) -> Unit) {
      onEvent(1)
      onEvent(1, 2)
      onEvent.invoke(3)
      wrap { onEvent -> onEvent(4) }
      take(onEvent)
      take(missing)
    }

    annotation class Immutable
    @Immutable data class Marked(val id: Int, val name: String) { val label: String get() = name }
    typealias PlainAlias = Plain
    annotation class StabilityInferred(val parameters: Int)
    @StabilityInferred(parameters = 2) class Inferred<T>

    fun <T> types(
      plain: Plain,
      nullable: Plain?,
      alias: PlainAlias,
      marked: Marked,
      child: Child,
      grandchild: Grandchild,
      kind: Kind,
      nested: Holder.Companion,
      inferred: Inferred<Plain>,
      generic: T,
      missing: Missing,
      list: List<Plain>,
      function: () -> Unit,
      text: String,
      javaFile: java.io.File,
      vararg rest: Plain,
    ) {}

    @JvmInline
    value class Url(val value: String)
    typealias UrlAlias = Url
    class Wrapper(val value: String)

    fun values(url: Url, nullableUrl: Url?, aliasUrl: UrlAlias, smartUrl: Any, wrapper: Wrapper, unknownUrl: Missing) {
      url.value
      nullableUrl?.value
      aliasUrl.value
      if (smartUrl is Url) smartUrl.value
      Url("a").value
      wrapper.value
      unknownUrl.value
    }

    fun smartCasts(
      smartArgument: Any,
      smartNullableArgument: Any?,
      notCastArgument: Any,
      unsigned: UInt,
      result: Result<String>,
      duration: kotlin.time.Duration,
    ) {
      take(unsigned)
      take(result)
      take(duration)
      if (smartArgument is Url) take(smartArgument)
      if (smartNullableArgument is Url?) take(smartNullableArgument)
      if (smartArgument is Url) take(notCastArgument)
    }

    interface Holder1<T> { val payload: T }
    open class HolderChild : Holder1<Int> { override val payload: Int = 0 }
    class HolderGrandchild : HolderChild()
    typealias HolderAlias = Holder1<String>
    class Namesake { class Holder1(val payload: Int) }

    fun instances(
      holder: Holder1<Int>,
      nullableHolder: Holder1<Int>?,
      aliasHolder: HolderAlias,
      directChild: HolderChild,
      indirectChild: HolderGrandchild,
      smartHolder: Any,
      namesake: Namesake.Holder1,
      unknownHolder: Missing,
    ) {
      holder.payload
      nullableHolder?.payload
      aliasHolder.payload
      directChild.payload
      indirectChild.payload
      if (smartHolder is HolderChild) smartHolder.payload
      HolderGrandchild().payload
      namesake.payload
      unknownHolder.payload
    }

    open class Request<T> {
      fun data(data: Any?) = this
      fun tagged(tag: T) = this
      class Builder { fun data(data: Any?) = this }
    }
    class ChildRequest : Request<Int>()
    fun data(data: Any?) {}
    fun Plain.tagged(tag: Int) {}

    fun callees(request: Request<Int>, builder: Request.Builder, child: ChildRequest, plain: Plain) {
      request.data(1)
      builder.data(2)
      child.data(3)
      child.tagged(4)
      data(5)
      builder.apply { data(6) }
      plain.tagged(7)
      missing.data(8)
    }

    class Painted(val shade: Int = 0) {
      fun member(shade: Int = 0) {}
      class Nested(val shade: Int = 0)
    }
    fun painted(shade: Int = 0, vararg extras: Int, block: () -> Unit = {}) {}

    data class Styled(val shade: Int = 0)
    open class PaintedBase { open fun inherited(shade: Int = 0) {} }
    class PaintedChild : PaintedBase()
    class PaintedOverride : PaintedBase() { override fun inherited(shade: Int) {} }
    open class PaintedGeneric<T> { fun generic(item: T? = null, shade: Int = 0) {} }
    class PaintedGenericChild : PaintedGeneric<String>()
    interface Paintable { fun delegated(shade: Int = 0) }
    class PaintedDelegate(paintable: Paintable) : Paintable by paintable
    typealias PaintedAlias = Painted

    fun inheritedParameters(
      styled: Styled,
      child: PaintedChild,
      generic: PaintedGenericChild,
      delegate: PaintedDelegate,
      override: PaintedOverride,
    ) {
      styled.copy(14)
      styled.copy(shade = 15)
      child.inherited(16)
      generic.generic(shade = 17)
      delegate.delegated(18)
      PaintedAlias(19)
      painted("20")
      override.inherited(21)
    }

    fun parameters(instance: Painted) {
      fun local(shade: Int = 0) {}
      class Local(val shade: Int = 0)
      painted(1)
      painted(shade = 2)
      painted(3, 31, 32) { 4 }
      painted((5))
      Painted(6)
      instance.member(7)
      Painted.Nested(8)
      local(9)
      Local(10)
      listOf(11)
      Pair(first = 12, second = 12)
      missing(13)
    }

    open class ScopedBase { val inherited: Int = 0 }
    class Scoped(val title: String, val subtitle: String, val onClick: () -> Unit) : ScopedBase() {
      fun member() {}
      fun String.decorate() {}
    }
    fun Scoped.extension() {}
    val Scoped.extended: Int get() = 0
    val top = 1

    fun scopes(scoped: Scoped, other: Scoped, outer: Int) {
      with(scoped) {
        with(other) {
          take(subtitle)
          scoped.member()
        }
        "x".decorate()
        val local = 1
        take(title)
        take(inherited)
        onClick()
        member()
        extension()
        take(extended)
        take(outer)
        take(top)
        take(local)
        take(missing)
        log("x")
      }
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
