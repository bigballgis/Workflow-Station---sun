/**
 * BusinessLogicEngine — Field linkage processing (Task 4.8).
 */

import type { LinkageRule } from '../formRendererHelpers'
import type { LinkageResult } from './types'
import { evaluateCondition } from './conditions'

// ─── processLinkage (Task 4.8) ──────────────────────────────────────────────

/**
 * Process a single linkage rule against the current form state.
 *
 * - option-filtering: uses filterConfig (declarative, not JS expression)
 * - value-auto-fill: uses valueMapping dictionary lookup
 * - field-state-change: uses stateConfig with ConditionExpression
 *
 * If a referenced field doesn't exist, logs a warning and skips.
 */
export function processLinkage(
  linkage: LinkageRule,
  sourceValue: unknown,
  formData: Record<string, unknown>,
  targetOptions?: Array<{ label: string; value: unknown; [key: string]: unknown }>,
): LinkageResult {
  const result: LinkageResult = {}

  if (!(linkage.sourceField in formData)) {
    console.warn(
      `[BusinessLogicEngine] Linkage references non-existent source field "${linkage.sourceField}", skipping.`,
    )
    return result
  }

  switch (linkage.linkageType) {
    case 'option-filtering': {
      if (!linkage.filterConfig || !targetOptions) {
        return result
      }
      const { filterField, filterOperator } = linkage.filterConfig
      result.filteredOptions = targetOptions.filter((option) => {
        const optionVal = option[filterField]
        switch (filterOperator) {
          case 'equals':

            return optionVal == sourceValue
          case 'contains': {
            if (typeof optionVal === 'string' && sourceValue != null) {
              return optionVal.includes(String(sourceValue))
            }
            if (Array.isArray(optionVal) && sourceValue != null) {
              return optionVal.includes(sourceValue)
            }
            return false
          }
          case 'in': {
            if (Array.isArray(sourceValue)) {
              return sourceValue.includes(optionVal)
            }
            return false
          }
          default:
            return true
        }
      })
      break
    }

    case 'value-auto-fill': {
      if (!linkage.valueMapping) {
        return result
      }
      const key = String(sourceValue)
      if (key in linkage.valueMapping) {
        result.autoFillValue = linkage.valueMapping[key]
      }
      break
    }

    case 'field-state-change': {
      if (!linkage.stateConfig) {
        return result
      }
      const conditionMet = evaluateCondition(linkage.stateConfig.condition, formData)
      result.stateChange = {
        disabled: conditionMet ? linkage.stateConfig.disabled : undefined,
        required: conditionMet ? linkage.stateConfig.required : undefined,
      }
      break
    }
  }

  return result
}
