// UnnecessaryModifierArgument, added in 2.2.0, needs type resolution.
package sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.Text

@Composable
fun Captions(title: String, modifier: Modifier = Modifier) {
  Column(modifier) {
    Text(title, Modifier)
    Text(title, modifier = androidx.compose.ui.Modifier)
    Text(title)
  }
}
