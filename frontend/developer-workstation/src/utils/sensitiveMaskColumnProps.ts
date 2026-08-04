/**
 * Sub-table column prop helpers for sensitive masking (DW Form Preview only).
 */

import {
  isInputTypeEligibleForMask,
  normalizeSensitiveMaskConfig,
  type SensitiveMaskConfig,
} from './sensitiveMask'

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
