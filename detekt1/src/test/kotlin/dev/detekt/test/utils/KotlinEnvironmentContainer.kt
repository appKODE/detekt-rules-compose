package dev.detekt.test.utils

import io.github.detekt.test.utils.createPsiFactory
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import java.io.File

class KotlinEnvironmentContainer internal constructor(
  val env: KotlinCoreEnvironment,
)

// One environment per test JVM instead of one per spec: detekt1 environments hold a Disposable
// that must be released, detekt2 containers are plain path holders with nothing to dispose.
// Built like detekt-test-utils' createEnvironment, but with the stdlib detekt 1.23.8's K1 compiler can read:
// the test runtime stdlib (2.2+, pulled in by kotest) has metadata it rejects, turning stdlib types into errors.
private val sharedEnvironment by lazy {
  createPsiFactory() // initializes the Kotlin language before the environment is created
  val configuration = CompilerConfiguration().apply {
    put(CommonConfigurationKeys.MODULE_NAME, "test_module")
    put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    addJvmClasspathRoot(File(System.getProperty("detekt1.stdlib")))
    put(JVMConfigurationKeys.JDK_HOME, File(System.getProperty("java.home")))
    configureJdkClasspathRoots()
  }
  val disposable = Disposer.newDisposable()
  Runtime.getRuntime().addShutdownHook(Thread { Disposer.dispose(disposable) })
  KotlinEnvironmentContainer(
    KotlinCoreEnvironment.createForTests(disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES),
  )
}

fun createEnvironment(): KotlinEnvironmentContainer = sharedEnvironment
