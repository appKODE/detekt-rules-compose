package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ComposableParametersOrderingAnalyzer

class ComposableParametersOrdering(config: Config = Config.empty) :
  Detekt1SharedRule(config, "Checks Composable function parameter ordering") {

  private val analyzer = ComposableParametersOrderingAnalyzer()

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
