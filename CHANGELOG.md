# Changelog

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

