package dev.detekt.test

import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.test.utils.KotlinEnvironmentContainer
import io.gitlab.arturbosch.detekt.test.TestConfig as Detekt1TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext as detekt1CompileAndLintWithContext
import io.gitlab.arturbosch.detekt.test.lint as detekt1Lint

typealias TestConfig = Detekt1TestConfig

fun Rule.lint(code: String): List<Finding> = this.detekt1Lint(code)

fun <T : Rule> T.lintWithContext(environment: KotlinEnvironmentContainer, code: String): List<Finding> =
  this.detekt1CompileAndLintWithContext(environment.env, code)
