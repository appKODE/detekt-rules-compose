# Changelog

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

