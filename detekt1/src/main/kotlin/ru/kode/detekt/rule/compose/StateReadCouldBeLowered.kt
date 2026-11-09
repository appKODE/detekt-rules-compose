package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.StateReadCouldBeLoweredAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.StateReadCouldBeLoweredOptions

@RequiresTypeResolution
class StateReadCouldBeLowered(
  config: Config = Config.empty,
  private val composableAnnotationClassPackage: String = "androidx.compose.runtime",
) : Detekt1SharedRule(config, "Reports state reads which could be moved closer to their use") {

  private val minDepth by config(defaultValue = 2)

  override fun visitNamedFunction(function: KtNamedFunction) {
    // bindingContext is set per file, so the analyzer cannot be cached
    val analyzer = StateReadCouldBeLoweredAnalyzer(
      StateReadCouldBeLoweredOptions(
        composableAnnotationClassPackage = composableAnnotationClassPackage,
        minDepth = minDepth,
      ),
      BindingContextComposeSemantic(bindingContext),
    )
    reportDiagnostics(analyzer.analyze(function))
  }
}
