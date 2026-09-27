package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.ReusedModifierInstanceAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.ReusedModifierInstanceOptions

class ReusedModifierInstance(
  config: Config = Config.empty,
  private val modifierClassPackage: String = "androidx.compose.ui",
) : Rule(config, "Reports errors in using modifier on wrong level of composable hierarchy"), RequiresAnalysisApi {

  private val analyzer by lazy {
    ReusedModifierInstanceAnalyzer(
      ReusedModifierInstanceOptions(modifierClassPackage = modifierClassPackage),
      AnalysisApiComposeSemantic,
    )
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
    super.visitNamedFunction(function)
  }
}
