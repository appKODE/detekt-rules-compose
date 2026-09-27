// ConditionCouldBeLifted cases whose 1.4.0 behaviour the 2.x port must keep.
package sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.Column
import androidx.compose.ui.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.Text
import androidx.compose.ui.dp

@Composable
fun PositionalModifier(visible: Boolean) {
  Column(Modifier.padding(4.dp)) {
    if (visible) {
      Text("a")
    }
  }
}

@Composable
fun NonComposableContent(visible: Boolean) {
  LazyColumn {
    if (visible) {
      item { Text("a") }
    }
  }
}

@Composable
fun ElseIfWithoutUi(a: Boolean, b: Boolean) {
  Column {
    if (a) {
      Text("a")
    } else if (b) {
      println("no ui")
    }
  }
}

@Composable
fun SlotLambda(visible: Boolean, icon: @Composable () -> Unit) {
  Column {
    if (visible) {
      icon()
    }
  }
}
