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
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor.Kind
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.isSealed
import org.jetbrains.kotlin.load.java.descriptors.JavaPropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactoryImpl
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.supertypes
import ru.kode.detekt.rule.compose.shared.ComposableLambdaParameter
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic
import ru.kode.detekt.rule.compose.shared.ResolvedClass

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

  override fun composableLambdaParameters(
    call: KtCallExpression,
    composableAnnotationPackage: String,
  ): List<ComposableLambdaParameter> {
    val descriptor = call.getResolvedCall(bindingContext)?.resultingDescriptor ?: return emptyList()
    val annotationFqName = FqName("$composableAnnotationPackage.Composable")
    val isInline = (descriptor as? FunctionDescriptor)?.isInline == true
    return descriptor.valueParameters
      .filter { it.type.isFunctionType && it.type.annotations.hasAnnotation(annotationFqName) }
      .map {
        ComposableLambdaParameter(
          it.name.asString(),
          isInline && !it.isNoinline && !it.isCrossinline && !it.type.isMarkedNullable,
        )
      }
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

  override fun calleeDeclaringClass(call: KtCallExpression): String? {
    val descriptor =
      call.getResolvedCall(bindingContext)?.resultingDescriptor as? CallableMemberDescriptor ?: return null
    return (DescriptorUtils.unwrapFakeOverride(descriptor).containingDeclaration as? ClassDescriptor)
      ?.fqNameSafe
      ?.asString()
  }

  override fun parameterOfArgument(call: KtCallExpression, argument: KtExpression): KtParameter? {
    val resolvedCall = call.getResolvedCall(bindingContext)?.takeIf { it.status.isSuccess } ?: return null
    val parameter = resolvedCall.valueArguments.entries
      .firstOrNull { entry -> entry.value.arguments.any { it.getArgumentExpression() == argument } }
      ?.key
      ?: return null
    // an inherited or a delegated member is a copy of the declared one
    val callee = generateSequence(resolvedCall.resultingDescriptor) { descriptor ->
      (descriptor as? CallableMemberDescriptor)
        ?.takeIf { it.kind == Kind.FAKE_OVERRIDE || it.kind == Kind.DELEGATION }
        ?.overriddenDescriptors
        ?.firstOrNull()
    }.last()
    // a generated function, like the copy of a data class, has the parameters of the declaration it is made from
    return callee.valueParameters.getOrNull(parameter.index)
      ?.takeIf { (callee as? CallableMemberDescriptor)?.kind == Kind.DECLARATION }
      ?.let { DescriptorToSourceUtils.descriptorToDeclaration(it) as? KtParameter }
  }

  override fun receiverHasSealedTypeOrSupertype(receiverExpression: KtExpression): Boolean {
    val type = receiverExpression.getType(bindingContext) ?: return false
    val directDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor
    if (directDescriptor?.isSealed() == true) return true

    return type.constructor.supertypes.any { superType ->
      (superType.constructor.declarationDescriptor as? ClassDescriptor)?.isSealed() == true
    }
  }

  override fun isValueClassInstance(expression: KtExpression): Boolean {
    return expression.types().any { type ->
      val descriptor = type.constructor.declarationDescriptor as? ClassDescriptor
      descriptor != null && (descriptor.isValue || descriptor.isInline) &&
        !KotlinBuiltIns.isUnderKotlinPackage(descriptor)
    }
  }

  override fun isInstanceOf(expression: KtExpression, fqName: String): Boolean {
    return expression.types().any { type ->
      (listOf(type) + type.supertypes()).any { it.fqNameOrNull()?.asString() == fqName }
    }
  }

  /**
   * The type of the expression and the types it is smart cast to. `getType` alone knows only the smart casts the
   * compiler needed, so not the one of an argument passed to an `Any?` parameter.
   */
  private fun KtExpression.types(): List<KotlinType> {
    val type = getType(bindingContext) ?: return emptyList()
    val container = parents.firstNotNullOfOrNull { bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, it] }
      ?: return listOf(type)
    val languageVersionSettings = LanguageVersionSettingsImpl.DEFAULT
    val value = DataFlowValueFactoryImpl(languageVersionSettings)
      .createDataFlowValue(this, type, bindingContext, container)
    // the info after a name reference is the one before it, the reference adds nothing
    val dataFlowInfo = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, this]?.dataFlowInfo ?: return listOf(type)
    return listOf(type) + dataFlowInfo.getStableTypes(value, languageVersionSettings)
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

  override fun referenceTargetsParameter(reference: KtNameReferenceExpression, parameter: KtParameter): Boolean? {
    val target = bindingContext[BindingContext.REFERENCE_TARGET, reference] ?: return null
    return DescriptorToSourceUtils.descriptorToDeclaration(target) == parameter
  }

  override fun resolveClass(typeReference: KtTypeReference): ResolvedClass? {
    // an unresolved type has an error class descriptor
    val type = bindingContext[BindingContext.TYPE, typeReference]?.takeUnless { it.isError } ?: return null
    val descriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return null
    // a local class has no class id in the Analysis API either
    if (DescriptorUtils.isLocal(descriptor)) return null
    return ResolvedClass(
      fqName = descriptor.fqNameSafe.asString(),
      packageName = descriptor.classId?.packageFqName?.asString().orEmpty(),
      isData = descriptor.isData,
      annotationNames = descriptor.annotations.mapNotNullTo(mutableSetOf()) { it.fqName?.shortName()?.asString() },
      propertyNames = descriptor.unsubstitutedMemberScope.getContributedDescriptors()
        // the Analysis API does not count the fields of a Java class as properties
        .filterIsInstance<PropertyDescriptor>()
        .filter { it !is JavaPropertyDescriptor && it.extensionReceiverParameter == null }
        .filter { it.visibility == DescriptorVisibilities.PUBLIC || it.visibility == DescriptorVisibilities.INTERNAL }
        .mapTo(mutableSetOf()) { it.name.asString() },
      componentNames = descriptor.unsubstitutedPrimaryConstructor?.takeIf { descriptor.isData }?.valueParameters
        ?.map { it.name.asString() }
        .orEmpty(),
      stabilityInferredParameters = descriptor.annotations
        .firstOrNull { it.fqName?.shortName()?.asString() == "StabilityInferred" }
        ?.allValueArguments?.get(Name.identifier("parameters"))?.value as? Int,
      typeParameterCount = descriptor.declaredTypeParameters.size,
    )
  }

  override fun isOnImplicitReceiverOf(reference: KtNameReferenceExpression, lambda: KtFunctionLiteral): Boolean {
    val resolvedCall = reference.getResolvedCall(bindingContext) ?: return false
    // `onClick()` of a property is a call of `invoke` on the property, the property is the one read on the receiver
    val call = (resolvedCall as? VariableAsFunctionResolvedCall)?.variableCall ?: resolvedCall
    return listOfNotNull(call.extensionReceiver, call.dispatchReceiver).filterIsInstance<ImplicitReceiver>()
      .any { DescriptorToSourceUtils.descriptorToDeclaration(it.declarationDescriptor) == lambda }
  }
}

private fun ComposeDiagnostic.entity(): Entity =
  if (atName) Entity.atName(anchor as KtNamedDeclaration) else Entity.from(anchor)
