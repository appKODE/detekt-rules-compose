package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import ru.kode.detekt.rule.compose.shared.analyzer.UnwrappedValueClassImageModelAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.UnwrappedValueClassImageModelOptions

class UnwrappedValueClassImageModel(config: Config = Config.empty) :
  Rule(config, "Reports value classes passed as a Coil image model without unwrapping"),
  RequiresAnalysisApi {

  private val imageFunctions by config(
    defaultValue = listOf("AsyncImage", "SubcomposeAsyncImage", "rememberAsyncImagePainter"),
  )

  private val analyzer by lazy {
    UnwrappedValueClassImageModelAnalyzer(
      UnwrappedValueClassImageModelOptions(imageFunctions = imageFunctions),
      AnalysisApiComposeSemantic,
    )
  }

  override fun visitCallExpression(expression: KtCallExpression) {
    reportDiagnostics(listOfNotNull(analyzer.analyze(expression)))
    super.visitCallExpression(expression)
  }
}
