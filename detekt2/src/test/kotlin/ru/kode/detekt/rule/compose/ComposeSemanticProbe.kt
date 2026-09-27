package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import org.jetbrains.kotlin.psi.KtFile
import ru.kode.detekt.rule.compose.shared.ComposeSemantic

/** Runs [block] with this engine's [ComposeSemantic] inside the Analysis API session of a linted [code] file. */
fun <T> withComposeSemantic(
  environment: KotlinEnvironmentContainer,
  code: String,
  block: (ComposeSemantic, KtFile) -> T,
): T {
  val probe = ComposeSemanticProbe(block)
  probe.lintWithContext(environment, code)
  return probe.results.single()
}

private class ComposeSemanticProbe<T>(
  private val block: (ComposeSemantic, KtFile) -> T,
) : Rule(Config.empty, "Captures ComposeSemantic answers"), RequiresAnalysisApi {
  val results = mutableListOf<T>()

  override fun visitKtFile(file: KtFile) {
    results += block(AnalysisApiComposeSemantic, file)
  }
}
