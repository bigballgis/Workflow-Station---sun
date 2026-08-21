/**
 * Text functions for computed fields — the Power Fx string set.
 *
 * Index conventions follow Power Fx / Excel, NOT JavaScript: positions are 1-based, and FIND
 * returns blank (not 0 and not an error) when the needle is absent so authors can test it with
 * ISBLANK. Java's substring/indexOf are 0-based, so the mirror implementation must offset — the
 * golden vectors pin every boundary case (empty needle, start beyond length, negative count).
 */
import { parseDecimal } from './decimal'
import { isBlank, isEvalFailure, toText } from './coerce'
import { type ComputedValue, evalErr, evalOk, type EvalResult } from './types'
import { checkArity, type EagerFn, wholeNumberArg } from './functionsMath'

function text(args: ComputedValue[], index: number): string {
  return toText(args[index])
}

/** Length / position argument — delegates to the shared whole-number rule in functionsMath. */
function count(fn: string, args: ComputedValue[], index: number): number | EvalResult {
  return wholeNumberArg(fn, args, index)
}

export const TEXT_FUNCTIONS: Record<string, EagerFn> = {
  CONCAT: (args) => {
    const arityError = checkArity('CONCAT', args, 1, -1)
    if (arityError) return arityError
    return evalOk({ kind: 'text', value: args.map(toText).join('') })
  },

  LEN: (args) => {
    const arityError = checkArity('LEN', args, 1, 1)
    if (arityError) return arityError
    return evalOk({ kind: 'number', value: { unscaled: BigInt(text(args, 0).length), scale: 0 } })
  },

  TRIM: (args) => {
    const arityError = checkArity('TRIM', args, 1, 1)
    if (arityError) return arityError
    return evalOk({ kind: 'text', value: text(args, 0).trim() })
  },

  UPPER: (args) => {
    const arityError = checkArity('UPPER', args, 1, 1)
    if (arityError) return arityError
    return evalOk({ kind: 'text', value: text(args, 0).toUpperCase() })
  },

  LOWER: (args) => {
    const arityError = checkArity('LOWER', args, 1, 1)
    if (arityError) return arityError
    return evalOk({ kind: 'text', value: text(args, 0).toLowerCase() })
  },

  LEFT: (args) => {
    const arityError = checkArity('LEFT', args, 2, 2)
    if (arityError) return arityError
    const n = count('LEFT', args, 1)
    if (isEvalFailure(n)) return n
    const size = Math.max(n as number, 0)
    return evalOk({ kind: 'text', value: text(args, 0).slice(0, size) })
  },

  RIGHT: (args) => {
    const arityError = checkArity('RIGHT', args, 2, 2)
    if (arityError) return arityError
    const n = count('RIGHT', args, 1)
    if (isEvalFailure(n)) return n
    const size = Math.max(n as number, 0)
    const source = text(args, 0)
    return evalOk({ kind: 'text', value: size === 0 ? '' : source.slice(Math.max(source.length - size, 0)) })
  },

  /** MID(text, start, count?) — start is 1-based; omitting count takes the rest. */
  MID: (args) => {
    const arityError = checkArity('MID', args, 2, 3)
    if (arityError) return arityError
    const start = count('MID', args, 1)
    if (isEvalFailure(start)) return start
    const source = text(args, 0)
    const from = Math.max((start as number) - 1, 0)
    if (args.length === 2) {
      return evalOk({ kind: 'text', value: source.slice(from) })
    }
    const length = count('MID', args, 2)
    if (isEvalFailure(length)) return length
    const size = Math.max(length as number, 0)
    return evalOk({ kind: 'text', value: source.slice(from, from + size) })
  },

  SUBSTITUTE: (args) => {
    const arityError = checkArity('SUBSTITUTE', args, 3, 3)
    if (arityError) return arityError
    const source = text(args, 0)
    const needle = text(args, 1)
    const replacement = text(args, 2)
    // Replacing "" would loop forever / insert between every char; Power Fx returns the input.
    if (needle === '') return evalOk({ kind: 'text', value: source })
    return evalOk({ kind: 'text', value: source.split(needle).join(replacement) })
  },

  /** FIND(needle, haystack, start?) — 1-based result, blank when not found. */
  FIND: (args) => {
    const arityError = checkArity('FIND', args, 2, 3)
    if (arityError) return arityError
    const needle = text(args, 0)
    const haystack = text(args, 1)
    let from = 0
    if (args.length === 3) {
      const start = count('FIND', args, 2)
      if (isEvalFailure(start)) return start
      from = Math.max((start as number) - 1, 0)
    }
    if (from > haystack.length) return evalOk({ kind: 'blank' })
    const found = haystack.indexOf(needle, from)
    if (found < 0) return evalOk({ kind: 'blank' })
    return evalOk({ kind: 'number', value: { unscaled: BigInt(found + 1), scale: 0 } })
  },

  STARTSWITH: (args) => {
    const arityError = checkArity('STARTSWITH', args, 2, 2)
    if (arityError) return arityError
    return evalOk({ kind: 'boolean', value: text(args, 0).startsWith(text(args, 1)) })
  },

  ENDSWITH: (args) => {
    const arityError = checkArity('ENDSWITH', args, 2, 2)
    if (arityError) return arityError
    return evalOk({ kind: 'boolean', value: text(args, 0).endsWith(text(args, 1)) })
  },

  /** Explicit text-to-number parse — the only sanctioned way to do it (see coerce.ts). */
  VALUE: (args) => {
    const arityError = checkArity('VALUE', args, 1, 1)
    if (arityError) return arityError
    const input = args[0]
    if (isBlank(input)) return evalOk({ kind: 'blank' })
    if (input.kind === 'number') return evalOk(input)
    if (input.kind === 'boolean') {
      return evalErr('TYPE_MISMATCH', 'VALUE cannot convert a boolean to a number')
    }
    const parsed = parseDecimal(input.value)
    if (!parsed) {
      return evalErr('TYPE_MISMATCH', `VALUE cannot parse "${input.value}" as a number`)
    }
    return evalOk({ kind: 'number', value: parsed })
  },

  ISBLANK: (args) => {
    const arityError = checkArity('ISBLANK', args, 1, 1)
    if (arityError) return arityError
    return evalOk({ kind: 'boolean', value: isBlank(args[0]) })
  },
}
