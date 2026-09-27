package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ComposableEventParameterNamingAnalyzer

class ComposableEventParameterNaming(config: Config = Config.empty) :
  Rule(config, "Checks Composable event parameters naming") {

  private val analyzer = ComposableEventParameterNamingAnalyzer()

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
