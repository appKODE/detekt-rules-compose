package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ConditionCouldBeLiftedAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.ConditionCouldBeLiftedOptions

class ConditionCouldBeLifted(
  config: Config = Config.empty,
  private val composableAnnotationClassPackage: String = "androidx.compose.runtime",
) : Rule(config, "Reports liftable conditions in compose layouts"), RequiresAnalysisApi {

  private val ignoreCallsWithArgumentNames by config(defaultValue = listOf("modifier"))

  private val analyzer by lazy {
    ConditionCouldBeLiftedAnalyzer(
      ConditionCouldBeLiftedOptions(
        composableAnnotationClassPackage = composableAnnotationClassPackage,
        ignoreCallsWithArgumentNames = ignoreCallsWithArgumentNames,
      ),
      AnalysisApiComposeSemantic,
    )
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
