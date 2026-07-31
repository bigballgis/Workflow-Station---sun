/**
 * Display-only sensitive masking for form Input fields.
 * Masked strings must never be written back into form models / submit payloads.
 * Mirror: frontend/user-portal/src/utils/sensitiveMask.ts (no shared package yet).
 */

import {
  applyMaskRanges,
  normalizeMaskRanges,
  type SensitiveMaskRange,
} from './sensitiveMaskRanges'

export type {
  MaskRangeSide,
  SensitiveMaskRange,
  SensitiveMaskRangeUi,
} from './sensitiveMaskRanges'

export {
  applyMaskRanges,
  maskRangeToUiRow,
  normalizeMaskRanges,
  resolveMaskIndex,
  uiRowToMaskRange,
} from './sensitiveMaskRanges'

export type SensitiveMaskPreset =
  | 'last4'
  | 'first4Last4'
  | 'first3Last4'
  | 'all'
  | 'custom'
  | 'ends'
  | 'ranges'

export interface SensitiveMaskConfig {
  enabled: boolean
  preset: SensitiveMaskPreset
  /** Keep-ends presets / custom: characters to leave plain at the start. */
  keepPrefix?: number
  /** Keep-ends presets / custom: characters to leave plain at the end. */
  keepSuffix?: number
  /** Ends-mask preset: characters to mask at the start. */
  maskPrefix?: number
  /** Ends-mask preset: characters to mask at the end. */
  maskSuffix?: number
  /** Ranges preset: half-open intervals to mask (supports negative indexes). */
  maskRanges?: SensitiveMaskRange[]
  maskChar?: string
  revealPlainOnFocus?: boolean
}

export const DEFAULT_SENSITIVE_MASK_CONFIG: SensitiveMaskConfig = {
  enabled: false,
  preset: 'all',
  keepPrefix: 0,
  keepSuffix: 4,
  maskPrefix: 3,
  maskSuffix: 4,
  maskRanges: [
    { start: 0, end: 3 },
    { start: -4 },
  ],
  maskChar: '*',
  revealPlainOnFocus: false,
}

const PRESETS: SensitiveMaskPreset[] = [
  'last4',
  'first4Last4',
  'first3Last4',
  'all',
  'custom',
  'ends',
  'ranges',
]

/** True when Input props.type is eligible (not textarea / password). */
export function isInputTypeEligibleForMask(inputType?: string | null): boolean {
  return inputType !== 'textarea' && inputType !== 'password'
}

export function isEndsMaskPreset(preset: SensitiveMaskPreset | undefined): boolean {
  return preset === 'ends'
}

export function isRangesMaskPreset(preset: SensitiveMaskPreset | undefined): boolean {
  return preset === 'ranges'
}

function clampInt(n: unknown, fallback: number): number {
  if (typeof n !== 'number' || !Number.isFinite(n)) return fallback
  return Math.trunc(n)
}

export function normalizeSensitiveMaskConfig(raw: unknown): SensitiveMaskConfig | null {
  if (raw == null || typeof raw !== 'object') return null
  const o = raw as Record<string, unknown>
  const preset = PRESETS.includes(o.preset as SensitiveMaskPreset)
    ? (o.preset as SensitiveMaskPreset)
    : 'all'
  const hasRangesKey = Array.isArray(o.maskRanges)
  const maskRanges = normalizeMaskRanges(o.maskRanges)
  return {
    enabled: o.enabled === true,
    preset,
    keepPrefix: Math.max(0, clampInt(o.keepPrefix, 0)),
    keepSuffix: Math.max(0, clampInt(o.keepSuffix, 4)),
    maskPrefix: Math.max(0, clampInt(o.maskPrefix, 3)),
    maskSuffix: Math.max(0, clampInt(o.maskSuffix, 4)),
    maskRanges: hasRangesKey
      ? maskRanges
      : [
          { start: 0, end: 3 },
          { start: -4, end: null },
        ],
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

/** Keep-ends style: plain prefix/suffix, masked middle. */
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
    case 'ends':
    case 'ranges':
      return { prefix: 0, suffix: 0 }
    default:
      return { prefix: 0, suffix: 4 }
  }
}

/** Ends-mask style: masked prefix/suffix, plain middle. */
export function resolveMaskEndCounts(config: SensitiveMaskConfig): { prefix: number; suffix: number } {
  if (config.preset !== 'ends') {
    return { prefix: 0, suffix: 0 }
  }
  return {
    prefix: Math.max(0, config.maskPrefix ?? 3),
    suffix: Math.max(0, config.maskSuffix ?? 4),
  }
}

/**
 * Apply mask to a raw string. Counts every character (including spaces).
 * Keep-ends: when shorter than keepPrefix+keepSuffix (or both zero), full mask.
 * Ends-mask: when shorter than maskPrefix+maskSuffix, full mask; when both zero, plain.
 * Ranges: mask characters covered by any normalized half-open interval; empty ranges → plain.
 */
export function applySensitiveMask(raw: string, config: SensitiveMaskConfig): string {
  if (raw == null || raw === '') return raw
  const maskChar = config.maskChar && config.maskChar.length > 0 ? config.maskChar.charAt(0) : '*'
  const len = raw.length

  if (isRangesMaskPreset(config.preset)) {
    const ranges = normalizeMaskRanges(config.maskRanges)
    return applyMaskRanges(raw, ranges, maskChar)
  }

  if (isEndsMaskPreset(config.preset)) {
    const { prefix: maskStart, suffix: maskEnd } = resolveMaskEndCounts(config)
    if (maskStart === 0 && maskEnd === 0) {
      return raw
    }
    if (maskStart + maskEnd >= len) {
      return maskChar.repeat(len)
    }
    return (
      maskChar.repeat(maskStart) +
      raw.slice(maskStart, len - maskEnd) +
      maskChar.repeat(maskEnd)
    )
  }

  const { prefix, suffix } = resolveKeepCounts(config)
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
