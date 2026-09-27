package ru.kode.detekt.rule.compose.shared.analyzer

import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

internal fun KtExpression.isModifierChainExpression(): Boolean {
  return when (this) {
    is KtDotQualifiedExpression -> text.startsWith("Modifier") || text.startsWith("modifier")
    is KtNameReferenceExpression -> text == "modifier"
    else -> false
  }
}

/**
 * Whether [name], referenced at this element, is declared between the element and [scope] (exclusive), i.e. shadows
 * a declaration of [scope] or outside it: an explicit parameter of an enclosing lambda or local function, a local
 * `val`/`var` (destructuring included) or local function declared before the element, a `for` loop variable, a
 * catch parameter, a `when` subject variable or a property of a local class or object. A `val` whose initializer
 * uses [name] itself (`val modifier = modifier.padding(4.dp)`) is not a shadow, it derives from the outer
 * declaration. Name-based, no type resolution; implicit `it` parameters are not considered.
 */
internal fun KtElement.isDeclaredBetween(name: String, scope: KtElement): Boolean {
  return parentsWithSelf.zipWithNext()
    .takeWhile { (_, parent) -> parent != scope }
    .any { (child, parent) ->
      when (parent) {
        is KtFunction -> parent.valueParameters.any { it.declares(name) }

        is KtBlockExpression -> parent.statements.takeWhile { it != child }.any { it.declares(name) }

        // the body sits in a container node, a direct child of the `for`
        is KtForExpression -> child == parent.body?.parent && parent.loopParameter.declares(name)

        is KtCatchClause -> child == parent.catchBody && parent.catchParameter.declares(name)

        is KtWhenExpression -> child is KtWhenEntry && parent.subjectVariable.declares(name)

        is KtClassBody -> parent.properties.any { it.declares(name) }

        else -> false
      }
    }
}

private fun KtElement?.declares(name: String): Boolean {
  return when (this) {
    is KtParameter -> this.name == name || destructuringDeclaration.declares(name)

    is KtProperty ->
      this.name == name &&
        initializer?.anyDescendantOfType<KtNameReferenceExpression> { it.getReferencedName() == name } != true

    is KtDestructuringDeclaration -> entries.any { it.name == name }

    is KtNamedFunction -> this.name == name

    else -> false
  }
}
