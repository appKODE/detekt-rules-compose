package ru.kode.detekt.rule.compose.node

import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import io.gitlab.arturbosch.detekt.rules.identifierName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

fun KtParameter.isComposableSlot(): Boolean {
  return this.typeReference?.hasAnnotation("Composable") == true
}

fun KtParameter.isEventHandler(): Boolean {
  if (this.isComposableSlot()) return false
  val firstChild = this.children.first { it is KtTypeReference }
  val firstChildType = (firstChild as KtTypeReference).typeElement
  return firstChildType is KtFunctionType &&
    firstChildType.returnTypeReference?.text == "Unit" &&
    firstChildType.receiverTypeReference == null
}

fun KtParameter.isModifier(): Boolean {
  return identifierName() == "modifier"
}

fun KtCallExpression.isComposableCall(
  composableAnnotationClassPackage: String,
  bindingContext: BindingContext,
): Boolean {
  val resolvedCall = this.getResolvedCall(bindingContext) ?: return false
  val annotationFqName = FqName("$composableAnnotationClassPackage.Composable")
  return resolvedCall.resultingDescriptor.annotations.hasAnnotation(annotationFqName) ||
    resolvedCall.dispatchReceiver?.type?.annotations?.hasAnnotation(annotationFqName) == true
}

fun KtExpression.hasComposableCallChildren(
  composableAnnotationClassPackage: String,
  bindingContext: BindingContext,
): Boolean {
  return this.collectDescendantsOfType<KtCallExpression>().any {
    it.isComposableCall(composableAnnotationClassPackage, bindingContext)
  }
}
