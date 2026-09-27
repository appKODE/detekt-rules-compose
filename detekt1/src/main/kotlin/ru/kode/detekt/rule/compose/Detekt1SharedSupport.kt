package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.fqNameOrNull
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.isSealed
import org.jetbrains.kotlin.load.java.descriptors.JavaPropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic

abstract class Detekt1SharedRule(
  config: Config,
  description: String,
) : Rule(config) {
  override val issue: Issue = Issue(
    javaClass.simpleName,
    Severity.Defect,
    description,
    Debt.FIVE_MINS,
  )

  internal fun reportDiagnostics(diagnostics: List<ComposeDiagnostic>) {
    diagnostics.forEach { diagnostic ->
      report(CodeSmell(issue, diagnostic.entity(), diagnostic.message))
    }
  }
}

internal class BindingContextComposeSemantic(
  private val bindingContext: BindingContext,
) : ComposeSemantic {

  override fun isComposableCall(call: KtCallExpression, composableAnnotationPackage: String): Boolean {
    val resolvedCall = call.getResolvedCall(bindingContext) ?: return false
    val annotationFqName = FqName("$composableAnnotationPackage.Composable")
    return resolvedCall.resultingDescriptor.annotations.hasAnnotation(annotationFqName) ||
      resolvedCall.dispatchReceiver?.type?.annotations?.hasAnnotation(annotationFqName) == true
  }

  override fun explicitArgumentParameterNames(call: KtCallExpression): Set<String> {
    val resolvedCall = call.getResolvedCall(bindingContext) ?: return emptySet()
    return resolvedCall.valueArguments
      // default values and empty varargs have no argument expressions, the Analysis API leaves them unmapped too
      .filterValues { it.arguments.isNotEmpty() }
      .keys
      .mapTo(mutableSetOf()) { it.name.asString() }
  }

  override fun firstComposableLambdaParameterName(
    call: KtCallExpression,
    composableAnnotationPackage: String,
  ): String? {
    val resolvedCall = call.getResolvedCall(bindingContext) ?: return null
    val annotationFqName = FqName("$composableAnnotationPackage.Composable")
    return resolvedCall.resultingDescriptor.valueParameters
      .firstOrNull { it.type.isFunctionType && it.type.annotations.hasAnnotation(annotationFqName) }
      ?.name
      ?.asString()
  }

  override fun argumentForParameterNamed(call: KtCallExpression, name: String): KtExpression? {
    val resolvedCall = call.getResolvedCall(bindingContext) ?: return null
    return resolvedCall.valueArguments.entries
      .firstOrNull { it.key.name.asString() == name }
      ?.value
      ?.arguments
      ?.singleOrNull()
      ?.getArgumentExpression()
  }

  override fun callHasParameterOfType(call: KtCallExpression, fqName: String): Boolean {
    val resolvedCall = call.getResolvedCall(bindingContext) ?: return false
    return resolvedCall.valueArguments.any { it.key.type.fqNameOrNull()?.asString() == fqName }
  }

  override fun receiverHasSealedTypeOrSupertype(receiverExpression: KtExpression): Boolean {
    val type = receiverExpression.getType(bindingContext) ?: return false
    val directDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor
    if (directDescriptor?.isSealed() == true) return true

    return type.constructor.supertypes.any { superType ->
      (superType.constructor.declarationDescriptor as? ClassDescriptor)?.isSealed() == true
    }
  }

  override fun lambdaHasImplicitIt(lambda: KtLambdaExpression): Boolean {
    if (lambda.functionLiteral.hasParameterSpecification()) return false
    return bindingContext[BindingContext.FUNCTION, lambda.functionLiteral]?.valueParameters?.size == 1
  }

  override fun isConstantReference(reference: KtNameReferenceExpression): Boolean {
    return when (val target = bindingContext[BindingContext.REFERENCE_TARGET, reference]) {
      // Kotlin constants only: a Java `static final` field is a const JavaPropertyDescriptor here
      is PropertyDescriptor -> target.isConst && target !is JavaPropertyDescriptor

      // companion objects are OBJECT too
      is ClassDescriptor -> target.kind == ClassKind.ENUM_ENTRY || target.kind == ClassKind.OBJECT

      else -> false
    }
  }
}

private fun ComposeDiagnostic.entity(): Entity =
  if (atName) Entity.atName(anchor as KtNamedDeclaration) else Entity.from(anchor)
