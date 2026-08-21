/**
 * Type coercion and comparison rules for computed fields.
 *
 * These rules are the semantic contract between the TS and Java interpreters. Two deliberate
 * choices, both fixed in goldenVectors.json:
 *
 * 1. TEXT DOES NOT IMPLICITLY BECOME A NUMBER. `amount + "abc"` is a TYPE_MISMATCH error, not 0.
 *    The old evaluateFormula coerced non-numeric input to 0 and returned it as a result, which is
 *    precisely the silent-wrong-number failure `error-handling-governance.mdc` red line 1 forbids.
 *    Authors who really mean "parse this string" write VALUE(text).
 *
 * 2. BLANK BEHAVES AS 0 IN ARITHMETIC and as "" in text context. This is Excel / Power Fx
 *    behaviour and is a semantic definition, not an error swallow: blank stays distinguishable
 *    through ISBLANK, so the author can always branch on emptiness explicitly.
 */
import { compare as compareDecimal, parseDecimal, toDecimalString, ZERO } from './decimal'
import { type ComputedValue, type Decimal, evalErr, type EvalResult } from './types'

export function isBlank(value: ComputedValue): boolean {
  return value.kind === 'blank'
}

/**
 * Normalize a raw row value (whatever came out of process-variable JSON) into a ComputedValue.
 *
 * A string that is ENTIRELY a decimal number becomes a number. This is not a softening of rule 1
 * above — it is about where the value came from. JSON row storage loses declared types: a DECIMAL
 * field edited in an el-input arrives as "1999.99", not 1999.99. Treating that as text would make
 * every numeric field unusable in a formula. A string that is not wholly numeric ("abc", "12abc",
 * "1,999.99") stays text and still fails loudly in arithmetic, which is the property that matters.
 *
 * Empty / whitespace-only string counts as blank: JSON rows routinely carry "" for untouched inputs.
 */
export function fromRowValue(raw: unknown): ComputedValue {
  if (raw === null || raw === undefined) return { kind: 'blank' }
  if (typeof raw === 'boolean') return { kind: 'boolean', value: raw }
  if (typeof raw === 'number') {
    if (!Number.isFinite(raw)) return { kind: 'blank' }
    const parsed = parseDecimal(String(raw))
    return parsed ? { kind: 'number', value: parsed } : { kind: 'blank' }
  }
  if (typeof raw === 'bigint') return { kind: 'number', value: { unscaled: raw, scale: 0 } }
  if (typeof raw === 'string') {
    if (raw.trim() === '') return { kind: 'blank' }
    const parsed = parseDecimal(raw)
    return parsed ? { kind: 'number', value: parsed } : { kind: 'text', value: raw }
  }
  return { kind: 'blank' }
}

/**
 * Numeric view of a value. Text is refused on purpose (see file header). Blank is 0.
 * `context` names the operation so the error message can point at it.
 */
export function toNumber(value: ComputedValue, context: string): Decimal | EvalResult {
  if (value.kind === 'number') return value.value
  if (value.kind === 'blank') return ZERO
  if (value.kind === 'boolean') {
    return evalErr('TYPE_MISMATCH', `${context} expects a number but received a boolean`)
  }
  return evalErr(
    'TYPE_MISMATCH',
    `${context} expects a number but received the text "${truncate(value.value)}". Wrap it in VALUE() to parse it.`,
  )
}

export function toText(value: ComputedValue): string {
  switch (value.kind) {
    case 'text': return value.value
    case 'number': return toDecimalString(value.value)
    case 'boolean': return value.value ? 'true' : 'false'
    default: return ''
  }
}

/** Boolean view. No 0/1 or ""/"x" truthiness — ambiguity there is a bug factory. */
export function toBoolean(value: ComputedValue, context: string): boolean | EvalResult {
  if (value.kind === 'boolean') return value.value
  if (value.kind === 'blank') return false
  if (value.kind === 'number') {
    return evalErr('TYPE_MISMATCH', `${context} expects a condition (true/false) but received a number`)
  }
  return evalErr('TYPE_MISMATCH', `${context} expects a condition (true/false) but received text`)
}

export function isEvalFailure(value: unknown): value is EvalResult & { ok: false } {
  return typeof value === 'object' && value !== null && (value as EvalResult).ok === false
}

/**
 * Equality. Strict about blank: blank equals only blank. Power Fx treats Blank() = 0 as true,
 * which makes "is it empty or is it zero" untestable; requiring ISBLANK is more predictable.
 */
export function valuesEqual(left: ComputedValue, right: ComputedValue): boolean | EvalResult {
  if (isBlank(left) || isBlank(right)) return isBlank(left) && isBlank(right)
  if (left.kind === 'number' && right.kind === 'number') {
    return compareDecimal(left.value, right.value) === 0
  }
  if (left.kind === 'text' && right.kind === 'text') return left.value === right.value
  if (left.kind === 'boolean' && right.kind === 'boolean') return left.value === right.value
  return evalErr('TYPE_MISMATCH', `Cannot compare ${left.kind} with ${right.kind}`)
}

/**
 * Ordering for < <= > >=. Numbers compare numerically, text lexicographically (code-unit order,
 * which Java's String.compareTo matches exactly). Mixed kinds are an error rather than a guess.
 */
export function compareValues(left: ComputedValue, right: ComputedValue): number | EvalResult {
  if (left.kind === 'text' || right.kind === 'text') {
    if (left.kind === 'text' && right.kind === 'text') {
      return left.value < right.value ? -1 : left.value > right.value ? 1 : 0
    }
    if (isBlank(left) || isBlank(right)) {
      const a = toText(left)
      const b = toText(right)
      return a < b ? -1 : a > b ? 1 : 0
    }
    return evalErr('TYPE_MISMATCH', `Cannot order ${left.kind} against ${right.kind}`)
  }
  if (left.kind === 'boolean' || right.kind === 'boolean') {
    return evalErr('TYPE_MISMATCH', 'Booleans support = and <> but not ordering comparisons')
  }
  const a = toNumber(left, 'Comparison')
  if (isEvalFailure(a)) return a
  const b = toNumber(right, 'Comparison')
  if (isEvalFailure(b)) return b
  return compareDecimal(a as Decimal, b as Decimal)
}

function truncate(text: string): string {
  return text.length <= 24 ? text : `${text.slice(0, 24)}…`
}
