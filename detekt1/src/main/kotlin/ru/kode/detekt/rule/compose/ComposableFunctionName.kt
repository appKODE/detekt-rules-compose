package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ComposableFunctionNameAnalyzer

class ComposableFunctionName(config: Config = Config.empty) :
  Detekt1SharedRule(config, "Incorrect composable function name") {

  private val analyzer = ComposableFunctionNameAnalyzer()

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
    super.visitNamedFunction(function)
  }
}
