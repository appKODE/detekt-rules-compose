package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic
import ru.kode.detekt.rule.compose.shared.ResolvedClass
import ru.kode.detekt.rule.compose.shared.hasAnnotationNamed

data class NonImmutableDataClassParameterOptions(
  val ignoredPackageSegments: List<String> = emptyList(),
  val stableTypes: List<StableTypePattern> = emptyList(),
)

/** A compiled line of the `stableTypePatterns` option, see [stableTypePatterns]. */
class StableTypePattern internal constructor(
  internal val regex: Regex,
  /** Bits of the type parameters marked with `*` in `Generic<*,_>`, null for a pattern without type parameters. */
  internal val typeParameterMask: Int?,
)

/**
 * Compiles the value of the `stableTypePatterns` option the way the Compose compiler reads its stability
 * configuration file: blank lines and the ones starting with `//` are skipped, `*` is one or more characters of a name
 * segment, `**` crosses the segments. A line the Compose compiler would not accept fails with an error which names
 * the option.
 */
fun stableTypePatterns(patterns: List<String>): List<StableTypePattern> = patterns.map { it.trim() }
  .filterNot { it.isEmpty() || it.startsWith("//") }
  .map { pattern ->
    require(VALID_STABLE_TYPE_PATTERN.matches(pattern)) {
      "NonImmutableDataClassParameter: stableTypePatterns \"$pattern\" is not a valid pattern"
    }
    val name = pattern.substringBefore('<').split("**")
      .joinToString("[\\w.]+") { it.split("*").joinToString("\\w+", transform = Regex::escape) }
    val typeParameters = pattern.substringAfter('<', "").substringBefore('>').takeIf { it.isNotEmpty() }?.split(',')
    StableTypePattern(
      Regex(name),
      typeParameters?.foldIndexed(0) { index, mask, flag -> if (flag == "_") mask else mask or (1 shl index) },
    )
  }

/**
 * Reports a parameter of a composable function whose type is a data class which is not marked with `@Immutable` or
 * `@Stable`.
 *
 * Non-compliant:
 * ```
 * data class Profile(val name: String)
 *
 * @Composable
 * fun ProfileCard(profile: Profile) {}
 * ```
 *
 * Compliant:
 * ```
 * @Immutable
 * data class Profile(val name: String)
 *
 * @Composable
 * fun ProfileCard(profile: Profile) {}
 * ```
 *
 * A data class in the type arguments is reported too: `items: List<Profile>` uses the unstable `Profile`. (The `List`
 * itself stays unstable for the Compose compiler even with a stable `Profile`, that is not reported.)
 *
 * With strong skipping, the default since Kotlin 2.0.20, a composable with an unstable parameter is skippable too, but
 * the parameter is compared by instance (`===`) instead of `equals()`: an equal data class which is created anew, by a
 * `copy()` or a mapper, still recomposes the composable. The annotation brings the comparison by `equals()` back.
 *
 * Not reported:
 * - data classes of the `kotlin` package, they can't be annotated
 * - data classes of a library built with the Compose compiler which are known to be stable at compile time
 *   (`@StabilityInferred`). A generic one is stable together with the type arguments it depends on, only those
 *   arguments are checked
 * - data classes of [NonImmutableDataClassParameterOptions.ignoredPackageSegments], for example generated ones
 * - data classes matching the `stableTypePatterns` option ([stableTypePatterns]), which has the syntax of the
 *   stability configuration file of the Compose compiler: `*` is a part of a name segment, `**` is any number of
 *   segments, the type arguments marked with `_` in `Generic<*,_>` are not checked. Copy the lines of that file here,
 *   a detekt rule can't locate the file itself. Unlike the Compose compiler, the rule does not match the pattern
 *   against the supertypes of a class
 *
 * ```
 * // in detekt-config.yaml
 * NonImmutableDataClassParameter:
 *   active: true
 *   ignoredPackageSegments: [ 'schema' ]
 *   stableTypePatterns: [ 'com.example.model.**', 'java.time.*' ]
 * ```
 */
class NonImmutableDataClassParameterAnalyzer(
  private val options: NonImmutableDataClassParameterOptions,
  private val semantic: ComposeSemantic,
) {
  // A class of the linted sources is judged by its annotations only, the stability the Compose compiler
  // would infer for it (all properties are `val` of stable types) is not computed. A library class whose stability
  // the Compose compiler left to the runtime (it has properties of classes of another module) is reported, the
  // `$stable` field which holds the answer can't be read here. List such classes in stableTypePatterns.
  // Only data classes are judged: an unstable type which is not one (`List`, `java.util.Date`, a class
  // with a `var`) is not reported, neither as a parameter nor as a type argument, and the extension receiver of the
  // composable is not checked. Annotating `Profile` does not make `List<Profile>` stable either
  fun analyze(function: KtNamedFunction): List<ComposeDiagnostic> {
    if (!function.hasAnnotationNamed("Composable")) return emptyList()

    // a vararg parameter is an array of its declared type
    return function.valueParameters.filterNot { it.isVarArg }.flatMap { parameter ->
      val typeReference = parameter.typeReference ?: return@flatMap emptyList()
      val ownClass = unstableDataClass(typeReference)
      val used = typeReference.typeArguments().mapNotNull(::unstableDataClass).distinct() - listOfNotNull(ownClass)
      listOfNotNull(ownClass).map { "is a data class \"${it.simpleName()}\" which" }
        .plus(used.map { "uses a data class \"${it.simpleName()}\" which" })
        .map { ComposeDiagnostic("Parameter \"${parameter.name}\" $it $NOT_MARKED", parameter, atName = true) }
    }
  }

  /** The qualified name of the data class of the type when it is not stable. */
  private fun unstableDataClass(typeReference: KtTypeReference): String? {
    val dataClass = semantic.resolveClass(typeReference)?.takeIf { it.isData } ?: return null
    val packageSegments = dataClass.packageName.split('.')
    val isStable = dataClass.annotationNames.any { it == "Immutable" || it == "Stable" } ||
      dataClass.isInferredStable ||
      packageSegments.firstOrNull() == "kotlin" ||
      packageSegments.any { it in options.ignoredPackageSegments } ||
      options.stableTypes.any { it.regex.matches(dataClass.fqName) }
    return dataClass.fqName.takeUnless { isStable }
  }

  // The arguments are read from the written type, the ones behind a type alias are not seen
  private fun KtTypeReference.typeArguments(): List<KtTypeReference> {
    // a function type is stable whatever its parameter types are
    val userType = generateSequence(typeElement) { (it as? KtNullableType)?.innerType }.lastOrNull() as? KtUserType
    val arguments = userType?.typeArguments.orEmpty().ifEmpty { return emptyList() }
    // the written arguments of a type alias are not the ones of its class
    val mask = semantic.resolveClass(this)?.takeIf { it.typeParameterCount == arguments.size }?.typeParameterMask()
    return arguments.filterIndexed { index, _ -> mask == null || mask and (1 shl index) != 0 }
      .mapNotNull { it.typeReference }
      .flatMap { listOf(it) + it.typeArguments() }
  }

  /** Bits of the type parameters the stability of a stable class depends on, null when it is not known. */
  private fun ResolvedClass.typeParameterMask(): Int? {
    val pattern = options.stableTypes.firstOrNull { it.regex.matches(fqName) }
    return if (pattern != null) pattern.typeParameterMask else stabilityInferredParameters?.takeIf { isInferredStable }
  }
}

private fun String.simpleName() = substringAfterLast('.')

// the pattern the Compose compiler validates the lines of its stability configuration file with (FqNameMatcher)
private val VALID_STABLE_TYPE_PATTERN =
  Regex("""((\w+\*{0,2}|\*{1,2})\.)*((\w+(<?(([*|_],)*[*|_])>)+)|(\w+\*{0,2}|\*{1,2}))""")

private const val NOT_MARKED = "is not marked with @Immutable or @Stable"
