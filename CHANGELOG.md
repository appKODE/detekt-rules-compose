# Changelog

## 2.1.2

### Upgrading from 2.1.1

No configuration change is needed, the two new options default to the 2.1.1 behaviour.
`UnnecessaryEventHandlerParameter` reports fewer findings in some places and more in others (see below), so baseline
entries can become unused and, with `maxIssues: 0`, a build that passed on 2.1.1 can fail on existing code.

### Changes

* `UnnecessaryEventHandlerParameter`: a call of a function which only shares its name with an event handler
  parameter, like the extension `Modifier.onShown(...)` called inside a composable with an `onShown` parameter, is no
  longer mistaken for a call of the handler. The reference is resolved with type resolution; without it, a call with
  a receiver, with named arguments or with another number of arguments is not treated as a handler call (#46)
* `UnnecessaryEventHandlerParameter`: a state argument is reported only when every call of the handler passes the
  same expression at that position, as it already was for constants. A handler called as `onShown(id, position)` in
  one place and `onShown(visible, 0)` in another is no longer reported, the parent can't supply these values
  itself (#49). As a consequence:
  * findings disappear when the calls disagree or when the handler is also passed on as a value
    (`Child(onClick = onClick)`)
  * several calls of one handler and several state arguments of one call now give one finding per handler instead of
    one per call and per argument, its message lists all the parameters
  * `onClick.invoke(data.id)` and `onClick?.invoke(data.id)` are now reported like `onClick(data.id)`
  * a constant argument is now reported when the handler is checked for null before the call, as in
    `if (onClick != null) onClick(1)`
* `ComposableParametersOrdering`: overriding and `actual` functions are no longer reported, their order is dictated
  by the overridden or `expect` declaration, which is still checked (#48)
* `ComposableParametersOrdering`: new `allowTrailingEventHandlers` option (default `true`). With `false` a required
  event handler (a non-composable lambda without a receiver which returns `Unit`) placed after optional parameters is
  reported, as in `fun Item(id: String, modifier: Modifier = Modifier, onClick: () -> Unit)`. Lambdas with a
  receiver, lambdas returning a value and parameters named in `trailingSlotNames` are not reported (#50)
* `MissingModifierDefaultValue`: new `checkAbstractFunctions` option (default `false`). With `true` abstract and open
  functions and functions of interfaces are checked too. Turn it on only if the project compiles with the K2
  compiler and Kotlin language version 2.1 (abstract functions) or 2.2 (open functions), older setups reject default
  values there. Overriding functions, `actual` functions and the function of a `fun interface` are never
  reported (#47)

## 2.1.1 - 2026-09-29

### Changes

* `ConditionCouldBeLifted`: a layout that also calls a composable outside the `if` through a qualified expression,
  like `slot?.invoke()`, `slot.invoke()` or `slot?.let { it() }`, is no longer reported, lifting the condition would
  drop that call. More generally, any statement other than a declaration that contains a composable call now blocks
  the report, like `items.forEach { Text(it) }`, `repeat(n) { Text() }` or `when (x) { a -> Text() }`, so findings
  can disappear and baseline entries for them become unused

## 2.1.0 - 2026-09-27

### Upgrading from 2.0.0

No configuration change is needed. `ComposableParametersOrdering` and `UnnecessaryEventHandlerParameter` check more
than they did in 2.0.0 (see below), so with `maxIssues: 0` a build that passed on 2.0.0 can fail on existing code:
fix the findings, suppress them or regenerate the baseline. To keep the 2.0.0 behaviour for constants, set
`reportConstantArguments: false` on `UnnecessaryEventHandlerParameter`.

`ComposableParametersOrdering` no longer reports a composable slot placed before other parameters and its order
message no longer ends with ", composable slots"; baseline entries for it become unused and go away when the
baseline is regenerated.

### Changes

* `ModifierHeightWithText`: `height(IntrinsicSize.Min)` and `height(IntrinsicSize.Max)` (also imported as `Min`/`Max`
  or fully qualified) are no longer reported, they size to the content and can't clip text (#25, #34)
* `ReusedModifierInstance` and `UnnecessaryEventHandlerParameter`: a name shadowed by a lambda or local function
  parameter, a local `val` (unless derived from the name itself, like `val modifier = modifier.padding(4.dp)`), a
  `for` loop variable, a catch parameter, a `when` subject or a local object property is no longer mistaken for the
  composable's parameter (#30, #41)
* `ConditionCouldBeLifted`: a condition on the content lambda's own parameters (including its implicit `it`, resolved
  with type resolution) or on local `val`s declared in it before the `if` is no longer reported, it can't be lifted
  out of the lambda; names from lambdas enclosing the layout call still are (#31)
* `TopLevelComposableFunctions`: new `allowInInterfaces` option (default `false`) allows composables declared in
  interfaces; classes are still reported (#29)
* New rule `UnnecessaryLayoutWrapper` (disabled by default), based on #23 by Sergey Shevtsov: reports a `Box`,
  `Column` or `Row` without parameters which only wraps a single `Box`, `Column` or `Row`
* `ComposableParametersOrdering`: composable slots are no longer forced to the end, a required slot can stay among
  the required parameters and an optional one among the optional parameters, as in Material's `TextField` and
  `AlertDialog`. Instead, slots named in the new `trailingSlotNames` option (default `[content]`) must be the last
  parameter, and after the first optional parameter only the last parameter may be a required composable slot, so
  it can be passed as a trailing lambda. The order message drops ", composable slots". A required event handler or
  other non-slot lambda after the optional parameters is still not reported, even when it isn't the last
  parameter (#40)
* `UnnecessaryEventHandlerParameter`: a constant argument of an event handler (a literal, a string without templates,
  a Kotlin `const val`, an enum entry or an object, like `onUserIntent(Intent.Back)`) is now reported when every
  call (including `onUserIntent?.invoke(...)`) passes the same constant at that position and the handler isn't also
  passed on as a value, the parent can pass it itself. One finding per handler, listing all such constants; different
  constants (`onCheckedChange(true)` and `onCheckedChange(false)`) are not reported. New option `reportConstantArguments` (default `true`) turns this off. Composables without state
  parameters are now checked too (#3)

## 2.0.0 - 2026-09-27

### Upgrading from 1.4.0

No migration is needed on detekt 1: keep `ru.kode:detekt-rules-compose` and bump the version. The rule set id,
rule ids and configuration keys are unchanged. Two things can still show up after the upgrade:

* **New findings.** Expression-bodied composables (`@Composable fun Item() = Column { ... }`) are now checked by
  `ConditionCouldBeLifted`, `ModifierHeightWithText` and `ReusedModifierInstance`, and nullable event handlers
  (`onClick: (() -> Unit)? = null`) by `ComposableEventParameterNaming` and `UnnecessaryEventHandlerParameter`.
  With `maxIssues: 0` a build that passed on 1.4.0 can fail on existing code: fix the findings, suppress them or
  regenerate the baseline.
* **Fewer false positives.** `@Composable fun Screen() = Box {}`, an explicit `kotlin.Unit` return type, event
  handlers like `onSpeed`/`onFeed`, and `modifier: Modifier = Modifier.Companion` are no longer reported. Baseline
  entries for them become unused; they do no harm and go away when the baseline is regenerated.

### Changes

* Two artifacts, one per detekt engine:
  * `ru.kode:detekt-rules-compose` for detekt `1.22.0`–`1.23.8`, same coordinates as 1.4.0: a drop-in upgrade
  * `ru.kode:detekt-rules-compose-detekt2` for detekt `2.0.0-alpha.6`
* Both are built from the same rule sources (shared analyzers for all 11 rules) with an engine-specific
  type resolution adapter: `BindingContext` on detekt 1, the Analysis API on detekt 2
* Rule ids, rule set id `compose`, configuration keys, and, for code 1.4.0 already reported, messages and finding positions are unchanged
  (checked by `scripts/cli-smoke-test.sh`, which runs the 1.4.0 jar and both new jars through the real detekt CLIs)
* Jars no longer bundle anything but the rules: no shadow jar, no Kotlin stdlib
* Built with Kotlin `2.4.20`, Gradle `9.8.0`; the detekt 1 artifact is compiled against detekt-api `1.23.0` so it keeps running on older detekt 1.x
* `ConditionCouldBeLifted`:
  * a `content` lambda passed positionally before a trailing lambda (`Card({ if (x) Text() }) {}`) is now
    checked; 1.4.0 only looked at the last argument
  * on detekt 2, calls of a `@Composable` slot lambda (`icon()`) count as composable calls
* `ComposableFunctionName`: no longer asks for lower case on `@Composable fun Screen() = Box {}` (an expression
  body calling an upper-case function is skipped, its return type is unknown without type resolution) and on an
  explicit `kotlin.Unit` return type
* Nullable function-type parameters:
  * nullable event handlers (`onClick: (() -> Unit)? = null`) are now checked by `ComposableEventParameterNaming`
    and `UnnecessaryEventHandlerParameter`; the latter keeps the `?` in the type it suggests
  * nullable composable slots written as `(@Composable () -> Unit)?` are recognized as slots (not event handlers),
    so `ComposableParametersOrdering` checks their position
* `ModifierDefaultValue`: `Modifier.Companion`, `androidx.compose.ui.Modifier` and
  `androidx.compose.ui.Modifier.Companion` are accepted as default values, like `Modifier`
* `ComposableEventParameterNaming`: the past-tense check no longer fires on present-tense verbs ending in "ed"
  (`onSpeed`, `onFeed`, `onNeed`, `onEmbed`, ...) nor on Compose's own `onFocusChanged` and `onPlaced`
* `ConditionCouldBeLifted`, `ModifierHeightWithText` and `ReusedModifierInstance` now also check expression-bodied
  composables (`@Composable fun Item() = Column { ... }`); 1.4.0 only looked at block bodies, so these are new
  findings on upgrade

## 1.4.0 - 2024-08-22

* Merge `ModifierParameterPosition` rule into the `ComposableParametersOrdering` rule. After upgrading to the new version of this ruleset, `ModifierParameterPosition` should be removed from `detekt-config.yml` file
* Add support for building fat jars
* Bugfixes

## 1.3.0 - 2023-07-20

* Several rules (`ReusedModifierInstance`, `UnnecessaryEventHandlerParameter`) were switched to run only when Detekt is working in a [type resolution mode](https://detekt.dev/docs/gettingstarted/type-resolution/). This is required to make these rules more robust and have less false positives (such as #5, #13). Expect more rules in this ruleset to support only running in the _type resolution_ mode
* New **experimental** rule: `ConditionCouldBeLifted`

    It will detect cases when if-condition inside a composable layout call
    could be "lifted up" and the whole call could be moved into that
    conditional expression, for example:

    ```
    Column {
      if (x == 3) {
        Text("1")
        Text("2")
      }
    }
    ```

    could be turned into

    ```
    if (x == 3) {
      Column {
        Text("1")
        Text("2")
      }
    }
    ```

    At the moment it tries to be extra careful to avoid reporting any
    potentially side-effecting code (for example if `Column` in the example
    above would have some `Modifier` affecting a parent layout, this
    conditional-lifting change wouldn't be correct), and by being "extra"
    careful it can miss some potential cases for optimisation.

    This may be improved in future.
* Bug fixes

## 1.2.2 - 2022-09-30

* New rule: `ComposeFunctionName` ensures that Composable functions which return Unit should start with upper-case while the ones that return a value should start with lower case
* Improve `ReusedModifierInstance` to detekt more cases, now it works correctly for cases when a composable call is wrapped in conditional (and other expressions)

## 1.2.1 - 2022-08-14

* Ignore composable functions in interfaces/abstract classes for `MissingModifierDefaultValue` (#11)

## 1.2.0 - 2022-08-14

* New rule: `TopLevelComposableFunctions` ensures that all composable functions are top-level functions (disabled by default)
* Ignore overridden functions in `MissingModifierDefaultValue` (#11)
* Fix exception in UnnecessaryEventHandlerParameter (#14)

## 1.1.0 - 2022-07-02

* New rule: `ComposableParametersOrdering` suggests separating required an optional parameters of the composable function into groups
* New rule: `ModifierDefaultValue` ensures that `modifier` parameter has a correct default value
* New rule: `MissingModifierDefaultValue` checks if `modifier` default value is specified
* Improved error messages (#2)
* Fixed false positive in `ComposableEventParameterNaming` (#6)

## 1.0.1 - 2022-05-26

* Update `ModifierParameterPosition` rule to better follow Compose style: `modifier` parameter should be a first _optional_ parameter, i.e. it should come after _required_ parameters and before _optional_ parameters


## 1.0.0 - 2022-05-19

* Initial release
