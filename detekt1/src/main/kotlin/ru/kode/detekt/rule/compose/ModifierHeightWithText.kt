package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ModifierHeightWithTextAnalyzer

class ModifierHeightWithText(config: Config = Config.empty) :
  Detekt1SharedRule(config, "Reports usage of height modifier in composable functions with text") {

  private val analyzer = ModifierHeightWithTextAnalyzer()

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
    super.visitNamedFunction(function)
  }
}
