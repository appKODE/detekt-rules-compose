package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.MissingModifierDefaultValueAnalyzer

class MissingModifierDefaultValue(config: Config = Config.empty) :
  Detekt1SharedRule(config, "Checks that Modifier parameter has a default value") {

  private val analyzer = MissingModifierDefaultValueAnalyzer()

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
