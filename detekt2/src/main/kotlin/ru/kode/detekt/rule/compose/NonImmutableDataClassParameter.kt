package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.NonImmutableDataClassParameterAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.NonImmutableDataClassParameterOptions
import ru.kode.detekt.rule.compose.shared.analyzer.stableTypePatterns

class NonImmutableDataClassParameter(config: Config = Config.empty) :
  Rule(config, "Reports composable parameters whose data class is not marked with @Immutable or @Stable"),
  RequiresAnalysisApi {

  private val ignoredPackageSegments by config(defaultValue = emptyList<String>())
  private val stableTypePatterns by config(defaultValue = emptyList<String>())

  private val analyzer by lazy {
    NonImmutableDataClassParameterAnalyzer(
      NonImmutableDataClassParameterOptions(ignoredPackageSegments, stableTypePatterns(stableTypePatterns)),
      AnalysisApiComposeSemantic,
    )
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
