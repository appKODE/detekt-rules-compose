package ru.kode.detekt.rule.compose.node

import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import io.gitlab.arturbosch.detekt.rules.identifierName
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference

fun KtParameter.isComposableSlot(): Boolean {
  return this.typeReference?.hasAnnotation("Composable") == true
}

fun KtParameter.isEventParameter(): Boolean {
  if (this.isComposableSlot()) return false
  val firstChild = this.children.first { it is KtTypeReference }
  val firstChildType = (firstChild as KtTypeReference).typeElement
  return firstChildType is KtFunctionType &&
    firstChildType.returnTypeReference?.text == "Unit" &&
    firstChildType.receiverTypeReference == null
}

fun KtParameter.isModifierParameter(): Boolean {
  return identifierName() == "modifier"
}
