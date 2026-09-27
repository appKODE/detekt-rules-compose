package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ComposableEventParameterNamingAnalyzer

class ComposableEventParameterNaming(config: Config = Config.empty) :
  Detekt1SharedRule(config, "Checks Composable event parameters naming") {

  private val analyzer = ComposableEventParameterNamingAnalyzer()

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
