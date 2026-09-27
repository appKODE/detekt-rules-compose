package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtNamedFunction
import ru.kode.detekt.rule.compose.shared.analyzer.PublicComposablePreviewAnalyzer

class PublicComposablePreview(config: Config = Config.empty) :
  Detekt1SharedRule(config, "Reports public composable previews") {

  private val analyzer = PublicComposablePreviewAnalyzer()

  override fun visitNamedFunction(function: KtNamedFunction) {
    reportDiagnostics(analyzer.analyze(function))
    super.visitNamedFunction(function)
  }
}
