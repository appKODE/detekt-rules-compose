package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.psi.KtCallExpression
import ru.kode.detekt.rule.compose.shared.analyzer.UnnecessaryModifierArgumentAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.UnnecessaryModifierArgumentOptions

@RequiresTypeResolution
class UnnecessaryModifierArgument(
  config: Config = Config.empty,
  private val modifierClassPackage: String = "androidx.compose.ui",
) : Detekt1SharedRule(
  config,
  "Reports Modifier arguments passed to a parameter which has Modifier as its default value",
) {

  override fun visitCallExpression(expression: KtCallExpression) {
    // bindingContext is set per file, so the analyzer cannot be cached
    val analyzer = UnnecessaryModifierArgumentAnalyzer(
      UnnecessaryModifierArgumentOptions(modifierClassPackage),
      BindingContextComposeSemantic(bindingContext),
    )
    reportDiagnostics(analyzer.analyze(expression))
    super.visitCallExpression(expression)
  }
}
