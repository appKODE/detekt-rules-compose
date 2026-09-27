package ru.kode.detekt.rule.compose

import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import ru.kode.detekt.rule.compose.shared.ComposeDiagnostic
import ru.kode.detekt.rule.compose.shared.ComposeSemantic

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

  override fun firstComposableLambdaParameterName(
    call: KtCallExpression,
    composableAnnotationPackage: String,
  ): String? {
    val annotationFqName = "$composableAnnotationPackage.Composable"
    return analyze(call) {
      val symbol = call.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return@analyze null
      symbol.valueParameters
        .firstOrNull { parameter ->
          parameter.returnType is KaFunctionType &&
            parameter.returnType.annotations.any { it.classId?.asFqNameString() == annotationFqName }
        }
        ?.name
        ?.asString()
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

  override fun receiverHasSealedTypeOrSupertype(receiverExpression: KtExpression): Boolean {
    return analyze(receiverExpression) {
      val receiverTypeSymbol = receiverExpression.expressionType?.symbol as? KaClassSymbol ?: return@analyze false
      receiverTypeSymbol.modality == KaSymbolModality.SEALED ||
        receiverTypeSymbol.superTypes.any { superType ->
          (superType.symbol as? KaClassSymbol)?.modality == KaSymbolModality.SEALED
        }
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
}

internal fun Rule.reportDiagnostics(diagnostics: List<ComposeDiagnostic>) {
  diagnostics.forEach { diagnostic ->
    report(Finding(diagnostic.entity(), diagnostic.message))
  }
}

private fun ComposeDiagnostic.entity(): Entity =
  if (atName) Entity.atName(anchor as KtNamedDeclaration) else Entity.from(anchor)
