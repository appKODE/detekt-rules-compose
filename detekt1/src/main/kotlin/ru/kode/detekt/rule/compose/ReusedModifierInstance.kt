package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ReusedModifierInstanceAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.ReusedModifierInstanceOptions

@RequiresTypeResolution
class ReusedModifierInstance(
  config: Config = Config.empty,
  private val modifierClassPackage: String = "androidx.compose.ui",
) : Detekt1SharedRule(config, "Reports errors in using modifier on wrong level of composable hierarchy") {

  override fun visitNamedFunction(function: KtNamedFunction) {
    // bindingContext is set per file, so the analyzer cannot be cached
    val analyzer = ReusedModifierInstanceAnalyzer(
      ReusedModifierInstanceOptions(modifierClassPackage = modifierClassPackage),
      BindingContextComposeSemantic(bindingContext),
    )
    reportDiagnostics(analyzer.analyze(function))
    super.visitNamedFunction(function)
  }
}
