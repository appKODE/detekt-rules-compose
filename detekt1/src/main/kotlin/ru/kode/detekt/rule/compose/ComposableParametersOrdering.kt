package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ComposableParametersOrderingAnalyzer

class ComposableParametersOrdering(config: Config = Config.empty) :
  Detekt1SharedRule(config, "Checks Composable function parameter ordering") {

  private val trailingSlotNames by config(defaultValue = listOf("content"))

  private val analyzer by lazy { ComposableParametersOrderingAnalyzer(trailingSlotNames) }

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
