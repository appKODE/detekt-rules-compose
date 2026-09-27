import com.vanniktech.maven.publish.MavenPublishBaseExtension
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.spotless)
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.vanniktech.publish) apply false
  alias(libs.plugins.kover) apply false
}

allprojects {
  repositories {
    mavenCentral()
  }
}

subprojects {
  apply(plugin = "org.jetbrains.kotlin.jvm")

  extensions.configure<KotlinJvmProjectExtension>("kotlin") {
    jvmToolchain(11)
  }

  tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
      showExceptions = true
      events = setOf(org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED)
      exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
  }

  // detekt1/detekt2: rule jars built from shared/ sources against each engine's own compiler
  if (project.name != "shared-tests") {
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.vanniktech.maven.publish")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    extensions.configure<KotlinJvmProjectExtension>("kotlin") {
      sourceSets.named("main") { kotlin.srcDir(rootProject.file("shared/src/main/kotlin")) }
      sourceSets.named("test") { kotlin.srcDir(rootProject.file("shared-tests/src/suite/kotlin")) }
    }

    extensions.configure<KoverProjectExtension> {
      reports {
        filters {
          includes {
            // shared rule logic and the engine's ComposeSemantic implementation
            classes("ru.kode.detekt.rule.compose.shared.*", "*ComposeSemantic")
          }
        }
        verify {
          rule { minBound(90) }
        }
      }
    }

    extensions.configure<MavenPublishBaseExtension> {
      publishToMavenCentral()
      signAllPublications()
    }
  }
}

spotless {
  kotlin {
    target("**/*.kt")
    // smoke/ fixtures are expected-output data: reformatting would shift the reported positions
    targetExclude("**/build/**/*.*", "smoke/**")
    ktlint(libs.versions.ktlint.get())
      .editorConfigOverride(
        mapOf(
          "indent_size" to "2",
          "max_line_length" to "120",
          "ktlint_standard_function-expression-body" to "disabled",
          "ktlint_standard_class-signature" to "disabled",
        ),
      )
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    target("**/*.gradle.kts")
    ktlint(libs.versions.ktlint.get())
      .editorConfigOverride(
        mapOf("indent_size" to "2", "max_line_length" to "120"),
      )
    trimTrailingWhitespace()
    endWithNewline()
  }
}
