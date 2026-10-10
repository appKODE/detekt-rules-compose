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
