package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType

class UnnecessaryLayoutWrapper(config: Config = Config.empty) : Rule(config) {

  override val issue: Issue = Issue(
    javaClass.simpleName,
    Severity.CodeSmell,
    "Reports when Box, Column or Row is used as an unnecessary wrapper",
    Debt.FIVE_MINS
  )

  private val layoutNames = setOf("Box", "Column", "Row")

  private val unnecessaryExpressions = mutableListOf<KtCallExpression>()

  override fun visitCallExpression(expression: KtCallExpression) {
    val isLayout = layoutNames.any { it == expression.referenceName }
    val hasNoParameters = expression.valueArgumentList == null
    val hasSingleChildLayout = expression.lambdaBlock?.hasSingleLayout() ?: false
    if (isLayout && hasNoParameters && hasSingleChildLayout) {
      unnecessaryExpressions.add(expression)
    }

    super.visitCallExpression(expression)
  }

  private fun KtBlockExpression.hasSingleLayout(): Boolean {
    val callExpressions = this.callExpressions
    if (callExpressions.size != 1) return false
    val referencedName =
      (callExpressions[0].calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
    return layoutNames.any { it == referencedName }
  }

  override fun preVisit(root: KtFile) {
    unnecessaryExpressions.clear()
  }

  override fun postVisit(root: KtFile) {
    unnecessaryExpressions.forEach { expression ->
      report(
        CodeSmell(
          issue,
          Entity.from(expression),
          "${expression.referenceName} is used as an unnecessary wrapper. " +
            "Its child layout must be used directly"
        )
      )
    }
  }

  private val KtCallExpression.lambdaBlock
    get() = this.getChildOfType<KtLambdaArgument>()?.getLambdaExpression()?.bodyExpression

  private val KtCallExpression.referenceName
    get() = (this.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()

  private val KtBlockExpression.callExpressions
    get() = this.getChildrenOfType<KtCallExpression>()
}
