package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ComposableParametersOrderingAnalyzer

class ComposableParametersOrdering(config: Config = Config.empty) :
  Rule(config, "Checks Composable function parameter ordering") {

  private val trailingSlotNames by config(defaultValue = listOf("content"))

  private val allowTrailingEventHandlers by config(defaultValue = true)

  private val analyzer by lazy { ComposableParametersOrderingAnalyzer(trailingSlotNames, allowTrailingEventHandlers) }

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
