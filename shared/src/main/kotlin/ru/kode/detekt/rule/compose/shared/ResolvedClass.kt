package ru.kode.detekt.rule.compose.shared

/** What the analyzers need to know about a resolved class, see [ComposeSemantic.resolveClass]. */
data class ResolvedClass(
  val fqName: String,
  /** The package of the class, without the names of its outer classes; empty for the root package. */
  val packageName: String,
  val isData: Boolean,
  /** Short names of the annotations of the class. */
  val annotationNames: Set<String>,
  /**
   * Names of the public and internal properties of the class, the ones inherited from its supertypes included and
   * the extension properties it declares not. Kotlin classes only: empty for a Java class, whose fields and getters
   * are not properties.
   * A Kotlin class which extends a Java one (`java.util.ArrayList`) gets the synthetic properties of the
   * Java getters (`size`) from the Analysis API only; filter them by origin if a rule meets such a class.
   */
  val propertyNames: Set<String>,
  /** Names of the primary constructor parameters of a data class, in the order of its `componentN` functions. */
  val componentNames: List<String>,
  /**
   * The `parameters` bit mask of the `@StabilityInferred` annotation, which the Compose compiler adds to the classes it
   * compiles; `null` for a class without one, which a class of the linted sources always is.
   */
  val stabilityInferredParameters: Int? = null,
  val typeParameterCount: Int = 0,
) {
  /**
   * Whether the Compose compiler knows at compile time that the class is stable, at least together with its type
   * arguments. In the mask the bit of a type parameter is set when the stability of the class depends on it, and the
   * bit after them when the class is stable whatever the arguments are.
   *
   * The mask has no "known to be unstable" value. Without a bit the stability is left to the `$stable`
   * field read at runtime, and with the bits of type parameters only that field tells whether something else (a
   * `var`) makes the class unstable. The first is taken as unstable and the second as stable; read the field from
   * the class file if that misjudges a real library.
   */
  val isInferredStable: Boolean
    get() = stabilityInferredParameters?.let { it and ((2 shl typeParameterCount) - 1) != 0 } == true
}
