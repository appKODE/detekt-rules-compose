package dev.detekt.test.utils

import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import io.github.detekt.test.utils.createEnvironment as createDetekt1Environment

class KotlinEnvironmentContainer internal constructor(
  val env: KotlinCoreEnvironment,
)

// One environment per test JVM instead of one per spec: detekt1 environments hold a Disposable
// that must be released, detekt2 containers are plain path holders with nothing to dispose.
private val sharedEnvironment by lazy {
  val wrapper = createDetekt1Environment()
  Runtime.getRuntime().addShutdownHook(Thread(wrapper::dispose))
  KotlinEnvironmentContainer(wrapper.env)
}

fun createEnvironment(): KotlinEnvironmentContainer = sharedEnvironment
