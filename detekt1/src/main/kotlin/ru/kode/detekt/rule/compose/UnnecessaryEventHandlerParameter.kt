package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.UnnecessaryEventHandlerParameterAnalyzer

@RequiresTypeResolution
class UnnecessaryEventHandlerParameter(config: Config = Config.empty) :
  Detekt1SharedRule(config, "Checks for unnecessary event handler parameters") {

  override fun visitNamedFunction(function: KtNamedFunction) {
    // bindingContext is set per file, so the analyzer cannot be cached
    val analyzer = UnnecessaryEventHandlerParameterAnalyzer(BindingContextComposeSemantic(bindingContext))
    reportDiagnostics(analyzer.analyze(function))
  }
}
