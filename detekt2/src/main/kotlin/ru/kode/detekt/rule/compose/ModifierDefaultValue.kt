package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ModifierDefaultValueAnalyzer

class ModifierDefaultValue(config: Config = Config.empty) :
  Rule(config, "Checks that Modifier parameter has a correct default value") {

  private val analyzer = ModifierDefaultValueAnalyzer()

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
