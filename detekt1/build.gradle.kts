import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

kotlin {
  compilerOptions {
    // lowest API version Kotlin still accepts; detekt 1.23.8 runs on Kotlin 2.0.21, older 1.x on older Kotlin
    // (compat with those is checked by scripts/cli-smoke-test.sh)
    apiVersion.set(KotlinVersion.KOTLIN_2_0)
    languageVersion.set(KotlinVersion.KOTLIN_2_0)
  }
}

dependencies {
  compileOnly(libs.detekt1.api.min)
  testImplementation(libs.detekt1.test)
  testImplementation(libs.bundles.koTest)
  testImplementation(project(":shared-tests"))
}

// stdlib for the type-resolved test environment, see dev.detekt.test.utils.KotlinEnvironmentContainer
val detekt1Stdlib: Configuration by configurations.creating { isTransitive = false }

dependencies {
  detekt1Stdlib(libs.detekt1.kotlin.stdlib)
}

// resolved when the test JVM starts, not at configuration time
class Detekt1StdlibArgument(@get:Classpath val stdlib: FileCollection) : CommandLineArgumentProvider {
  override fun asArguments() = listOf("-Ddetekt1.stdlib=${stdlib.singleFile.absolutePath}")
}

tasks.test {
  jvmArgumentProviders += Detekt1StdlibArgument(detekt1Stdlib)
}
