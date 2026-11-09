package ru.kode.detekt.rule.compose.shared

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference

interface ComposeSemantic {
  fun isComposableCall(call: KtCallExpression, composableAnnotationPackage: String): Boolean

  /** Names of the callee parameters that receive an explicit (named or positional) argument. */
  fun explicitArgumentParameterNames(call: KtCallExpression): Set<String>

  /** The callee parameters whose type is a `@Composable` function type, in declaration order. */
  fun composableLambdaParameters(
    call: KtCallExpression,
    composableAnnotationPackage: String,
  ): List<ComposableLambdaParameter>

  /** Argument expression bound to the callee parameter [name], or null if it is not passed explicitly. */
  fun argumentForParameterNamed(call: KtCallExpression, name: String): KtExpression?

  fun callHasParameterOfType(call: KtCallExpression, fqName: String): Boolean

  fun receiverHasSealedTypeOrSupertype(receiverExpression: KtExpression): Boolean

  /** Whether [lambda] has an implicit `it` parameter; false when that can't be resolved. */
  fun lambdaHasImplicitIt(lambda: KtLambdaExpression): Boolean

  /**
   * Whether [reference] resolves to a Kotlin `const val` (not a Java `static final` field), an enum entry or a named
   * or companion object; false when it can't be resolved.
   */
  fun isConstantReference(reference: KtNameReferenceExpression): Boolean

  /** Whether [reference] resolves to [parameter] and not to a namesake; null when it can't be resolved. */
  fun referenceTargetsParameter(reference: KtNameReferenceExpression, parameter: KtParameter): Boolean?

  /**
   * The class [typeReference] resolves to, with type aliases expanded and nullability ignored; null for a type
   * parameter or when it can't be resolved.
   */
  fun resolveClass(typeReference: KtTypeReference): ResolvedClass?

  /**
   * Whether the type of [expression] is a value (inline) class; false when it can't be resolved and for the classes of
   * the `kotlin` package and its subpackages (`UInt`, `Result`, `Duration`), which nobody declares as a wrapper.
   */
  fun isValueClassInstance(expression: KtExpression): Boolean

  /**
   * Whether the type of [expression] is the class [fqName] or its subtype, with smart casts applied and type aliases
   * expanded; false when it can't be resolved.
   */
  fun isInstanceOf(expression: KtExpression, fqName: String): Boolean

  /**
   * Fully qualified name of the class which declares the function [call] calls, also when the function is called on a
   * subclass; null for a function which is not a member or when the call can't be resolved.
   */
  fun calleeDeclaringClass(call: KtCallExpression): String?

  /**
   * The declaration of the parameter of the function [call] calls which [argument], one of the argument expressions
   * of the call as written, is passed to. An inherited or a delegated member has the parameter of the class which
   * declares it. Null when the call isn't resolved without errors or the function is not declared in the linted
   * sources, a generated one (the `copy` of a data class) included.
   */
  fun parameterOfArgument(call: KtCallExpression, argument: KtExpression): KtParameter?

  /**
   * Whether the member or extension [reference] names is called or read on the receiver of [lambda] (its `this`)
   * taken implicitly, as the dispatch or as the extension receiver; false for the receiver of another lambda or of a
   * class, even of the same type, and when [reference] can't be resolved.
   */
  fun isOnImplicitReceiverOf(reference: KtNameReferenceExpression, lambda: KtFunctionLiteral): Boolean
}
