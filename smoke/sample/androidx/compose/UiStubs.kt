package androidx.compose.ui

import androidx.compose.runtime.Composable

annotation class Preview

data class Dp(val v: Int)
val Int.dp get() = Dp(this)

interface Modifier {
  fun padding(all: Dp): Modifier = this
  fun height(height: Dp): Modifier = this
  companion object : Modifier
}

@Composable fun Row(modifier: Modifier = Modifier, content: @Composable () -> Unit) {}
@Composable fun Column(modifier: Modifier = Modifier, content: @Composable () -> Unit) {}
@Composable fun Text(text: String, modifier: Modifier = Modifier) {}

interface LazyListScope {
  fun item(content: @Composable () -> Unit)
}

@Composable fun LazyColumn(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {}
