package ru.kode.detekt.rule.compose

import dev.detekt.api.Config
import dev.detekt.test.TestConfig
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import ru.kode.detekt.rule.compose.snippet.composeSnippet

class UnwrappedValueClassImageModelTest : ShouldSpec({
  val environment = createEnvironment()

  fun lint(code: String, config: Config = Config.empty) =
    UnwrappedValueClassImageModel(config).lintWithContext(environment, code)

  should("report a value class passed as the model of an image function") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test(url: ImageUrl, optionalUrl: ImageUrl?, user: User) {
        AsyncImage(model = url, contentDescription = null)
        AsyncImage(optionalUrl, contentDescription = null)
        SubcomposeAsyncImage(contentDescription = null, model = user.avatar)
        val painter = rememberAsyncImagePainter(ImageUrl("a"))
      }
      """,
    )

    val findings = lint(code)

    findings.map { it.message } shouldBe listOf("url", "optionalUrl", "user.avatar", "ImageUrl(\"a\")").map {
      "\"$it\" is a value class, Coil can't load an image from it. Pass its underlying value instead"
    }
    findings[0].shouldStartAt(code, "url, contentDescription")
    findings[1].shouldStartAt(code, "optionalUrl, contentDescription")
    findings[2].shouldStartAt(code, "user.avatar)")
    findings[3].shouldStartAt(code, "ImageUrl(\"a\"))")
  }

  should("report a value class passed as the data of an image request") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test(url: ImageUrl) {
        AsyncImage(model = ImageRequest.Builder().crossfade(true).data(url).build(), contentDescription = null)
      }

      fun request(imageUrl: ImageUrl) = ImageRequest.Builder().data(data = imageUrl).build()
      """,
    )

    val findings = lint(code)

    findings.map { it.message } shouldBe listOf("url", "imageUrl").map {
      "\"$it\" is a value class, Coil can't load an image from it. Pass its underlying value instead"
    }
    findings[0].shouldStartAt(code, "url).build()")
    findings[1].shouldStartAt(code, "imageUrl).build()")
  }

  should("report the data of an image request builder which is not the receiver of a call chain") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      fun applied(appliedUrl: ImageUrl) = ImageRequest.Builder().apply { data(appliedUrl) }.build()

      fun stored(storedUrl: ImageUrl): ImageRequest {
        val builder = ImageRequest.Builder()
        builder.data(storedUrl)
        return builder.build()
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf("appliedUrl", "storedUrl").map {
      "\"$it\" is a value class, Coil can't load an image from it. Pass its underlying value instead"
    }
  }

  should("report a model which is smart cast to a value class") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test(model: Any, other: Any?) {
        if (model is ImageUrl) AsyncImage(model = model, contentDescription = null)
        if (other is ImageUrl?) AsyncImage(model = other, contentDescription = null)
      }
      """,
    )

    lint(code).map { it.message } shouldBe listOf("model", "other").map {
      "\"$it\" is a value class, Coil can't load an image from it. Pass its underlying value instead"
    }
  }

  should("not report an unwrapped value class and other models") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      @Composable
      fun Test(url: ImageUrl, text: String, wrapper: Wrapper, any: Any?, unsigned: UInt, result: Result<String>) {
        AsyncImage(model = url.value, contentDescription = null)
        AsyncImage(model = unsigned, contentDescription = null)
        AsyncImage(model = result, contentDescription = null)
        AsyncImage(model = text, contentDescription = null)
        AsyncImage(model = wrapper, contentDescription = null)
        AsyncImage(model = any, contentDescription = null)
        AsyncImage(model = missing, contentDescription = null)
        AsyncImage(model = ImageRequest.Builder().data(url.value).build(), contentDescription = null)
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("not report a value class passed to other functions and parameters") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      class Payload { fun data(data: Any?) = this }
      fun coilImageRequestFactory() = Payload()
      class Builder { fun data(data: Any?) = this }
      fun data(data: Any?) {}
      fun Image(model: Any?) {}
      fun AsyncImage(url: ImageUrl) {}

      @Composable
      fun Test(url: ImageUrl) {
        Image(model = url)
        AsyncImage(url)
        Payload().data(url)
        coilImageRequestFactory().data(url)
        Builder().data(url)
        data(url)
        ImageRequest.Builder().apply { missing.data(url) }
        UnknownImage(model = url)
      }
      """,
    )

    lint(code).shouldBeEmpty()
  }

  should("use the configured image functions") {
    // language=kotlin
    val code = composeSnippet(
      STUBS + """
      fun Image(model: Any?) {}

      @Composable
      fun Test(url: ImageUrl) {
        Image(model = url)
        AsyncImage(model = url, contentDescription = null)
      }
      """,
    )

    val findings = lint(code, TestConfig("imageFunctions" to listOf("Image")))

    findings.size shouldBe 1
    findings.single().shouldStartAt(code, "url)")
  }
})

private const val STUBS = """
      @JvmInline
      value class ImageUrl(val value: String)
      class Wrapper(val value: String)
      class User(val avatar: ImageUrl)

      fun AsyncImage(model: Any?, contentDescription: String?) {}
      fun SubcomposeAsyncImage(model: Any?, contentDescription: String?) {}
      fun rememberAsyncImagePainter(model: Any?): Any = Any()

      class ImageRequest {
        class Builder {
          fun data(data: Any?) = this
          fun crossfade(enable: Boolean) = this
          fun build() = ImageRequest()
        }
      }
"""
