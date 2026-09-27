// One planted violation per rule; expected-findings.txt lists what each engine must report.
package sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.Preview
import androidx.compose.ui.Row
import androidx.compose.ui.Text
import androidx.compose.ui.dp

@Composable
fun ReusedModifier(modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Text("a", modifier = modifier)
  }
}

@Composable
fun EventHandler(item: String, onClick: (String) -> Unit) {
  Text(item)
  Row { onClick(item) }
}

@Composable
fun HeightWithText() {
  Row(modifier = Modifier.height(48.dp)) {
    Text("a")
  }
}

@Preview
@Composable
fun PublicPreview() {
  Text("a")
}

@Composable
fun EventNaming(clicked: () -> Unit, onValueChanged: () -> Unit) {
  Text("a")
}

@Composable
fun ParametersOrdering(modifier: Modifier = Modifier, title: String) {
  Text(title, modifier = modifier)
}

@Composable
fun WrongDefault(modifier: Modifier = Modifier.padding(1.dp)) {
  Text("a", modifier = modifier)
}

@Composable
fun MissingDefault(modifier: Modifier) {
  Text("a", modifier = modifier)
}

class Holder {
  @Composable
  fun Nested() {
    Text("a")
  }
}

@Composable
fun lowercaseComposable() {
  Text("a")
}

@Composable
fun Liftable(visible: Boolean) {
  Column {
    if (visible) {
      Text("a")
    }
  }
}
