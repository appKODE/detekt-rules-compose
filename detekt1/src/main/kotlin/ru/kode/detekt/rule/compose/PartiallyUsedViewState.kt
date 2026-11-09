package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.PartiallyUsedViewStateAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.PartiallyUsedViewStateOptions
import ru.kode.detekt.rule.compose.shared.analyzer.stateClassPattern

@RequiresTypeResolution
class PartiallyUsedViewState(config: Config = Config.empty) :
  Detekt1SharedRule(
    config,
    "Reports composable functions which take a whole view state but read only a few of its properties",
  ) {

  private val stateClassPattern by config(defaultValue = ".*ViewState")
  private val usedFieldsThreshold by config(defaultValue = 3)

  private val options by lazy {
    PartiallyUsedViewStateOptions(stateClassPattern(stateClassPattern), usedFieldsThreshold)
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    // bindingContext is set per file, so the analyzer cannot be cached
    val analyzer = PartiallyUsedViewStateAnalyzer(options, BindingContextComposeSemantic(bindingContext))
    reportDiagnostics(analyzer.analyze(function))
  }
}
