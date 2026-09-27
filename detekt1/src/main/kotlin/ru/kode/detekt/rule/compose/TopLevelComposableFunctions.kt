package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.TopLevelComposableFunctionsAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.TopLevelComposableFunctionsOptions

class TopLevelComposableFunctions(config: Config = Config.empty) :
  Detekt1SharedRule(config, "Checks that composable function is defined as a top-level function") {

  private val allowInObjects by config(defaultValue = false)

  private val analyzer by lazy {
    TopLevelComposableFunctionsAnalyzer(
      TopLevelComposableFunctionsOptions(allowInObjects = allowInObjects),
    )
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
