package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.PublicComposablePreviewAnalyzer

class PublicComposablePreview(config: Config = Config.empty) :
  Rule(config, "Reports public composable previews") {

  private val analyzer = PublicComposablePreviewAnalyzer()

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
    super.visitNamedFunction(function)
  }
}
