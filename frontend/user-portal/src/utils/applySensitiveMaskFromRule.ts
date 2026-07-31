import type { FormField } from '@/components/formRendererHelpers'
import {
  isInputTypeEligibleForMask,
  normalizeSensitiveMaskConfig,
  type SensitiveMaskConfig,
} from '@/utils/sensitiveMask'

/** Pass-through for sub-table column props when mask config is present and eligible. */
export function pickSensitiveMaskProp(
  rProps: Record<string, unknown>,
): SensitiveMaskConfig | undefined {
  const inputType = typeof rProps.type === 'string' ? rProps.type : undefined
  if (!isInputTypeEligibleForMask(inputType)) return undefined
  const cfg = normalizeSensitiveMaskConfig(rProps.sensitiveMask)
  return cfg?.enabled ? cfg : undefined
}

/** Attach enabled sensitiveMask onto column passProps (text columns only). */
export function assignSensitiveMaskColumnProps(
  passProps: Record<string, unknown>,
  columnType: string | undefined,
  rProps: Record<string, unknown>,
): void {
  if (columnType !== 'text') return
  const mask = pickSensitiveMaskProp(rProps)
  if (!mask) return
  passProps.sensitiveMask = mask
  if (typeof rProps.type === 'string') passProps.type = rProps.type
}

/**
 * Copy designer Input sensitiveMask onto FormField when type is plain text Input.
 */
export function applySensitiveMaskFromRule(
  field: FormField,
  rule: { type?: string; props?: Record<string, unknown> },
): void {
  if (rule.type !== 'input') return
  const inputType = typeof rule.props?.type === 'string' ? rule.props.type : undefined
  if (!isInputTypeEligibleForMask(inputType)) return
  const cfg = normalizeSensitiveMaskConfig(rule.props?.sensitiveMask)
  if (cfg?.enabled) {
    field.sensitiveMask = cfg
  }
}
