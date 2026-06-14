/**
 * BusinessLogicEngine — Result interfaces shared across the engine modules.
 *
 * Pure type declarations; no runtime behavior.
 */

// ─── Result Interfaces ──────────────────────────────────────────────────────

export interface EvaluationResult {
  visibilityChanges: Map<string, boolean>
  calculatedValues: Map<string, number>
  optionChanges: Map<string, Array<{ label: string; value: any }>>
  stateChanges: Map<string, { disabled?: boolean; required?: boolean }>
}

export interface SummaryEvaluationResult {
  summaryValues: Map<string, number>
}

export interface FormValidationResult {
  valid: boolean
  fieldErrors: Map<string, string[]>
  crossFieldErrors: Array<{ targetField: string; message: string }>
  subTableErrors: Map<number, SubTableValidationResult>
}

export interface CrossFieldValidationResult {
  valid: boolean
  errors: Array<{ targetField: string; message: string }>
}

export interface SubTableValidationResult {
  valid: boolean
  rowCountError?: string
  cellErrors: Map<number, Map<string, string[]>>
}

export interface LinkageResult {
  filteredOptions?: Array<{ label: string; value: unknown }>
  autoFillValue?: unknown
  stateChange?: { disabled?: boolean; required?: boolean }
}
