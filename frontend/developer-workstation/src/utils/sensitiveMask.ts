/**
 * Display-only sensitive masking for form Input fields.
 * Masked strings must never be written back into form models / submit payloads.
 * Mirror: frontend/user-portal/src/utils/sensitiveMask.ts (no shared package yet).
 */

export type SensitiveMaskPreset = 'last4' | 'first4Last4' | 'first3Last4' | 'all' | 'custom'

export interface SensitiveMaskConfig {
  enabled: boolean
  preset: SensitiveMaskPreset
  keepPrefix?: number
  keepSuffix?: number
  maskChar?: string
  revealPlainOnFocus?: boolean
}

export const DEFAULT_SENSITIVE_MASK_CONFIG: SensitiveMaskConfig = {
  enabled: false,
  preset: 'last4',
  keepPrefix: 0,
  keepSuffix: 4,
  maskChar: '*',
  revealPlainOnFocus: false,
}

const PRESETS: SensitiveMaskPreset[] = ['last4', 'first4Last4', 'first3Last4', 'all', 'custom']

/** True when Input props.type is eligible (not textarea / password). */
export function isInputTypeEligibleForMask(inputType?: string | null): boolean {
  return inputType !== 'textarea' && inputType !== 'password'
}

export function normalizeSensitiveMaskConfig(raw: unknown): SensitiveMaskConfig | null {
  if (raw == null || typeof raw !== 'object') return null
  const o = raw as Record<string, unknown>
  const preset = PRESETS.includes(o.preset as SensitiveMaskPreset)
    ? (o.preset as SensitiveMaskPreset)
    : 'last4'
  return {
    enabled: o.enabled === true,
    preset,
    keepPrefix: typeof o.keepPrefix === 'number' ? Math.max(0, Math.floor(o.keepPrefix)) : 0,
    keepSuffix: typeof o.keepSuffix === 'number' ? Math.max(0, Math.floor(o.keepSuffix)) : 4,
    maskChar: typeof o.maskChar === 'string' && o.maskChar.length > 0 ? o.maskChar.charAt(0) : '*',
    revealPlainOnFocus: o.revealPlainOnFocus === true,
  }
}

/** Config is active for display/runtime (enabled + eligible input type). */
export function isSensitiveMaskActive(
  config: SensitiveMaskConfig | null | undefined,
  inputType?: string | null,
): boolean {
  return !!config?.enabled && isInputTypeEligibleForMask(inputType)
}

export function resolveKeepCounts(config: SensitiveMaskConfig): { prefix: number; suffix: number } {
  switch (config.preset) {
    case 'last4':
      return { prefix: 0, suffix: 4 }
    case 'first4Last4':
      return { prefix: 4, suffix: 4 }
    case 'first3Last4':
      return { prefix: 3, suffix: 4 }
    case 'all':
      return { prefix: 0, suffix: 0 }
    case 'custom':
      return {
        prefix: Math.max(0, config.keepPrefix ?? 0),
        suffix: Math.max(0, config.keepSuffix ?? 0),
      }
    default:
      return { prefix: 0, suffix: 4 }
  }
}

/**
 * Apply mask to a raw string. Counts every character (including spaces).
 * When the value is shorter than keepPrefix+keepSuffix, the entire value is masked.
 */
export function applySensitiveMask(raw: string, config: SensitiveMaskConfig): string {
  if (raw == null || raw === '') return raw
  const maskChar = config.maskChar && config.maskChar.length > 0 ? config.maskChar.charAt(0) : '*'
  const { prefix, suffix } = resolveKeepCounts(config)
  const len = raw.length
  if (prefix + suffix >= len || (prefix === 0 && suffix === 0)) {
    return maskChar.repeat(len)
  }
  const mid = len - prefix - suffix
  return raw.slice(0, prefix) + maskChar.repeat(mid) + raw.slice(len - suffix)
}

export interface MaskDisplayContext {
  /** Field is readonly / disabled (view mode). */
  isReadonly: boolean
  /** Input currently focused (editable only). */
  isFocused: boolean
  /** Table / list cell (always mask when active). */
  isListCell?: boolean
}

/**
 * Whether the UI should show the masked string.
 * - List / readonly: always mask when active
 * - Editable + revealPlainOnFocus: mask when not focused
 * - Editable + reveal off: never mask (plain while editing)
 */
export function shouldShowMaskedDisplay(
  config: SensitiveMaskConfig | null | undefined,
  ctx: MaskDisplayContext,
  inputType?: string | null,
): boolean {
  if (!isSensitiveMaskActive(config, inputType)) return false
  if (ctx.isListCell || ctx.isReadonly) return true
  if (config!.revealPlainOnFocus) return !ctx.isFocused
  return false
}

export function formatMaskedDisplay(
  raw: unknown,
  config: SensitiveMaskConfig | null | undefined,
  ctx: MaskDisplayContext,
  inputType?: string | null,
): string {
  if (raw == null || raw === '') return raw == null ? '' : String(raw)
  const s = String(raw)
  if (!shouldShowMaskedDisplay(config, ctx, inputType)) return s
  return applySensitiveMask(s, config!)
}

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
