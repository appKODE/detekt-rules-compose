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
