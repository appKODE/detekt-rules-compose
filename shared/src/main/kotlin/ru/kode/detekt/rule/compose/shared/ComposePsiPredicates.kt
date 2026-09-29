package ru.kode.detekt.rule.compose.shared

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference

fun KtModifierListOwner.hasAnnotationNamed(annotationName: String): Boolean {
  return annotationEntries.any { it.matchesAnnotationName(annotationName) }
}

fun KtTypeReference.hasAnnotationNamed(annotationName: String): Boolean {
  return annotationEntries.any { it.matchesAnnotationName(annotationName) }
}

private fun KtAnnotationEntry.matchesAnnotationName(annotationName: String): Boolean {
  val shortName = shortName?.asString()
  if (shortName == annotationName) {
    return true
  }

  val typeText = typeReference?.text ?: return false
  return typeText.substringAfterLast('.') == annotationName
}

fun KtNamedFunction.isOverrideLike(): Boolean {
  return hasModifier(KtTokens.OVERRIDE_KEYWORD)
}

fun KtNamedFunction.isActualLike(): Boolean {
  return hasModifier(KtTokens.ACTUAL_KEYWORD)
}

fun KtNamedFunction.isAbstractLike(): Boolean {
  return hasModifier(KtTokens.ABSTRACT_KEYWORD)
}

fun KtNamedFunction.isOpenLike(): Boolean {
  return hasModifier(KtTokens.OPEN_KEYWORD)
}

fun KtParameter.isComposableSlot(): Boolean {
  val typeReference = typeReference ?: return false
  // `(@Composable () -> Unit)?` puts the annotation on the nullable type, not on the type reference
  return typeReference.hasAnnotationNamed("Composable") ||
    (typeReference.typeElement as? KtNullableType)?.annotationEntries.orEmpty()
      .any { it.matchesAnnotationName("Composable") }
}

fun KtParameter.functionTypeOrNull(): KtFunctionType? {
  val typeElement = typeReference?.typeElement
  return (if (typeElement is KtNullableType) typeElement.innerType else typeElement) as? KtFunctionType
}

fun KtParameter.isEventHandler(): Boolean {
  if (isComposableSlot()) return false
  val functionType = functionTypeOrNull() ?: return false
  return functionType.returnTypeReference?.text == "Unit" &&
    functionType.receiverTypeReference == null
}

fun KtParameter.isModifier(): Boolean {
  return name == "modifier"
}

fun KtParameter.isLambda(): Boolean {
  val firstChild = children.firstOrNull { it is KtTypeReference } as? KtTypeReference ?: return false
  var firstChildType = firstChild.typeElement
  if (firstChildType is KtNullableType) {
    firstChildType = firstChildType.innerType
  }
  return firstChildType is KtFunctionType
}

/** true = Unit, false = a value, null = can't tell without type resolution. */
fun KtNamedFunction.returnsUnitLike(): Boolean? {
  val explicitType = typeReference
  if (explicitType != null) return explicitType.text == "Unit" || explicitType.text == "kotlin.Unit"
  if (hasBlockBody()) return true
  // `= Box {}` — an upper-case call body is usually a Unit composable (or a constructor); needs TR to decide
  val calleeName = (bodyExpression as? KtCallExpression)?.calleeExpression?.text
  return if (calleeName?.firstOrNull()?.isUpperCase() == true) null else false
}
