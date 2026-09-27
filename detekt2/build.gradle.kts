dependencies {
  compileOnly(libs.detekt2.api)
  compileOnly(libs.detekt2.psi)

  testImplementation(libs.detekt2.test)
  testImplementation(libs.detekt2.test.utils)
  testImplementation(libs.bundles.koTest)
  testImplementation(project(":shared-tests"))
}
