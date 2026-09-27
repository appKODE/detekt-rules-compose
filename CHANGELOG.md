# Changelog

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
