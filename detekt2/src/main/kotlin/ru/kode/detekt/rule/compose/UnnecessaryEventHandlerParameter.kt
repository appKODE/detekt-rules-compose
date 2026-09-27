package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.UnnecessaryEventHandlerParameterAnalyzer

class UnnecessaryEventHandlerParameter(config: Config = Config.empty) :
  Rule(config, "Checks for unnecessary event handler parameters"), RequiresAnalysisApi {

  private val reportConstantArguments by config(defaultValue = true)
  private val analyzer by lazy {
    UnnecessaryEventHandlerParameterAnalyzer(AnalysisApiComposeSemantic, reportConstantArguments)
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
