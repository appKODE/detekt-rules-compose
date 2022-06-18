# Detekt rules for Jetpack Compose

[![MavenCentral](https://img.shields.io/maven-central/v/ru.kode/detekt-rules-compose?versionPrefix=1.0.1)](https://search.maven.org/artifact/ru.kode/detekt-rules-compose)

A set of [Detekt](https://detekt.dev) rules to help prevent common errors in projects using Jetpack Compose.

# Rules

Short summary of the rules in this rule set:

- `ReusedModifierInstance` finds usages of `modifier` parameter on non-top-level children of a composable function. This tends to happen during refactorings and often leads to incorrect rendering of a composable


- `UnnecessaryEventHandlerParameter` suggests hoisting event argument passing to the upper level which often simplifies individual composable components


- `ComposableEventParameterNaming` ensures that all event handler parameters of composable functions are named in the same Compose-like style, i.e. they have `on` prefix and do not use past tense


- `ModifierHeightWithText` suggests using `Modifier.heightIn()` instead of `Modifier.height()` on a layouts which have `Text` children, so that if the text turns out to be long and would wrap, layout will not cut it off


- `ModifierParameterPosition` ensures that `modifier` is declared as a first parameter


- `PublicComposablePreview` finds and reports composable previews which are not marked as `private`

Rules can be individually turned `on` or `off` in the configuration file.  
More detailed rule descriptions with code snippets can be found in the [Wiki](https://github.com/appKODE/detekt-rules-compose/wiki).

# Installation and configuration

Add detekt rules plugin in your `build.gradle` (or use any other [supported method](https://detekt.dev/docs/introduction/extensions#let-detekt-know-about-your-extensions)):
```
dependencies {
  detektPlugins("ru.kode:detekt-rules-compose:1.0.1")
}
```
and then add this configuration section to your `detekt-config.yml` to activate the rules:
```
compose:
  ReusedModifierInstance:
    active: true
  UnnecessaryEventHandlerParameter:
    active: true
  ComposableEventParameterNaming:
    active: true
  ModifierHeightWithText:
    active: true
  ModifierParameterPosition:
    active: true
  PublicComposablePreview:
    active: true
```

# Contributors

Maintained by [KODE](https://kode.ru) and [Contributors](https://github.com/appKODE/detekt-rules-compose/graphs/contributors) ❤️

# License
```
MIT License

Copyright (c) 2022 KODE LLC

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
