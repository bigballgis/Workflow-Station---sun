/**
 * Fixed-point decimal arithmetic for computed fields.
 *
 * WHY bigint and not "scaled JS number": the Java side is java.math.BigDecimal, which is exactly
 * (unscaledValue, scale). Modelling the same pair with bigint makes the two interpreters
 * structurally identical instead of merely approximately equal — there is no MAX_SAFE_INTEGER
 * ceiling to reason about and no float rounding to diverge on. `0.1 + 0.2` is 0.3 here, not
 * 0.30000000000000004.
 *
 * Rounding is HALF_UP (away from zero on ties) everywhere, matching RoundingMode.HALF_UP.
 */
import type { Decimal } from './types'

/**
 * Working precision for division / AVG / SQRT, which can produce non-terminating decimals.
 * FIXED CONTRACT: both interpreters must use this exact value or results drift. The final
 * write applies the target field's own scale on top of this.
 */
export const DIVISION_SCALE = 10

const POW10: bigint[] = []

function pow10(n: number): bigint {
  if (n < 0) throw new Error(`pow10 requires n >= 0, got ${n}`)
  while (POW10.length <= n) {
    POW10.push(POW10.length === 0 ? 1n : POW10[POW10.length - 1] * 10n)
  }
  return POW10[n]
}

export const ZERO: Decimal = { unscaled: 0n, scale: 0 }
export const ONE: Decimal = { unscaled: 1n, scale: 0 }

/** Divide by a POSITIVE divisor with HALF_UP rounding, preserving the numerator's sign. */
function divRoundHalfUp(numerator: bigint, denominator: bigint): bigint {
  const negative = numerator < 0n
  const abs = negative ? -numerator : numerator
  const quotient = abs / denominator
  const remainder = abs % denominator
  const rounded = remainder * 2n >= denominator ? quotient + 1n : quotient
  return negative ? -rounded : rounded
}

/** Restate `d` at `targetScale`, rounding HALF_UP when losing digits. */
export function rescale(d: Decimal, targetScale: number): Decimal {
  if (targetScale === d.scale) return d
  if (targetScale > d.scale) {
    return { unscaled: d.unscaled * pow10(targetScale - d.scale), scale: targetScale }
  }
  return { unscaled: divRoundHalfUp(d.unscaled, pow10(d.scale - targetScale)), scale: targetScale }
}

/** Bring both operands to a common (the larger) scale — never loses information. */
function align(a: Decimal, b: Decimal): [bigint, bigint, number] {
  const scale = Math.max(a.scale, b.scale)
  return [rescale(a, scale).unscaled, rescale(b, scale).unscaled, scale]
}

export function parseDecimal(text: string): Decimal | null {
  const trimmed = text.trim()
  if (!/^[+-]?(\d+(\.\d*)?|\.\d+)$/.test(trimmed)) return null
  const negative = trimmed.startsWith('-')
  const unsigned = trimmed.replace(/^[+-]/, '')
  const dot = unsigned.indexOf('.')
  const digits = dot < 0 ? unsigned : unsigned.slice(0, dot) + unsigned.slice(dot + 1)
  const scale = dot < 0 ? 0 : unsigned.length - dot - 1
  const magnitude = digits === '' ? 0n : BigInt(digits)
  return { unscaled: negative ? -magnitude : magnitude, scale }
}

export function toDecimalString(d: Decimal): string {
  if (d.scale === 0) return d.unscaled.toString()
  const negative = d.unscaled < 0n
  const digits = (negative ? -d.unscaled : d.unscaled).toString().padStart(d.scale + 1, '0')
  const cut = digits.length - d.scale
  return `${negative ? '-' : ''}${digits.slice(0, cut)}.${digits.slice(cut)}`
}

export function add(a: Decimal, b: Decimal): Decimal {
  const [ua, ub, scale] = align(a, b)
  return { unscaled: ua + ub, scale }
}

export function subtract(a: Decimal, b: Decimal): Decimal {
  const [ua, ub, scale] = align(a, b)
  return { unscaled: ua - ub, scale }
}

/** Exact: scales add, mirroring BigDecimal.multiply. */
export function multiply(a: Decimal, b: Decimal): Decimal {
  return { unscaled: a.unscaled * b.unscaled, scale: a.scale + b.scale }
}

export function isZero(d: Decimal): boolean {
  return d.unscaled === 0n
}

/** Returns null on division by zero — callers must surface an error, never substitute 0. */
export function divide(a: Decimal, b: Decimal, scale: number = DIVISION_SCALE): Decimal | null {
  if (isZero(b)) return null
  // a/b at `scale` == round(ua * 10^(scale + sb - sa) / ub)
  const shift = scale + b.scale - a.scale
  let numerator = a.unscaled
  let denominator = b.unscaled
  if (shift >= 0) {
    numerator *= pow10(shift)
  } else {
    denominator *= pow10(-shift)
  }
  if (denominator < 0n) {
    numerator = -numerator
    denominator = -denominator
  }
  return { unscaled: divRoundHalfUp(numerator, denominator), scale }
}

export function compare(a: Decimal, b: Decimal): number {
  const [ua, ub] = align(a, b)
  return ua < ub ? -1 : ua > ub ? 1 : 0
}

export function negate(d: Decimal): Decimal {
  return { unscaled: -d.unscaled, scale: d.scale }
}

export function abs(d: Decimal): Decimal {
  return d.unscaled < 0n ? negate(d) : d
}

/** HALF_UP to `digits` decimal places, matching BigDecimal.setScale(digits, HALF_UP). */
export function roundTo(d: Decimal, digits: number): Decimal {
  return rescale(d, Math.max(digits, 0))
}

/** Truncate toward zero (ROUNDDOWN / TRUNC). */
export function truncateTo(d: Decimal, digits: number): Decimal {
  const target = Math.max(digits, 0)
  if (target >= d.scale) return rescale(d, target)
  const divisor = pow10(d.scale - target)
  const negative = d.unscaled < 0n
  const magnitude = (negative ? -d.unscaled : d.unscaled) / divisor
  return { unscaled: negative ? -magnitude : magnitude, scale: target }
}

/** Round away from zero (ROUNDUP) — ceil for positives, floor for negatives. */
export function roundAwayFromZero(d: Decimal, digits: number): Decimal {
  const target = Math.max(digits, 0)
  if (target >= d.scale) return rescale(d, target)
  const divisor = pow10(d.scale - target)
  const negative = d.unscaled < 0n
  const absUnscaled = negative ? -d.unscaled : d.unscaled
  let magnitude = absUnscaled / divisor
  if (absUnscaled % divisor !== 0n) magnitude += 1n
  return { unscaled: negative ? -magnitude : magnitude, scale: target }
}

/** Integer part, toward zero (INT). */
export function integerPart(d: Decimal): Decimal {
  return truncateTo(d, 0)
}

/**
 * Integer exponent only. A fractional exponent would force a transcendental implementation whose
 * last digits differ between JS and Java; refusing it keeps the two interpreters provably equal.
 * Negative exponents divide at DIVISION_SCALE.
 */
export function power(base: Decimal, exponent: Decimal): Decimal | null {
  // Truncation toward zero is a no-op exactly when the exponent is already an integer.
  const truncated = truncateTo(exponent, 0)
  if (compare(truncated, exponent) !== 0) return null
  let n = truncated.unscaled
  const inverse = n < 0n
  if (inverse) n = -n
  let result = ONE
  for (let i = 0n; i < n; i++) {
    result = multiply(result, base)
  }
  if (!inverse) return result
  return divide(ONE, result)
}

/** Floor integer square root via Newton's method — deterministic, mirrors BigInteger.sqrt(). */
export function integerSqrt(n: bigint): bigint {
  if (n < 0n) throw new Error('integerSqrt requires n >= 0')
  if (n < 2n) return n
  let x = n
  let y = (x + 1n) / 2n
  while (y < x) {
    x = y
    y = (x + n / x) / 2n
  }
  return x
}

/**
 * SQRT truncated (not rounded) at DIVISION_SCALE. Truncation is deliberate: both sides compute
 * floor(isqrt(unscaled * 10^(2S - scale))), so the results are bit-identical by construction.
 * Returns null for negatives — no silent NaN.
 */
export function sqrt(d: Decimal): Decimal | null {
  if (d.unscaled < 0n) return null
  if (isZero(d)) return { unscaled: 0n, scale: DIVISION_SCALE }
  const shift = 2 * DIVISION_SCALE - d.scale
  const scaled = shift >= 0
    ? d.unscaled * pow10(shift)
    : d.unscaled / pow10(-shift)
  return { unscaled: integerSqrt(scaled), scale: DIVISION_SCALE }
}

/** Remainder with the sign of the dividend (MOD), matching BigDecimal.remainder. */
export function remainder(a: Decimal, b: Decimal): Decimal | null {
  if (isZero(b)) return null
  const [ua, ub, scale] = align(a, b)
  return { unscaled: ua % ub, scale }
}

export function fromInt(n: number | bigint): Decimal {
  return { unscaled: BigInt(n), scale: 0 }
}
