// Rules added in 2.2.0, all of them need type resolution.
package sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Column
import androidx.compose.ui.Row
import androidx.compose.ui.Text

@Composable
fun LoweredRead(visible: State<Boolean>, title: String) {
  val isVisible = visible.value
  Column {
    Text(title)
    Row {
      if (isVisible) Text("a")
      Text("b")
    }
  }
}

data class Profile(val name: String)

@Composable
fun ProfileCard(profile: Profile) {
  Text(profile.name)
}

class HeaderViewState(val title: String, val subtitle: String, val count: Int)

@Composable
fun Header(state: HeaderViewState) {
  Text(state.title)
}

@JvmInline
value class ImageUrl(val value: String)

fun AsyncImage(model: Any?) {}

@Composable
fun Avatar(url: ImageUrl) {
  AsyncImage(model = url)
  AsyncImage(model = url.value)
}

// The cases below need the callee, the type arguments and the receivers to be resolved.
@Composable
inline fun Items(crossinline itemContent: @Composable () -> Unit) {
  itemContent()
}

@Composable
fun LoweredIntoItem(visible: State<Boolean>, title: String) {
  val isVisible = visible.value
  Column {
    Text(title)
    Items {
      if (isVisible) Text("a")
      Text("b")
    }
  }
}

@Composable
fun ProfileList(profiles: List<Profile>) {
  Text(profiles.size.toString())
}

// stands in for the one of the standard library, whose metadata the oldest detekt-cli can't read
inline fun <T, R> with(receiver: T, block: T.() -> R): R = receiver.block()

@Composable
fun Titles(state: HeaderViewState, other: HeaderViewState) {
  with(state) {
    with(other) { Text(title) }
    Text(subtitle)
  }
  Text(other.toString())
}
