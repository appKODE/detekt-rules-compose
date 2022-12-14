package ru.kode.detekt.rule.compose

import io.gitlab.arturbosch.detekt.test.lint
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize

class UnnecessaryLayoutWrapperTest : ShouldSpec({
  should("not report when parent layout with any parameter has single child layout") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Row(modifier = Modifier.size(200.dp)) {
          Column {
      
          }
        }
      }
    """.trimIndent()

    val findings = UnnecessaryLayoutWrapper().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report when child layout with any parameter has its own single child layout") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Box(modifier = Modifier.size(200.dp)) {
          Box(modifier = Modifier.size(200.dp)) {
            Row {
      
            }
          }
        }
      }
    """.trimIndent()

    val findings = UnnecessaryLayoutWrapper().lint(code)

    findings.shouldBeEmpty()
  }

  should("not report when parent parameterless layout has 2 or more child layouts") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Box {
          Box {
      
          }
          Column {
      
          }
        }
      }
    """.trimIndent()

    val findings = UnnecessaryLayoutWrapper().lint(code)

    findings.shouldBeEmpty()
  }

  should("report when parent parameterless layout has single child layout") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Box {
          Row {
      
          }
        }
      }
    """.trimIndent()

    val findings = UnnecessaryLayoutWrapper().lint(code)

    findings.shouldHaveSize(1)
  }

  should("report when child parameterless layout has its own single child layout") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Column(modifier = Modifier.size(200.dp)) {
          Column {
            Box(modifier = Modifier.size(200.dp)) {
      
            }
          }
        }
      }
    """.trimIndent()

    val findings = UnnecessaryLayoutWrapper().lint(code)

    findings.shouldHaveSize(1)
  }

  should("report when any of child parameterless layouts have their own single child layout") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Box(modifier = Modifier.size(200.dp)) {
          Box {
            Box(modifier = Modifier.size(200.dp)) {
      
            }
          }
          Column {
      
          }
        }
      }
    """.trimIndent()

    val findings = UnnecessaryLayoutWrapper().lint(code)

    findings.shouldHaveSize(1)
  }

  should("report when 2 or more child parameterless layouts have their own single child layouts") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        Box(modifier = Modifier.size(200.dp)) {
          Box {
            Box(modifier = Modifier.size(200.dp)) {
      
            }
          }
          Column {
            Column(modifier = Modifier.size(200.dp)) {
      
            }
          }
        }
      }
    """.trimIndent()

    val findings = UnnecessaryLayoutWrapper().lint(code)

    findings.shouldHaveSize(2)
  }

  should("report when function has default lambda parameter with parameterless layout having child layout") {
    // language=kotlin
    val code = """
      @Composable
      fun Test(
        paramLambda: @Composable () -> Unit = {
          Box {
            Column {
        
            } 
          }
        }
      ) {
      
      }
    """.trimIndent()

    val findings = UnnecessaryLayoutWrapper().lint(code)

    findings.shouldHaveSize(1)
  }

  should("report when parameterless layout has child layout passed to another function's lambda argument") {
    // language=kotlin
    val code = """
      @Composable
      fun Test() {
        OtherComposable(
          slot1 = {
            Row {
              Row {
      
              }
            }
          },
          slot2 = {
            Column(horizontalAlignment = Alignment.End) {
              Box {
      
              }
            }
          },
          a = 1,
          b = "test"
        )
      }

      @Composable
      fun OtherComposable(
        slot1: @Composable () -> Unit,
        slot2: @Composable () -> Unit,
        a: Int,
        b: String
      ) {
        Text(text = "test")
      }
    """.trimIndent()

    val findings = UnnecessaryLayoutWrapper().lint(code)

    findings.shouldHaveSize(1)
  }
})
