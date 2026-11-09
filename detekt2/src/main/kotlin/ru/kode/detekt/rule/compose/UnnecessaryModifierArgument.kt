package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import ru.kode.detekt.rule.compose.shared.analyzer.UnnecessaryModifierArgumentAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.UnnecessaryModifierArgumentOptions

class UnnecessaryModifierArgument(
  config: Config = Config.empty,
  private val modifierClassPackage: String = "androidx.compose.ui",
) : Rule(config, "Reports Modifier arguments passed to a parameter which has Modifier as its default value"),
  RequiresAnalysisApi {

  private val analyzer by lazy {
    UnnecessaryModifierArgumentAnalyzer(
      UnnecessaryModifierArgumentOptions(modifierClassPackage),
      AnalysisApiComposeSemantic,
    )
  }

  override fun visitCallExpression(expression: KtCallExpression) {
    reportDiagnostics(analyzer.analyze(expression))
    super.visitCallExpression(expression)
  }
}
