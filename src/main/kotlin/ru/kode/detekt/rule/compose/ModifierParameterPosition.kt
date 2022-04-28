package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Location
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.hasAnnotation
import io.gitlab.arturbosch.detekt.rules.identifierName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Checks that "modifier" argument for Composable function is passed as a first parameter.
 *
 * Wrong:
 * ```
 * fun Button(
 *   arrangement = Vertical,
 *   modifier = Modifier,
 * )
 * ```
 * Correct:
 * ```
 * fun Button(
 *   modifier = Modifier,
 *   arrangement = Vertical,
 * )
 * ```
 */
class ModifierParameterPosition(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "Reports incorrect modifier parameter position",
        Debt.FIVE_MINS
    )

    private val incorrectPositions = Collections.newSetFromMap(IdentityHashMap<KtNamedFunction, Boolean>())

    override fun visitNamedFunction(function: KtNamedFunction) {
        if (function.hasAnnotation("Composable")) {
            checkFunction(function)
        }
    }

    private fun checkFunction(function: KtNamedFunction) {
        val position = function.valueParameters.indexOfFirst { it.isModifierParameter() }
        if (position > 0) {
            incorrectPositions.add(function)
        }
    }

    override fun preVisit(root: KtFile) {
        incorrectPositions.clear()
    }

    override fun postVisit(root: KtFile) {
        incorrectPositions.forEach { node ->
            report(
                CodeSmell(
                    issue,
                    Entity.from(node, Location.from(node.valueParameters.first { it.isModifierParameter() })),
                    "Modifier parameter of composable functions must always be first"
                )
            )
        }
    }

    private fun KtParameter.isModifierParameter() = identifierName() == "modifier"
}
