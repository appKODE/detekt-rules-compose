package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.NonImmutableDataClassParameterAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.NonImmutableDataClassParameterOptions
import ru.kode.detekt.rule.compose.shared.analyzer.stableTypePatterns

@RequiresTypeResolution
class NonImmutableDataClassParameter(config: Config = Config.empty) :
  Detekt1SharedRule(config, "Reports composable parameters whose data class is not marked with @Immutable or @Stable") {

  private val ignoredPackageSegments by config(defaultValue = emptyList<String>())
  private val stableTypePatterns by config(defaultValue = emptyList<String>())

  private val options by lazy {
    NonImmutableDataClassParameterOptions(ignoredPackageSegments, stableTypePatterns(stableTypePatterns))
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    // bindingContext is set per file, so the analyzer cannot be cached
    val analyzer = NonImmutableDataClassParameterAnalyzer(options, BindingContextComposeSemantic(bindingContext))
    reportDiagnostics(analyzer.analyze(function))
  }
}
