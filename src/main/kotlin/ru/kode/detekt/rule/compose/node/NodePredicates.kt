package ru.kode.detekt.rule.compose.node

import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference

fun KtParameter.isComposableSlot(): Boolean {
  val firstChild = this.children.first { it is KtTypeReference }
  return (firstChild as KtTypeReference).hasAnnotation("Composable")
}

fun KtParameter.isEventParameter(): Boolean {
  if (this.isComposableSlot()) return false
  val firstChild = this.children.first { it is KtTypeReference }
  val firstChildType = (firstChild as KtTypeReference).typeElement
  return firstChildType is KtFunctionType &&
    firstChildType.returnTypeReference?.text == "Unit" &&
    firstChildType.receiverTypeReference == null
}
