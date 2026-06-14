/**
 * BusinessLogicEngine — Sub-table calculation & validation (Tasks 4.15, 4.18).
 */

import type {
  SubTableValidationConfig,
  RowFormulaRule,
} from '../formRendererHelpers'
import type { SubTableValidationResult } from './types'
import { evaluateFormula } from './formula'
import { validateField } from './validation'

// ─── calculateSubTableRow (Task 4.15) ───────────────────────────────────────

/**
 * Calculate a single sub-table row using row formulas (mathjs).
 * E.g., amount = quantity * unit_price.
 * Missing/non-numeric values treated as 0.
 *
 * @returns A new row object with calculated columns filled in.
 */
export function calculateSubTableRow(
  row: Record<string, unknown>,
  rowFormulas: RowFormulaRule[],
): Record<string, unknown> {
  const result = { ...row }

  for (const formula of rowFormulas) {
    const fieldValues: Record<string, unknown> = {}
    for (const dep of formula.dependsOn) {
      fieldValues[dep] = row[dep]
    }
    result[formula.targetColumn] = evaluateFormula(formula.expression, fieldValues)
  }

  return result
}

/**
 * Calculate summary aggregation over a sub-table column.
 * Supports: SUM, AVG, COUNT, MIN, MAX.
 */
export function calculateSummary(
  rows: Array<Record<string, unknown>>,
  column: string,
  aggregation: 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX',
): number {
  if (rows.length === 0) {
    return aggregation === 'COUNT' ? 0 : 0
  }

  const values = rows.map((row) => {
    const num = Number(row[column])
    return isNaN(num) ? 0 : num
  })

  switch (aggregation) {
    case 'SUM':
      return values.reduce((a, b) => a + b, 0)
    case 'AVG':
      return values.reduce((a, b) => a + b, 0) / values.length
    case 'COUNT':
      return rows.length
    case 'MIN':
      return Math.min(...values)
    case 'MAX':
      return Math.max(...values)
    default:
      return 0
  }
}

// ─── validateSubTable (Task 4.18) ───────────────────────────────────────────

/**
 * Validate a sub-table: check minRows/maxRows, validate each cell
 * against columnRules. Returns structured SubTableValidationResult.
 */
export function validateSubTable(
  rows: Array<Record<string, unknown>>,
  config: SubTableValidationConfig,
  t?: (key: string, params?: Record<string, unknown>) => string,
): SubTableValidationResult {
  const cellErrors = new Map<number, Map<string, string[]>>()
  let rowCountError: string | undefined
  let valid = true

  // Check row count constraints
  if (config.minRows !== undefined && rows.length < config.minRows) {
    rowCountError = t
      ? t('subTable.minRowsError', { min: config.minRows, actual: rows.length })
      : `Minimum ${config.minRows} row(s) required, got ${rows.length}`
    valid = false
  }
  if (config.maxRows !== undefined && rows.length > config.maxRows) {
    rowCountError = t
      ? t('subTable.maxRowsError', { max: config.maxRows, actual: rows.length })
      : `Maximum ${config.maxRows} row(s) allowed, got ${rows.length}`
    valid = false
  }

  // Validate each cell against column rules
  if (config.columnRules) {
    for (let rowIdx = 0; rowIdx < rows.length; rowIdx++) {
      const row = rows[rowIdx]
      const rowErrors = new Map<string, string[]>()

      for (const [columnName, rules] of Object.entries(config.columnRules)) {
        const cellValue = row[columnName]
        const errors = validateField(cellValue, rules)
        if (errors.length > 0) {
          rowErrors.set(columnName, errors)
          valid = false
        }
      }

      if (rowErrors.size > 0) {
        cellErrors.set(rowIdx, rowErrors)
      }
    }
  }

  return { valid, rowCountError, cellErrors }
}
