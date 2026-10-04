package ru.kode.detekt.rule.compose

import dev.detekt.api.Rule
import dev.detekt.test.lint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.string.shouldContain
import ru.kode.detekt.rule.compose.contract.SharedRuleContracts
import ru.kode.detekt.rule.compose.snippet.composeSnippet

/**
 * detekt2 has no "empty context" mode: `lint` refuses RequiresAnalysisApi rules up front, so these rules can only
 * run with an Analysis API session. Degradation on unresolved code is covered by HeavyRuleParityTest.
 */
class NoTypeResolutionTest : ShouldSpec({
  val rules = mapOf<String, () -> Rule>(
    "ConditionCouldBeLifted" to { ConditionCouldBeLifted() },
    "ReusedModifierInstance" to { ReusedModifierInstance() },
    "UnnecessaryEventHandlerParameter" to { UnnecessaryEventHandlerParameter() },
  )

  SharedRuleContracts.heavyParityCases.forEach { parityCase ->
    should("refuse to lint without Analysis API: ${parityCase.name}") {
      val error = shouldThrow<IllegalArgumentException> {
        rules.getValue(parityCase.ruleId)().lint(composeSnippet(parityCase.code))
      }

      error.message shouldContain "requires Analysis API"
    }
  }
})
