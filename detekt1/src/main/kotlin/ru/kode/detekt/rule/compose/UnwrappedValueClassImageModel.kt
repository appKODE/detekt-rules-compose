package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.psi.KtCallExpression
import ru.kode.detekt.rule.compose.shared.analyzer.UnwrappedValueClassImageModelAnalyzer
import ru.kode.detekt.rule.compose.shared.analyzer.UnwrappedValueClassImageModelOptions

@RequiresTypeResolution
class UnwrappedValueClassImageModel(config: Config = Config.empty) :
  Detekt1SharedRule(config, "Reports value classes passed as a Coil image model without unwrapping") {

  private val imageFunctions by config(
    defaultValue = listOf("AsyncImage", "SubcomposeAsyncImage", "rememberAsyncImagePainter"),
  )

  override fun visitCallExpression(expression: KtCallExpression) {
    // bindingContext is set per file, so the analyzer cannot be cached
    val analyzer = UnwrappedValueClassImageModelAnalyzer(
      UnwrappedValueClassImageModelOptions(imageFunctions = imageFunctions),
      BindingContextComposeSemantic(bindingContext),
    )
    reportDiagnostics(listOfNotNull(analyzer.analyze(expression)))
    super.visitCallExpression(expression)
  }
}
