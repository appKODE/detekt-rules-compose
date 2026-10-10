package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.StateReadCouldBeLoweredAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.StateReadCouldBeLoweredOptions

class StateReadCouldBeLowered(
  config: Config = Config.empty,
  private val composableAnnotationClassPackage: String = "androidx.compose.runtime",
) : Rule(config, "Reports state reads which could be moved closer to their use"), RequiresAnalysisApi {

  private val minDepth by config(defaultValue = 2)

  private val analyzer by lazy {
    StateReadCouldBeLoweredAnalyzer(
      StateReadCouldBeLoweredOptions(
        composableAnnotationClassPackage = composableAnnotationClassPackage,
        minDepth = minDepth,
      ),
      AnalysisApiComposeSemantic,
    )
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
