/**
 * AST interpreter for computed fields (TypeScript side).
 *
 * Mirrors the Java interpreter node for node; goldenVectors.json is the shared contract test.
 * There is no eval, no new Function and no expression library — just a switch over node kinds.
 *
 * IF / AND / OR / SWITCH / COALESCE are LAZY. That is not an optimization, it is a correctness
 * requirement: without short-circuiting, the standard divide-guard `IF(qty = 0, 0, total / qty)`
 * would still raise DIVISION_BY_ZERO on the untaken branch and the guard would be unwritable.
 */
import {
  add,
  compare as compareDecimal,
  divide,
  DIVISION_SCALE,
  multiply,
  parseDecimal,
  subtract,
} from './decimal'
import {
  compareValues,
  fromRowValue,
  isEvalFailure,
  toBoolean,
  toNumber,
  valuesEqual,
} from './coerce'
import { checkArity, MATH_FUNCTIONS } from './functionsMath'
import { TEXT_FUNCTIONS } from './functionsText'
import {
  type AggregateNode,
  type AstNode,
  type ComputedValue,
  type Decimal,
  evalErr,
  evalOk,
  type EvalResult,
} from './types'

const EAGER_FUNCTIONS: Record<string, (args: ComputedValue[]) => EvalResult> = {
  ...MATH_FUNCTIONS,
  ...TEXT_FUNCTIONS,
  NOT: (args) => {
    const arityError = checkArity('NOT', args, 1, 1)
    if (arityError) return arityError
    const value = toBoolean(args[0], 'NOT')
    if (isEvalFailure(value)) return value
    return evalOk({ kind: 'boolean', value: !(value as boolean) })
  },
}

/** Names handled lazily by the interpreter rather than through EAGER_FUNCTIONS. */
export const LAZY_FUNCTIONS = new Set(['IF', 'AND', 'OR', 'SWITCH', 'COALESCE'])

export function isKnownFunction(name: string): boolean {
  return LAZY_FUNCTIONS.has(name) || Object.prototype.hasOwnProperty.call(EAGER_FUNCTIONS, name)
}

export function knownFunctionNames(): string[] {
  return [...LAZY_FUNCTIONS, ...Object.keys(EAGER_FUNCTIONS)].sort()
}

/**
 * Sub-table rows grouped by CANONICAL table name.
 *
 * The caller is responsible for de-duplication before constructing this: process variables keep
 * `__subTables__` keyed by bindingId, exact table name AND display-name aliases, all pointing at
 * copies of the same rows. Summing every matching key double- or triple-counts money while
 * looking entirely plausible. See normalizeSubTables below.
 */
export interface EvaluationContext {
  /** Same-row field values, already raw (JSON) — normalized through fromRowValue on read. */
  row: Record<string, unknown>
  /** Canonical table name (lower-cased) -> rows. */
  subTables?: Record<string, Array<Record<string, unknown>>>
}

/** What a `__subTables__` slice key actually refers to, as resolved by the caller. */
export interface SliceIdentity {
  tableId?: string | number | null
  /** Real table name — becomes the canonical key, because that is what the AST references. */
  tableName?: string | null
}

/**
 * Collapse a raw `__subTables__` map into exactly one slice per table, keyed by table NAME.
 *
 * `identify` resolves a slice key to its table (the backend derives it from dw_table_definitions).
 * Slices resolving to an already-seen tableId are aliases and get dropped — NOT merged, since
 * merging is precisely the double-counting bug. Keying the result by table name rather than by
 * whichever alias appeared first matters: an AggregateNode references `request_items`, so a slice
 * that arrived under the bindingId key "42" must still be found under "request_items".
 *
 * With no `identify` (design-time preview, where only names are known) each distinct lower-cased
 * key survives on its own.
 */
export function normalizeSubTables(
  raw: Record<string, unknown> | undefined | null,
  identify?: (sliceKey: string) => SliceIdentity | null | undefined,
): Record<string, Array<Record<string, unknown>>> {
  const result: Record<string, Array<Record<string, unknown>>> = {}
  if (!raw || typeof raw !== 'object') return result
  const seen = new Set<string>()

  for (const [sliceKey, value] of Object.entries(raw)) {
    if (!Array.isArray(value)) continue
    const rows = value.filter((row): row is Record<string, unknown> =>
      row !== null && typeof row === 'object' && !Array.isArray(row))
    const identity = identify?.(sliceKey)
    const tableName = identity?.tableName?.trim()
    const canonical = (tableName && tableName !== '' ? tableName : sliceKey).toLowerCase()
    const hasId = identity?.tableId !== null && identity?.tableId !== undefined
      && String(identity.tableId) !== ''
    const dedupKey = hasId ? `id:${String(identity!.tableId)}` : `name:${canonical}`

    if (seen.has(dedupKey)) continue
    seen.add(dedupKey)
    result[canonical] = rows
  }
  return result
}

function lookupRows(
  context: EvaluationContext,
  table: string,
): Array<Record<string, unknown>> | undefined {
  return context.subTables?.[table.toLowerCase()]
}

function evaluateAggregate(node: AggregateNode, context: EvaluationContext): EvalResult {
  const rows = lookupRows(context, node.table)
  if (!rows) {
    return evalErr('UNKNOWN_TABLE', `Sub-table '${node.table}' is not present on this record`)
  }
  if (node.fn === 'COUNT' && !node.column) {
    return evalOk({ kind: 'number', value: { unscaled: BigInt(rows.length), scale: 0 } })
  }
  const column = node.column as string
  const numbers: Decimal[] = []
  let nonBlankCount = 0
  for (const row of rows) {
    const value = fromRowValue(row[column])
    if (value.kind === 'blank') continue
    nonBlankCount++
    if (node.fn === 'COUNT') continue
    const asNumber = toNumber(value, `${node.fn}(${node.table}.${column})`)
    if (isEvalFailure(asNumber)) return asNumber
    numbers.push(asNumber as Decimal)
  }
  if (node.fn === 'COUNT') {
    return evalOk({ kind: 'number', value: { unscaled: BigInt(nonBlankCount), scale: 0 } })
  }
  // Empty aggregate is 0 for SUM and blank for MIN/MAX/AVG: there is no meaningful
  // minimum of nothing, and returning 0 there would be an invented number.
  if (numbers.length === 0) {
    return node.fn === 'SUM'
      ? evalOk({ kind: 'number', value: { unscaled: 0n, scale: 0 } })
      : evalOk({ kind: 'blank' })
  }
  switch (node.fn) {
    case 'SUM':
      return evalOk({ kind: 'number', value: numbers.reduce(add) })
    case 'MIN':
      return evalOk({ kind: 'number', value: numbers.reduce((a, b) => (compareDecimal(a, b) <= 0 ? a : b)) })
    case 'MAX':
      return evalOk({ kind: 'number', value: numbers.reduce((a, b) => (compareDecimal(a, b) >= 0 ? a : b)) })
    case 'AVG': {
      const total = numbers.reduce(add)
      const mean = divide(total, { unscaled: BigInt(numbers.length), scale: 0 }, DIVISION_SCALE)
      if (!mean) return evalErr('DIVISION_BY_ZERO', 'AVG over an empty set')
      return evalOk({ kind: 'number', value: mean })
    }
    default:
      return evalErr('UNSUPPORTED_NODE', `Unsupported aggregate '${node.fn}'`)
  }
}

function evaluateBinary(node: AstNode & { type: 'binary' }, context: EvaluationContext): EvalResult {
  const left = evaluateAst(node.left, context)
  if (!left.ok) return left
  const right = evaluateAst(node.right, context)
  if (!right.ok) return right

  if (node.op === '=' || node.op === '<>') {
    const equal = valuesEqual(left.value, right.value)
    if (isEvalFailure(equal)) return equal
    return evalOk({ kind: 'boolean', value: node.op === '=' ? (equal as boolean) : !(equal as boolean) })
  }
  if (node.op === '<' || node.op === '<=' || node.op === '>' || node.op === '>=') {
    const order = compareValues(left.value, right.value)
    if (isEvalFailure(order)) return order
    const c = order as number
    const value = node.op === '<' ? c < 0 : node.op === '<=' ? c <= 0 : node.op === '>' ? c > 0 : c >= 0
    return evalOk({ kind: 'boolean', value })
  }

  const a = toNumber(left.value, `Operator '${node.op}'`)
  if (isEvalFailure(a)) return a
  const b = toNumber(right.value, `Operator '${node.op}'`)
  if (isEvalFailure(b)) return b
  const x = a as Decimal
  const y = b as Decimal
  switch (node.op) {
    case '+': return evalOk({ kind: 'number', value: add(x, y) })
    case '-': return evalOk({ kind: 'number', value: subtract(x, y) })
    case '*': return evalOk({ kind: 'number', value: multiply(x, y) })
    case '/': {
      const quotient = divide(x, y, DIVISION_SCALE)
      if (!quotient) {
        return evalErr('DIVISION_BY_ZERO', 'Division by zero. Guard it with IF(divisor = 0, …, …).')
      }
      return evalOk({ kind: 'number', value: quotient })
    }
    default:
      return evalErr('UNSUPPORTED_NODE', `Unsupported operator '${node.op}'`)
  }
}

function evaluateLazyCall(fn: string, args: AstNode[], context: EvaluationContext): EvalResult {
  switch (fn) {
    case 'IF': {
      if (args.length < 2 || args.length > 3) {
        return evalErr('WRONG_ARG_COUNT', `IF expects 2-3 arguments but got ${args.length}`)
      }
      const condition = evaluateAst(args[0], context)
      if (!condition.ok) return condition
      const flag = toBoolean(condition.value, 'IF condition')
      if (isEvalFailure(flag)) return flag
      if (flag as boolean) return evaluateAst(args[1], context)
      return args.length === 3 ? evaluateAst(args[2], context) : evalOk({ kind: 'blank' })
    }
    case 'AND':
    case 'OR': {
      if (args.length < 1) {
        return evalErr('WRONG_ARG_COUNT', `${fn} expects at least 1 argument`)
      }
      const shortCircuit = fn === 'AND' ? false : true
      for (const arg of args) {
        const evaluated = evaluateAst(arg, context)
        if (!evaluated.ok) return evaluated
        const flag = toBoolean(evaluated.value, fn)
        if (isEvalFailure(flag)) return flag
        if ((flag as boolean) === shortCircuit) {
          return evalOk({ kind: 'boolean', value: shortCircuit })
        }
      }
      return evalOk({ kind: 'boolean', value: !shortCircuit })
    }
    case 'SWITCH': {
      // SWITCH(expr, match1, result1, [match2, result2, ...], [default])
      if (args.length < 3) {
        return evalErr('WRONG_ARG_COUNT', 'SWITCH expects at least 3 arguments')
      }
      const subject = evaluateAst(args[0], context)
      if (!subject.ok) return subject
      let i = 1
      while (i + 1 < args.length) {
        const candidate = evaluateAst(args[i], context)
        if (!candidate.ok) return candidate
        const equal = valuesEqual(subject.value, candidate.value)
        if (isEvalFailure(equal)) return equal
        if (equal as boolean) return evaluateAst(args[i + 1], context)
        i += 2
      }
      // A trailing odd argument is the default branch.
      return i < args.length ? evaluateAst(args[i], context) : evalOk({ kind: 'blank' })
    }
    case 'COALESCE': {
      if (args.length < 1) {
        return evalErr('WRONG_ARG_COUNT', 'COALESCE expects at least 1 argument')
      }
      for (const arg of args) {
        const evaluated = evaluateAst(arg, context)
        if (!evaluated.ok) return evaluated
        if (evaluated.value.kind !== 'blank') return evaluated
      }
      return evalOk({ kind: 'blank' })
    }
    default:
      return evalErr('UNKNOWN_FUNCTION', `Unknown function '${fn}'`)
  }
}

export function evaluateAst(node: AstNode, context: EvaluationContext): EvalResult {
  switch (node.type) {
    case 'number': {
      const parsed = parseDecimal(node.text)
      if (!parsed) {
        return evalErr('SYNTAX_ERROR', `Invalid numeric literal '${node.text}'`)
      }
      return evalOk({ kind: 'number', value: parsed })
    }
    case 'text':
      return evalOk({ kind: 'text', value: node.value })
    case 'boolean':
      return evalOk({ kind: 'boolean', value: node.value })
    case 'field':
      return evalOk(fromRowValue(context.row[node.name]))
    case 'aggregate':
      return evaluateAggregate(node, context)
    case 'unary': {
      const operand = evaluateAst(node.operand, context)
      if (!operand.ok) return operand
      const value = toNumber(operand.value, "Unary '-'")
      if (isEvalFailure(value)) return value
      const decimal = value as Decimal
      return evalOk({ kind: 'number', value: { unscaled: -decimal.unscaled, scale: decimal.scale } })
    }
    case 'binary':
      return evaluateBinary(node, context)
    case 'call': {
      if (LAZY_FUNCTIONS.has(node.fn)) {
        return evaluateLazyCall(node.fn, node.args, context)
      }
      const implementation = EAGER_FUNCTIONS[node.fn]
      if (!implementation) {
        return evalErr('UNKNOWN_FUNCTION', `Unknown function '${node.fn}'`)
      }
      const args: ComputedValue[] = []
      for (const arg of node.args) {
        const evaluated = evaluateAst(arg, context)
        if (!evaluated.ok) return evaluated
        args.push(evaluated.value)
      }
      return implementation(args)
    }
    default:
      return evalErr('UNSUPPORTED_NODE', `Unsupported node type '${(node as { type: string }).type}'`)
  }
}
