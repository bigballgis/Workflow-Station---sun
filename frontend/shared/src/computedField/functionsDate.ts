/**
 * Date functions for computed fields.
 *
 * DATEDIFF(start, end) follows Power Fx argument order: end minus start, in whole calendar days.
 * The '-' operator uses the same day count with reversed operands (left minus right).
 */
import { calendarEpochDay } from './date'
import { type ComputedValue, evalErr, evalOk, type EvalResult } from './types'
import { checkArity, type EagerFn } from './functionsMath'

function datediffArg(index: number, value: ComputedValue): number | EvalResult {
  const day = calendarEpochDay(value)
  if (day == null) {
    return evalErr(
      'TYPE_MISMATCH',
      `DATEDIFF argument ${index + 1} is not a calendar date (expected YYYY-MM-DD or YYYY/MM/DD)`,
    )
  }
  return day
}

export const DATE_FUNCTIONS: Record<string, EagerFn> = {
  DATEDIFF: (args) => {
    const arityError = checkArity('DATEDIFF', args, 2, 2)
    if (arityError) return arityError
    const start = datediffArg(0, args[0])
    if (typeof start !== 'number') return start
    const end = datediffArg(1, args[1])
    if (typeof end !== 'number') return end
    return evalOk({ kind: 'number', value: { unscaled: BigInt(end - start), scale: 0 } })
  },
}
