package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal fun KtExpression.isModifierChainExpression(): Boolean {
  return when (this) {
    is KtDotQualifiedExpression -> text.startsWith("Modifier") || text.startsWith("modifier")
    is KtNameReferenceExpression -> text == "modifier"
    else -> false
  }
}
