/**
 * Math functions for computed fields — the Power Fx numeric set.
 *
 * All of these are eager (arguments already evaluated). The lazy forms (IF / AND / OR / SWITCH /
 * COALESCE) live in evaluator.ts because they must not evaluate every branch.
 *
 * POWER accepts integer exponents only and SQRT truncates at DIVISION_SCALE — both restrictions
 * exist so the JS and Java results are equal by construction rather than by luck. See decimal.ts.
 */
import {
  abs,
  compare,
  integerPart,
  power,
  remainder,
  roundAwayFromZero,
  roundTo,
  sqrt,
  truncateTo,
} from './decimal'
import { isEvalFailure, toNumber } from './coerce'
import { type ComputedValue, type Decimal, evalErr, evalOk, type EvalResult } from './types'

export type EagerFn = (args: ComputedValue[]) => EvalResult

/** Arity check shared by every function; `max` of -1 means variadic. */
export function checkArity(fn: string, args: ComputedValue[], min: number, max: number): EvalResult | null {
  if (args.length < min || (max >= 0 && args.length > max)) {
    const expected = max < 0 ? `at least ${min}` : min === max ? `${min}` : `${min}-${max}`
    return evalErr('WRONG_ARG_COUNT', `${fn} expects ${expected} argument(s) but got ${args.length}`)
  }
  return null
}

/** Read an argument as a number, propagating the coercion error unchanged. */
function num(fn: string, args: ComputedValue[], index: number): Decimal | EvalResult {
  return toNumber(args[index], `${fn} argument ${index + 1}`)
}

/**
 * Whole-number argument (ROUND digit counts, LEFT/MID lengths, FIND start positions).
 * Shared with functionsText.ts — one place decides what "must be a whole number" means.
 */
export function wholeNumberArg(
  fn: string,
  args: ComputedValue[],
  index: number,
  fallback = 0,
): number | EvalResult {
  if (args.length <= index) return fallback
  const raw = num(fn, args, index)
  if (isEvalFailure(raw)) return raw
  const value = raw as Decimal
  const truncated = truncateTo(value, 0)
  if (compare(truncated, value) !== 0) {
    return evalErr('TYPE_MISMATCH', `${fn} argument ${index + 1} must be a whole number`)
  }
  return Number(truncated.unscaled)
}

function unary(fn: string, apply: (value: Decimal) => Decimal): EagerFn {
  return (args) => {
    const arityError = checkArity(fn, args, 1, 1)
    if (arityError) return arityError
    const value = num(fn, args, 0)
    if (isEvalFailure(value)) return value
    return evalOk({ kind: 'number', value: apply(value as Decimal) })
  }
}

function roundingFn(fn: string, apply: (value: Decimal, places: number) => Decimal): EagerFn {
  return (args) => {
    const arityError = checkArity(fn, args, 1, 2)
    if (arityError) return arityError
    const value = num(fn, args, 0)
    if (isEvalFailure(value)) return value
    const places = wholeNumberArg(fn, args, 1)
    if (isEvalFailure(places)) return places
    return evalOk({ kind: 'number', value: apply(value as Decimal, places as number) })
  }
}

export const MATH_FUNCTIONS: Record<string, EagerFn> = {
  ROUND: roundingFn('ROUND', roundTo),
  ROUNDUP: roundingFn('ROUNDUP', roundAwayFromZero),
  ROUNDDOWN: roundingFn('ROUNDDOWN', truncateTo),
  TRUNC: roundingFn('TRUNC', truncateTo),
  ABS: unary('ABS', abs),
  INT: unary('INT', integerPart),

  SQRT: (args) => {
    const arityError = checkArity('SQRT', args, 1, 1)
    if (arityError) return arityError
    const value = num('SQRT', args, 0)
    if (isEvalFailure(value)) return value
    const result = sqrt(value as Decimal)
    if (!result) {
      return evalErr('NEGATIVE_SQRT', 'SQRT is undefined for negative numbers')
    }
    return evalOk({ kind: 'number', value: result })
  },

  POWER: (args) => {
    const arityError = checkArity('POWER', args, 2, 2)
    if (arityError) return arityError
    const base = num('POWER', args, 0)
    if (isEvalFailure(base)) return base
    const exponent = num('POWER', args, 1)
    if (isEvalFailure(exponent)) return exponent
    const result = power(base as Decimal, exponent as Decimal)
    if (!result) {
      return evalErr(
        'NON_INTEGER_EXPONENT',
        'POWER supports whole-number exponents only, so that results are identical on client and server',
      )
    }
    return evalOk({ kind: 'number', value: result })
  },

  MOD: (args) => {
    const arityError = checkArity('MOD', args, 2, 2)
    if (arityError) return arityError
    const dividend = num('MOD', args, 0)
    if (isEvalFailure(dividend)) return dividend
    const divisor = num('MOD', args, 1)
    if (isEvalFailure(divisor)) return divisor
    const result = remainder(dividend as Decimal, divisor as Decimal)
    if (!result) return evalErr('DIVISION_BY_ZERO', 'MOD by zero')
    return evalOk({ kind: 'number', value: result })
  },
}
