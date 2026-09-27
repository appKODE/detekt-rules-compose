package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ComposableParametersOrderingAnalyzer

class ComposableParametersOrdering(config: Config = Config.empty) :
  Rule(config, "Checks Composable function parameter ordering") {

  private val analyzer = ComposableParametersOrderingAnalyzer()

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
