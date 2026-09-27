// Minimal stand-ins for the Compose API so the sample resolves without the real libraries.
package androidx.compose.runtime

@Target(
  AnnotationTarget.FUNCTION,
  AnnotationTarget.TYPE,
  AnnotationTarget.TYPE_PARAMETER,
  AnnotationTarget.PROPERTY_GETTER,
)
annotation class Composable
