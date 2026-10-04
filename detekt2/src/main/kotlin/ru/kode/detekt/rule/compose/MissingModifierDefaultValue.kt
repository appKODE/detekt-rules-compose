package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.MissingModifierDefaultValueAnalyzer

class MissingModifierDefaultValue(config: Config = Config.empty) :
  Rule(config, "Checks that Modifier parameter has a default value") {

  private val checkAbstractFunctions by config(defaultValue = false)

  private val analyzer by lazy { MissingModifierDefaultValueAnalyzer(checkAbstractFunctions) }

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
  }
}
