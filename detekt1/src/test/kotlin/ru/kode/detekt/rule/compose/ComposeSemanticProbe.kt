package ru.kode.detekt.rule.compose

import dev.detekt.test.utils.KotlinEnvironmentContainer
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.test.lintWithContext
import org.jetbrains.kotlin.psi.KtFile
import ru.kode.detekt.rule.compose.shared.ComposeSemantic

/** Runs [block] with this engine's [ComposeSemantic], bound to a type-resolved [code] file. */
fun <T> withComposeSemantic(
  environment: KotlinEnvironmentContainer,
  code: String,
  block: (ComposeSemantic, KtFile) -> T,
): T {
  val results = mutableListOf<T>()
  val probe = object : Rule(Config.empty) {
    override val issue = Issue("ComposeSemanticProbe", Severity.Defect, "", Debt.FIVE_MINS)

    override fun visitKtFile(file: KtFile) {
      results += block(BindingContextComposeSemantic(bindingContext), file)
    }
  }
  probe.lintWithContext(environment.env, code)
  return results.single()
}
