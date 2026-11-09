package ru.kode.detekt.rule.compose

import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import ru.kode.detekt.rule.compose.shared.ComposableLambdaParameter
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic
import ru.kode.detekt.rule.compose.shared.ResolvedClass

internal object AnalysisApiComposeSemantic : ComposeSemantic {
  override fun isComposableCall(call: KtCallExpression, composableAnnotationPackage: String): Boolean {
    val annotationFqName = "$composableAnnotationPackage.Composable"
    return analyze(call) {
      val functionCall = call.resolveToCall()?.singleFunctionCallOrNull() ?: return@analyze false
      functionCall.symbol.annotations.any { it.classId?.asFqNameString() == annotationFqName } ||
        functionCall.dispatchReceiver?.type?.annotations
          ?.any { it.classId?.asFqNameString() == annotationFqName } == true
    }
  }

  override fun explicitArgumentParameterNames(call: KtCallExpression): Set<String> {
    return analyze(call) {
      call.resolveToCall()?.singleFunctionCallOrNull()?.valueArgumentMapping?.values
        ?.mapTo(mutableSetOf()) { it.name.asString() }
        .orEmpty()
    }
  }

  override fun composableLambdaParameters(
    call: KtCallExpression,
    composableAnnotationPackage: String,
  ): List<ComposableLambdaParameter> {
    val annotationFqName = "$composableAnnotationPackage.Composable"
    return analyze(call) {
      val symbol = call.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return@analyze emptyList()
      val isInline = (symbol as? KaNamedFunctionSymbol)?.isInline == true
      symbol.valueParameters
        .filter { parameter ->
          parameter.returnType is KaFunctionType &&
            parameter.returnType.annotations.any { it.classId?.asFqNameString() == annotationFqName }
        }
        .map { parameter ->
          ComposableLambdaParameter(
            parameter.name.asString(),
            isInline && !parameter.isNoinline && !parameter.isCrossinline &&
              !parameter.returnType.isMarkedNullable,
          )
        }
    }
  }

  override fun argumentForParameterNamed(call: KtCallExpression, name: String): KtExpression? {
    return analyze(call) {
      call.resolveToCall()?.singleFunctionCallOrNull()?.valueArgumentMapping?.entries
        ?.firstOrNull { it.value.name.asString() == name }
        ?.key
    }
  }

  override fun callHasParameterOfType(call: KtCallExpression, fqName: String): Boolean {
    return analyze(call) {
      val symbol = call.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return@analyze false
      symbol.valueParameters.any { parameter ->
        parameter.returnType.symbol?.classId?.asFqNameString() == fqName
      }
    }
  }

  override fun calleeDeclaringClass(call: KtCallExpression): String? {
    return analyze(call) {
      val symbol = call.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return@analyze null
      (symbol.fakeOverrideOriginal.callableId ?: symbol.callableId)?.classId?.asFqNameString()
    }
  }

  override fun parameterOfArgument(call: KtCallExpression, argument: KtExpression): KtParameter? {
    return analyze(call) {
      val functionCall = call.resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze null
      val parameter = functionCall.valueArgumentMapping[argument]?.symbol ?: return@analyze null
      // an inherited or a delegated member can be a copy of the declared one
      val symbol = functionCall.symbol.fakeOverrideOriginal as? KaFunctionSymbol ?: return@analyze null
      // a generated function, like the copy of a data class, has the parameters of the declaration it is made from
      val isDeclared = symbol.origin == KaSymbolOrigin.SOURCE || symbol.origin == KaSymbolOrigin.TYPEALIASED_CONSTRUCTOR
      symbol.valueParameters.firstOrNull { it.name == parameter.name }
        ?.takeIf { isDeclared }
        // a library declaration has the PSI of its decompiled stub, without the default values
        ?.let { it.psi as? KtParameter }
        ?.takeUnless { it.containingKtFile.isCompiled }
    }
  }

  override fun receiverHasSealedTypeOrSupertype(receiverExpression: KtExpression): Boolean {
    return analyze(receiverExpression) {
      val receiverTypeSymbol = receiverExpression.expressionType?.symbol as? KaClassSymbol ?: return@analyze false
      receiverTypeSymbol.modality == KaSymbolModality.SEALED ||
        receiverTypeSymbol.superTypes.any { superType ->
          (superType.symbol as? KaClassSymbol)?.modality == KaSymbolModality.SEALED
        }
    }
  }

  override fun isValueClassInstance(expression: KtExpression): Boolean {
    return analyze(expression) {
      val symbol = expression.expressionType?.expandedSymbol as? KaNamedClassSymbol ?: return@analyze false
      symbol.isInline && symbol.classId?.packageFqName?.startsWith(Name.identifier("kotlin")) != true
    }
  }

  override fun isInstanceOf(expression: KtExpression, fqName: String): Boolean {
    return analyze(expression) {
      val type = expression.expressionType ?: return@analyze false
      (sequenceOf(type) + type.allSupertypes).any { it.expandedSymbol?.classId?.asFqNameString() == fqName }
    }
  }

  override fun lambdaHasImplicitIt(lambda: KtLambdaExpression): Boolean {
    if (lambda.functionLiteral.hasParameterSpecification()) return false
    return analyze(lambda) { lambda.functionLiteral.symbol.valueParameters.size == 1 }
  }

  override fun isConstantReference(reference: KtNameReferenceExpression): Boolean {
    return analyze(reference) {
      when (val symbol = reference.mainReference.resolveToSymbol()) {
        is KaKotlinPropertySymbol -> symbol.isConst
        is KaEnumEntrySymbol -> true
        is KaClassSymbol -> symbol.classKind == KaClassKind.OBJECT || symbol.classKind == KaClassKind.COMPANION_OBJECT
        else -> false
      }
    }
  }

  override fun referenceTargetsParameter(reference: KtNameReferenceExpression, parameter: KtParameter): Boolean? {
    // a resolved symbol without source (a library declaration) is not the parameter, only no symbol is "unknown"
    return analyze(reference) { reference.mainReference.resolveToSymbol()?.let { it.psi == parameter } }
  }

  override fun resolveClass(typeReference: KtTypeReference): ResolvedClass? {
    return analyze(typeReference) {
      // the type of a vararg parameter is the array, its type reference names the element
      val type = typeReference.type
        .let { if ((typeReference.parent as? KtParameter)?.isVarArg == true) it.arrayElementType ?: it else it }
      val symbol = type.expandedSymbol ?: return@analyze null
      val classId = symbol.classId ?: return@analyze null
      ResolvedClass(
        fqName = classId.asFqNameString(),
        packageName = classId.packageFqName.asString(),
        isData = (symbol as? KaNamedClassSymbol)?.isData == true,
        annotationNames = symbol.annotations.mapNotNullTo(mutableSetOf()) { it.classId?.shortClassName?.asString() },
        propertyNames = symbol.memberScope.callables.filterIsInstance<KaPropertySymbol>()
          .filter { !it.isExtension }
          .filter { it.visibility == KaSymbolVisibility.PUBLIC || it.visibility == KaSymbolVisibility.INTERNAL }
          .mapTo(mutableSetOf()) { it.name.asString() },
        componentNames = symbol.takeIf { (it as? KaNamedClassSymbol)?.isData == true }
          ?.declaredMemberScope?.constructors?.firstOrNull { it.isPrimary }?.valueParameters
          ?.map { it.name.asString() }
          .orEmpty(),
        stabilityInferredParameters = symbol.annotations
          .firstOrNull { it.classId?.shortClassName?.asString() == "StabilityInferred" }
          ?.arguments?.firstOrNull { it.name.asString() == "parameters" }
          ?.let { (it.expression as? KaAnnotationValue.ConstantValue)?.value?.value as? Int },
        typeParameterCount = (symbol as? KaNamedClassSymbol)?.typeParameters.orEmpty().size,
      )
    }
  }

  override fun isOnImplicitReceiverOf(reference: KtNameReferenceExpression, lambda: KtFunctionLiteral): Boolean {
    return analyze(reference) {
      val applied = reference.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
        ?.partiallyAppliedSymbol
        ?: return@analyze false
      listOfNotNull(applied.extensionReceiver, applied.dispatchReceiver)
        .map { (it as? KaSmartCastedReceiverValue)?.original ?: it }
        .filterIsInstance<KaImplicitReceiverValue>()
        // the receiver of a lambda is a parameter of its anonymous function
        .any { ((it.symbol as? KaReceiverParameterSymbol)?.owningCallableSymbol ?: it.symbol).psi == lambda }
    }
  }
}

internal fun Rule.reportDiagnostics(diagnostics: List<ComposeDiagnostic>) {
  diagnostics.forEach { diagnostic ->
    report(Finding(diagnostic.entity(), diagnostic.message))
  }
}

private fun ComposeDiagnostic.entity(): Entity =
  if (atName) Entity.atName(anchor as KtNamedDeclaration) else Entity.from(anchor)
