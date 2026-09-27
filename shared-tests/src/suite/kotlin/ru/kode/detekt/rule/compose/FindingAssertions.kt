package ru.kode.detekt.rule.compose

import dev.detekt.api.Finding
import io.kotest.matchers.shouldBe

/**
 * Asserts the finding starts exactly at [marker], which must occur once in [code]. Line and column are 1-based,
 * as detekt reports them; computing them from the code keeps assertions independent of the snippet preamble.
 */
fun Finding.shouldStartAt(code: String, marker: String) {
  val offset = code.indexOf(marker)
  check(offset >= 0 && offset == code.lastIndexOf(marker)) { "Marker must occur exactly once: $marker" }
  val before = code.substring(0, offset)
  val expected = "${before.count { it == '\n' } + 1}:${offset - before.lastIndexOf('\n')}"
  val source = entity.location.source
  "${source.line}:${source.column}" shouldBe expected
}
