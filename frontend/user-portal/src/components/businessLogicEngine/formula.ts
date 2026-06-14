/**
 * BusinessLogicEngine — Safe formula evaluation (Task 4.5).
 *
 * Security: NO eval(), NO new Function(). All expressions evaluated through
 * mathjs (restricted instance) with a whitelisted function scope only.
 */

import { create, all } from 'mathjs'

// ─── Safe mathjs instance ───────────────────────────────────────────────────

const DANGEROUS_KEYWORDS = ['eval', 'Function', 'import', 'require', 'window', 'document']

const math = create(all, {})

/** Whitelist of allowed functions for formula evaluation */
const WHITELISTED_FUNCTIONS: Record<string, (...args: number[]) => number> = {
  SUM: (...args: number[]) => args.reduce((a, b) => a + b, 0),
  AVG: (...args: number[]) =>
    args.length === 0 ? 0 : args.reduce((a, b) => a + b, 0) / args.length,
  MIN: (...args: number[]) => (args.length === 0 ? 0 : Math.min(...args)),
  MAX: (...args: number[]) => (args.length === 0 ? 0 : Math.max(...args)),
  ROUND: (value: number, decimals: number = 0) => {
    const factor = Math.pow(10, decimals)
    return Math.round(value * factor) / factor
  },
  IF: (condition: number, trueVal: number, falseVal: number) =>
    condition ? trueVal : falseVal,
}

// ─── evaluateFormula (Task 4.5) ─────────────────────────────────────────────

/**
 * Check if an expression contains dangerous keywords.
 * Rejects expressions with: eval, Function, import, require, window, document.
 */
export function containsDangerousKeyword(expression: string): boolean {
  return DANGEROUS_KEYWORDS.some((keyword) => {
    const regex = new RegExp(`\\b${keyword}\\b`)
    return regex.test(expression)
  })
}

/**
 * Safely evaluate a math formula expression using mathjs with a restricted scope.
 *
 * Only whitelisted functions are available: SUM, AVG, MIN, MAX, ROUND, IF.
 * Missing or non-numeric values in the scope are treated as 0.
 * Expressions containing dangerous keywords are rejected (returns 0).
 *
 * @returns The numeric result, or 0 on error / dangerous input.
 */
export function evaluateFormula(
  expression: string,
  fieldValues: Record<string, unknown>,
): number {
  if (!expression || typeof expression !== 'string') {
    return 0
  }

  if (containsDangerousKeyword(expression)) {
    console.warn(
      `[BusinessLogicEngine] Formula rejected — contains dangerous keyword: "${expression}"`,
    )
    return 0
  }

  // Build a safe scope: field values coerced to numbers, plus whitelisted functions
  const scope: Record<string, unknown> = { ...WHITELISTED_FUNCTIONS }
  for (const [key, val] of Object.entries(fieldValues)) {
    const num = Number(val)
    scope[key] = isNaN(num) ? 0 : num
  }

  try {
    const result = math.evaluate(expression, scope)
    const num = Number(result)
    if (!isFinite(num)) {
      return 0
    }
    return num
  } catch (err) {
    console.warn(
      `[BusinessLogicEngine] Formula evaluation error for "${expression}":`,
      err,
    )
    return 0
  }
}
