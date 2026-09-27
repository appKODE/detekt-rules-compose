package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ConditionCouldBeLiftedAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.ConditionCouldBeLiftedOptions

@RequiresTypeResolution
class ConditionCouldBeLifted(
  config: Config = Config.empty,
  private val composableAnnotationClassPackage: String = "androidx.compose.runtime",
) : Detekt1SharedRule(config, "Reports liftable conditions in compose layouts") {

  private val ignoreCallsWithArgumentNames by config(defaultValue = listOf("modifier"))

  override fun visitNamedFunction(function: KtNamedFunction) {
    // bindingContext is set per file, so the analyzer cannot be cached
    val analyzer = ConditionCouldBeLiftedAnalyzer(
      ConditionCouldBeLiftedOptions(
        composableAnnotationClassPackage = composableAnnotationClassPackage,
        ignoreCallsWithArgumentNames = ignoreCallsWithArgumentNames,
      ),
      BindingContextComposeSemantic(bindingContext),
    )
    reportDiagnostics(analyzer.analyze(function))
  }
}
