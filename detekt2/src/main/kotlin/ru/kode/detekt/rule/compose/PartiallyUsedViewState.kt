package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.PartiallyUsedViewStateAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.PartiallyUsedViewStateOptions
import ru.kode.detekt.rule.compose.shared.analyzer.stateClassPattern

class PartiallyUsedViewState(config: Config = Config.empty) :
  Rule(config, "Reports composable functions which take a whole view state but read only a few of its properties"),
  RequiresAnalysisApi {

  private val stateClassPattern by config(defaultValue = ".*ViewState")
  private val usedFieldsThreshold by config(defaultValue = 3)

  private val analyzer by lazy {
    PartiallyUsedViewStateAnalyzer(
      PartiallyUsedViewStateOptions(stateClassPattern(stateClassPattern), usedFieldsThreshold),
      AnalysisApiComposeSemantic,
    )
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
