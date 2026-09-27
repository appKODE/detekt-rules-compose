package ru.kode.detekt.rule.compose.shared

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression

interface ComposeSemantic {
  fun isComposableCall(call: KtCallExpression, composableAnnotationPackage: String): Boolean

  /** Names of the callee parameters that receive an explicit (named or positional) argument. */
  fun explicitArgumentParameterNames(call: KtCallExpression): Set<String>

  /** Name of the first callee parameter whose type is a `@Composable` function type. */
  fun firstComposableLambdaParameterName(call: KtCallExpression, composableAnnotationPackage: String): String?

  /** Argument expression bound to the callee parameter [name], or null if it is not passed explicitly. */
  fun argumentForParameterNamed(call: KtCallExpression, name: String): KtExpression?

  fun callHasParameterOfType(call: KtCallExpression, fqName: String): Boolean

  fun receiverHasSealedTypeOrSupertype(receiverExpression: KtExpression): Boolean
}
