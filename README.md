# Detekt rules for Jetpack Compose

[![MavenCentral](https://img.shields.io/maven-central/v/ru.kode/detekt-rules-compose?versionPrefix=1.3.0)](https://search.maven.org/artifact/ru.kode/detekt-rules-compose)

A set of [Detekt](https://detekt.dev) rules to help prevent common errors in projects using Jetpack Compose.

# Rules

Here are some highlights of rules in this rule set:

- `ReusedModifierInstance` finds usages of `modifier` parameter on non-top-level children of a composable function. This tends to happen during refactorings and often leads to incorrect rendering of a composable

- `UnnecessaryEventHandlerParameter` suggests hoisting event argument passing to the upper level which often simplifies individual composable components

- `ModifierHeightWithText` suggests using `Modifier.heightIn()` instead of `Modifier.height()` on a layouts which have `Text` children, so that if the text turns out to be long and would wrap, layout will not cut it off

- `ComposableEventParameterNaming` ensures that all event handler parameters of composable functions are named in the same Compose-like style, i.e. they have `on` prefix and do not use past tense

- `ComposableParametersOrdering` suggests separating required an optional parameters of the composable function into groups

- `ModifierParameterPosition` ensures that `modifier` is declared as a first parameter

- `ModifierDefaultValue` ensures that `modifier` parameter has a correct default value

- `MissingModifierDefaultValue` checks if `modifier` default value is specified

- `PublicComposablePreview` finds and reports composable previews which are not marked as `private`

- `TopLevelComposableFunctions` ensures that all composable functions are top-level functions (disabled by default)

- `ComposableFunctionName` ensures that Composable functions which return Unit should start with upper-case while the ones that return a value should start with lower case

- and others...

Rules can be individually turned `on` or `off` in the configuration file.  
More detailed rule descriptions with code snippets can be found in the [Wiki](https://github.com/appKODE/detekt-rules-compose/wiki).

# Installation and configuration

Add detekt rules plugin in your `build.gradle` (or use any other [supported method](https://detekt.dev/docs/introduction/extensions#let-detekt-know-about-your-extensions)):
```
dependencies {
  detektPlugins("ru.kode:detekt-rules-compose:1.3.0")
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
  ComposableParametersOrdering:
    active: true
  ModifierHeightWithText:
    active: true
  ModifierParameterPosition:
    active: true
  ModifierDefaultValue:
    active: true
  MissingModifierDefaultValue:
    active: true
  PublicComposablePreview:
    active: true
  TopLevelComposableFunctions:
    active: true
    allowInObjects: false
  ComposableFunctionName:
    active: true
  ConditionCouldBeLifted:
    active: true
    ignoreCallsWithArgumentNames: [ 'modifier', 'contentAlignment' ]
```

## Detekt configuration for Compose

Detekt manual contains few useful tips on how to configure Detekt in Compose-based projects, [check them out](https://detekt.dev/docs/introduction/compose/)!

# Contributors

Maintained by [KODE](https://kode.ru) and [Contributors](https://github.com/appKODE/detekt-rules-compose/graphs/contributors) ❤️

# License
```
MIT License

Copyright (c) 2024 KODE LLC

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
