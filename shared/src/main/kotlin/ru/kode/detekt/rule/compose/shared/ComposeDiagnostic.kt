package ru.kode.detekt.rule.compose.shared

import org.jetbrains.kotlin.psi.KtElement

data class ComposeDiagnostic(
  val message: String,
  val anchor: KtElement,
  /** Report at the name of [anchor] (a named declaration), like detekt's `Entity.atName`. */
  val atName: Boolean = false,
)
