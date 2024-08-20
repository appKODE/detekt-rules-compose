# Changelog

## 1.4.0 - ?

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

