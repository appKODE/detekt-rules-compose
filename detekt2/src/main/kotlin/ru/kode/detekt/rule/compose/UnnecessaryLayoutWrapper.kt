package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import ru.kode.detekt.rule.compose.shared.analyzer.UnnecessaryLayoutWrapperAnalyzer

class UnnecessaryLayoutWrapper(config: Config = Config.empty) :
  Rule(config, "Reports Box, Column or Row which only wraps a single Box, Column or Row") {

  private val analyzer = UnnecessaryLayoutWrapperAnalyzer()

  override fun visitCallExpression(expression: KtCallExpression) {
    reportDiagnostics(listOfNotNull(analyzer.analyze(expression)))
    super.visitCallExpression(expression)
  }
}
