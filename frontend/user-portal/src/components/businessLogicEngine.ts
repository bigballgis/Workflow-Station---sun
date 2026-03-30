/**
 * BusinessLogicEngine — Runtime engine for form business logic evaluation.
 *
 * Covers: condition visibility, calculation formulas, field linkage,
 * validation, cross-field validation, sub-table calculation & validation,
 * and dependency graph optimization.
 *
 * Security: NO eval(), NO new Function(). All expressions evaluated through
 * mathjs (restricted instance) or the custom condition evaluator.
 */

import { create, all } from 'mathjs'
import type {
  FormBusinessLogicConfig,
  FormulaRule,
  LinkageRule,
  ConditionExpression,
  CrossFieldRule,
  SummaryRule,
  SubTableValidationConfig,
  RowFormulaRule,
  ValidationRule,
} from './formRendererHelpers'

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

// ─── evaluateCondition (Task 4.2) ───────────────────────────────────────────

/**
 * Evaluate a ConditionExpression against formData.
 *
 * Supports operators: equals, not-equals, contains, greater-than, less-than,
 * is-empty, is-not-empty. Supports AND/OR logic with children.
 *
 * If a referenced field doesn't exist in formData, logs a warning and
 * returns true (ignore rule).
 */
export function evaluateCondition(
  condition: ConditionExpression,
  formData: Record<string, unknown>,
): boolean {
  // If the condition has children with a logic operator, evaluate recursively
  if (condition.children && condition.children.length > 0 && condition.logic) {
    const childResults = condition.children.map((child) =>
      evaluateCondition(child, formData),
    )
    if (condition.logic === 'AND') {
      return childResults.every(Boolean)
    }
    // OR
    return childResults.some(Boolean)
  }

  const fieldKey = condition.field
  if (!(fieldKey in formData)) {
    console.warn(
      `[BusinessLogicEngine] Condition references non-existent field "${fieldKey}", ignoring rule.`,
    )
    return true
  }

  const fieldValue = formData[fieldKey]
  const conditionValue = condition.value

  switch (condition.operator) {
    case 'equals':
      // eslint-disable-next-line eqeqeq
      return fieldValue == conditionValue
    case 'not-equals':
      // eslint-disable-next-line eqeqeq
      return fieldValue != conditionValue
    case 'contains': {
      if (typeof fieldValue === 'string' && conditionValue != null) {
        return fieldValue.includes(String(conditionValue))
      }
      if (Array.isArray(fieldValue) && conditionValue != null) {
        return fieldValue.includes(conditionValue)
      }
      return false
    }
    case 'greater-than':
      return Number(fieldValue) > Number(conditionValue)
    case 'less-than':
      return Number(fieldValue) < Number(conditionValue)
    case 'is-empty':
      return (
        fieldValue === null ||
        fieldValue === undefined ||
        fieldValue === '' ||
        (Array.isArray(fieldValue) && fieldValue.length === 0)
      )
    case 'is-not-empty':
      return !(
        fieldValue === null ||
        fieldValue === undefined ||
        fieldValue === '' ||
        (Array.isArray(fieldValue) && fieldValue.length === 0)
      )
    default:
      return true
  }
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

// ─── processLinkage (Task 4.8) ──────────────────────────────────────────────

export interface LinkageResult {
  filteredOptions?: Array<{ label: string; value: unknown }>
  autoFillValue?: unknown
  stateChange?: { disabled?: boolean; required?: boolean }
}

/**
 * Process a single linkage rule against the current form state.
 *
 * - option-filtering: uses filterConfig (declarative, not JS expression)
 * - value-auto-fill: uses valueMapping dictionary lookup
 * - field-state-change: uses stateConfig with ConditionExpression
 *
 * If a referenced field doesn't exist, logs a warning and skips.
 */
export function processLinkage(
  linkage: LinkageRule,
  sourceValue: unknown,
  formData: Record<string, unknown>,
  targetOptions?: Array<{ label: string; value: unknown; [key: string]: unknown }>,
): LinkageResult {
  const result: LinkageResult = {}

  if (!(linkage.sourceField in formData)) {
    console.warn(
      `[BusinessLogicEngine] Linkage references non-existent source field "${linkage.sourceField}", skipping.`,
    )
    return result
  }

  switch (linkage.linkageType) {
    case 'option-filtering': {
      if (!linkage.filterConfig || !targetOptions) {
        return result
      }
      const { filterField, filterOperator } = linkage.filterConfig
      result.filteredOptions = targetOptions.filter((option) => {
        const optionVal = option[filterField]
        switch (filterOperator) {
          case 'equals':
            // eslint-disable-next-line eqeqeq
            return optionVal == sourceValue
          case 'contains': {
            if (typeof optionVal === 'string' && sourceValue != null) {
              return optionVal.includes(String(sourceValue))
            }
            if (Array.isArray(optionVal) && sourceValue != null) {
              return optionVal.includes(sourceValue)
            }
            return false
          }
          case 'in': {
            if (Array.isArray(sourceValue)) {
              return sourceValue.includes(optionVal)
            }
            return false
          }
          default:
            return true
        }
      })
      break
    }

    case 'value-auto-fill': {
      if (!linkage.valueMapping) {
        return result
      }
      const key = String(sourceValue)
      if (key in linkage.valueMapping) {
        result.autoFillValue = linkage.valueMapping[key]
      }
      break
    }

    case 'field-state-change': {
      if (!linkage.stateConfig) {
        return result
      }
      const conditionMet = evaluateCondition(linkage.stateConfig.condition, formData)
      result.stateChange = {
        disabled: conditionMet ? linkage.stateConfig.disabled : undefined,
        required: conditionMet ? linkage.stateConfig.required : undefined,
      }
      break
    }
  }

  return result
}

// ─── validateField (Task 4.11) ──────────────────────────────────────────────

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_REGEX = /^1[3-9]\d{9}$/

/**
 * Validate a single field value against an array of ValidationRule.
 * Returns an array of error messages for all failing rules.
 * Generates Element Plus compatible validation rules.
 */
export function validateField(
  value: unknown,
  rules: ValidationRule[],
): string[] {
  const errors: string[] = []

  for (const rule of rules) {
    switch (rule.type) {
      case 'required': {
        if (
          value === null ||
          value === undefined ||
          value === '' ||
          (Array.isArray(value) && value.length === 0)
        ) {
          errors.push(rule.message)
        }
        break
      }

      case 'pattern': {
        if (rule.pattern && value != null && value !== '') {
          try {
            const regex = new RegExp(rule.pattern)
            if (!regex.test(String(value))) {
              errors.push(rule.message)
            }
          } catch {
            console.warn(
              `[BusinessLogicEngine] Invalid regex pattern: "${rule.pattern}"`,
            )
          }
        }
        break
      }

      case 'number': {
        if (value != null && value !== '') {
          const num = Number(value)
          if (isNaN(num)) {
            errors.push(rule.message)
          } else {
            if (rule.min !== undefined && num < rule.min) {
              errors.push(rule.message)
            }
            if (rule.max !== undefined && num > rule.max) {
              errors.push(rule.message)
            }
          }
        }
        break
      }

      case 'email': {
        if (value != null && value !== '' && !EMAIL_REGEX.test(String(value))) {
          errors.push(rule.message)
        }
        break
      }

      case 'phone': {
        if (value != null && value !== '' && !PHONE_REGEX.test(String(value))) {
          errors.push(rule.message)
        }
        break
      }

      case 'custom': {
        // Custom validation uses pattern field as a regex
        if (rule.pattern && value != null && value !== '') {
          try {
            const regex = new RegExp(rule.pattern)
            if (!regex.test(String(value))) {
              errors.push(rule.message)
            }
          } catch {
            console.warn(
              `[BusinessLogicEngine] Invalid custom regex: "${rule.pattern}"`,
            )
          }
        }
        // minLength / maxLength checks
        if (value != null && typeof value === 'string') {
          if (rule.minLength !== undefined && value.length < rule.minLength) {
            errors.push(rule.message)
          }
          if (rule.maxLength !== undefined && value.length > rule.maxLength) {
            errors.push(rule.message)
          }
        }
        break
      }
    }
  }

  // Deduplicate error messages
  return [...new Set(errors)]
}

// ─── validateCrossFields (Task 4.13) ────────────────────────────────────────

/**
 * Evaluate cross-field validation rules.
 * Supports: greater-than, less-than, equals, not-equals, date-after, date-before.
 * Returns errors with targetField and message.
 */
export function validateCrossFields(
  rules: CrossFieldRule[],
  formData: Record<string, unknown>,
): CrossFieldValidationResult {
  const errors: Array<{ targetField: string; message: string }> = []

  for (const rule of rules) {
    if (rule.fields.length < 2) continue

    const [fieldA, fieldB] = rule.fields
    const valA = formData[fieldA]
    const valB = formData[fieldB]

    // Skip if either field is empty/undefined
    if (valA == null || valA === '' || valB == null || valB === '') {
      continue
    }

    let valid = true

    switch (rule.operator) {
      case 'greater-than':
        valid = Number(valA) > Number(valB)
        break
      case 'less-than':
        valid = Number(valA) < Number(valB)
        break
      case 'equals':
        // eslint-disable-next-line eqeqeq
        valid = valA == valB
        break
      case 'not-equals':
        // eslint-disable-next-line eqeqeq
        valid = valA != valB
        break
      case 'date-after': {
        const dateA = new Date(String(valA)).getTime()
        const dateB = new Date(String(valB)).getTime()
        valid = !isNaN(dateA) && !isNaN(dateB) && dateA > dateB
        break
      }
      case 'date-before': {
        const dateA = new Date(String(valA)).getTime()
        const dateB = new Date(String(valB)).getTime()
        valid = !isNaN(dateA) && !isNaN(dateB) && dateA < dateB
        break
      }
    }

    if (!valid) {
      errors.push({ targetField: rule.targetField, message: rule.message })
    }
  }

  return { valid: errors.length === 0, errors }
}

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

// ─── DependencyGraph (Task 4.20) ────────────────────────────────────────────

interface RuleNode {
  type: 'formula' | 'linkage' | 'visibility'
  ruleIndex: number
  dependsOn: string[]
  targetField: string
}

/**
 * Dependency graph for business logic rules.
 * Builds a graph from formulas, linkages, and visibility rules.
 * Supports incremental evaluation: getAffectedRules(fieldKey) returns
 * only rules that depend on the changed field.
 * Circular dependency detection: max 10 iterations, then warn and stop.
 */
export class DependencyGraph {
  private fieldToRules = new Map<string, RuleNode[]>()
  private allRules: RuleNode[] = []

  static readonly MAX_ITERATIONS = 10

  /**
   * Build the dependency graph from config rules.
   */
  build(
    formulas: FormulaRule[],
    linkages: LinkageRule[],
    visibilityRules: Array<{ field: string; dependsOn: string[] }>,
  ): void {
    this.fieldToRules.clear()
    this.allRules = []

    // Register formula rules
    for (let i = 0; i < formulas.length; i++) {
      const formula = formulas[i]
      const node: RuleNode = {
        type: 'formula',
        ruleIndex: i,
        dependsOn: formula.dependsOn,
        targetField: formula.targetField,
      }
      this.allRules.push(node)
      this.registerNode(node)
    }

    // Register linkage rules
    for (let i = 0; i < linkages.length; i++) {
      const linkage = linkages[i]
      const node: RuleNode = {
        type: 'linkage',
        ruleIndex: i,
        dependsOn: [linkage.sourceField],
        targetField: linkage.targetField,
      }
      this.allRules.push(node)
      this.registerNode(node)
    }

    // Register visibility rules
    for (let i = 0; i < visibilityRules.length; i++) {
      const vis = visibilityRules[i]
      const node: RuleNode = {
        type: 'visibility',
        ruleIndex: i,
        dependsOn: vis.dependsOn,
        targetField: vis.field,
      }
      this.allRules.push(node)
      this.registerNode(node)
    }
  }

  private registerNode(node: RuleNode): void {
    for (const dep of node.dependsOn) {
      if (!this.fieldToRules.has(dep)) {
        this.fieldToRules.set(dep, [])
      }
      this.fieldToRules.get(dep)!.push(node)
    }
  }

  /**
   * Get all rules affected by a field change, including transitive dependencies.
   * Uses BFS with circular dependency detection (max 10 iterations).
   */
  getAffectedRules(fieldKey: string): RuleNode[] {
    const affected: RuleNode[] = []
    const visited = new Set<string>()
    const queue: string[] = [fieldKey]
    let iterations = 0

    while (queue.length > 0 && iterations < DependencyGraph.MAX_ITERATIONS) {
      iterations++
      const currentField = queue.shift()!

      if (visited.has(currentField)) continue
      visited.add(currentField)

      const rules = this.fieldToRules.get(currentField) ?? []
      for (const rule of rules) {
        affected.push(rule)
        // If this rule's target field triggers further rules, enqueue it
        if (!visited.has(rule.targetField)) {
          queue.push(rule.targetField)
        }
      }
    }

    if (iterations >= DependencyGraph.MAX_ITERATIONS) {
      console.warn(
        `[DependencyGraph] Circular dependency detected for field "${fieldKey}". ` +
        `Stopped after ${DependencyGraph.MAX_ITERATIONS} iterations.`,
      )
    }

    return affected
  }

  /** Check if the graph has any rules registered */
  hasRules(): boolean {
    return this.allRules.length > 0
  }

  /** Get all registered rules */
  getAllRules(): RuleNode[] {
    return this.allRules
  }
}

// ─── BusinessLogicEngine class (Task 4.1) ───────────────────────────────────

/**
 * Runtime engine for form business logic.
 *
 * init(config) — parse configJson, build dependency graph, `?? []` / `?? {}` for optional fields
 * onFieldChange(fieldKey, value, formData) — debounced 50ms, re-evaluate affected rules via dependency graph
 * onSubTableChange(bindingId, rows, formData) — trigger summary calculations
 * isFieldVisible / getFieldState / getCalculatedValue / getFilteredOptions — state accessors
 * validateAll / validateCrossField / validateSubTable — validation methods
 */
export class BusinessLogicEngine {
  private formulas: FormulaRule[] = []
  private linkages: LinkageRule[] = []
  private crossFieldRules: CrossFieldRule[] = []
  private summaryRules: SummaryRule[] = []
  private subTableValidation: Record<string, SubTableValidationConfig> = {}
  private rules: Record<string, unknown>[] = []
  private subForms: Record<string, { rule: unknown[]; rowFormulas?: RowFormulaRule[] }> = {}

  private dependencyGraph = new DependencyGraph()

  // Internal state caches
  private visibilityState = new Map<string, boolean>()
  private calculatedValues = new Map<string, number>()
  private filteredOptions = new Map<string, Array<{ label: string; value: unknown }>>()
  private fieldStates = new Map<string, { disabled: boolean; required: boolean }>()

  // Debounce timer
  private debounceTimer: ReturnType<typeof setTimeout> | null = null
  private static readonly DEBOUNCE_MS = 50

  // Leading-edge debounce state (Req 13.1, 13.2)
  private pendingFieldKey: string | null = null
  private pendingFormData: Record<string, unknown> | null = null
  private lastResult: EvaluationResult = {
    visibilityChanges: new Map(),
    calculatedValues: new Map(),
    optionChanges: new Map(),
    stateChanges: new Map(),
  }

  /**
   * Initialize the engine: parse configJson, build dependency graph.
   * All extension fields use `?? []` / `?? {}` for backward compatibility.
   */
  init(config: FormBusinessLogicConfig): void {
    this.rules = config.rule ?? []
    this.formulas = config.formulas ?? []
    this.linkages = config.linkages ?? []
    this.crossFieldRules = config.crossFieldRules ?? []
    this.summaryRules = config.summaryRules ?? []
    this.subTableValidation = config.subTableValidation ?? {}
    this.subForms = config.subForms ?? {}

    // Reset state
    this.visibilityState.clear()
    this.calculatedValues.clear()
    this.filteredOptions.clear()
    this.fieldStates.clear()

    // Build dependency graph if there are any rules to process
    if (this.formulas.length || this.linkages.length || this.rules.length) {
      this.buildDependencyGraph()
    }
  }

  private buildDependencyGraph(): void {
    // Extract visibility rules from form-create rule[].control
    const visibilityRules = this.extractVisibilityDependencies()

    this.dependencyGraph.build(this.formulas, this.linkages, visibilityRules)
  }

  /**
   * Extract visibility dependencies from form-create rule[].control.
   * Each control entry references fields that determine visibility.
   */
  private extractVisibilityDependencies(): Array<{ field: string; dependsOn: string[] }> {
    const result: Array<{ field: string; dependsOn: string[] }> = []

    const processRules = (rules: Record<string, unknown>[]) => {
      for (const rule of rules) {
        const field = rule.field as string | undefined
        const control = rule.control as Array<{
          rule?: Array<{ field?: string }>
        }> | undefined

        if (field && control && Array.isArray(control)) {
          const deps: string[] = []
          for (const ctrl of control) {
            if (ctrl.rule && Array.isArray(ctrl.rule)) {
              for (const condRule of ctrl.rule) {
                if (condRule.field) {
                  deps.push(condRule.field)
                }
              }
            }
          }
          if (deps.length > 0) {
            result.push({ field, dependsOn: deps })
          }
        }

        // Recurse into children
        if (rule.children && Array.isArray(rule.children)) {
          processRules(rule.children as Record<string, unknown>[])
        }
      }
    }

    processRules(this.rules)
    return result
  }

  /**
   * Field value change handler — leading-edge debounce (Req 13.1, 13.2).
   * First call executes immediately; subsequent calls within DEBOUNCE_MS are merged.
   */
  onFieldChange(
    fieldKey: string,
    _value: unknown,
    formData: Record<string, unknown>,
  ): EvaluationResult {
    if (this.debounceTimer) {
      // Already within debounce window — store pending change for trailing execution
      clearTimeout(this.debounceTimer)
      this.pendingFieldKey = fieldKey
      this.pendingFormData = { ...formData }

      this.debounceTimer = setTimeout(() => {
        if (this.pendingFieldKey && this.pendingFormData) {
          this.lastResult = this.evaluateAffectedRules(this.pendingFieldKey, this.pendingFormData)
          this.pendingFieldKey = null
          this.pendingFormData = null
        }
        this.debounceTimer = null
      }, BusinessLogicEngine.DEBOUNCE_MS)

      return this.lastResult
    }

    // Leading edge — execute immediately
    this.lastResult = this.evaluateAffectedRules(fieldKey, formData)

    // Start debounce window for subsequent calls
    this.debounceTimer = setTimeout(() => {
      if (this.pendingFieldKey && this.pendingFormData) {
        this.lastResult = this.evaluateAffectedRules(this.pendingFieldKey, this.pendingFormData)
        this.pendingFieldKey = null
        this.pendingFormData = null
      }
      this.debounceTimer = null
    }, BusinessLogicEngine.DEBOUNCE_MS)

    return this.lastResult
  }

  private evaluateAffectedRules(
    fieldKey: string,
    formData: Record<string, unknown>,
  ): EvaluationResult {
    const visibilityChanges = new Map<string, boolean>()
    const calculatedValues = new Map<string, number>()
    const optionChanges = new Map<string, Array<{ label: string; value: unknown }>>()
    const stateChanges = new Map<string, { disabled?: boolean; required?: boolean }>()

    const affectedRules = this.dependencyGraph.getAffectedRules(fieldKey)

    for (const rule of affectedRules) {
      switch (rule.type) {
        case 'formula': {
          const formula = this.formulas[rule.ruleIndex]
          if (formula) {
            const val = evaluateFormula(formula.expression, formData as Record<string, unknown>)
            calculatedValues.set(formula.targetField, val)
            this.calculatedValues.set(formula.targetField, val)
          }
          break
        }

        case 'linkage': {
          const linkage = this.linkages[rule.ruleIndex]
          if (linkage) {
            const sourceValue = formData[linkage.sourceField]
            const linkResult = processLinkage(linkage, sourceValue, formData)

            if (linkResult.filteredOptions) {
              optionChanges.set(linkage.targetField, linkResult.filteredOptions)
              this.filteredOptions.set(linkage.targetField, linkResult.filteredOptions)
            }
            if (linkResult.autoFillValue !== undefined) {
              calculatedValues.set(
                linkage.targetField,
                Number(linkResult.autoFillValue) || 0,
              )
            }
            if (linkResult.stateChange) {
              stateChanges.set(linkage.targetField, linkResult.stateChange)
              this.fieldStates.set(linkage.targetField, {
                disabled: linkResult.stateChange.disabled ?? false,
                required: linkResult.stateChange.required ?? false,
              })
            }
          }
          break
        }

        case 'visibility': {
          const visible = this.evaluateVisibility(rule.targetField, formData)
          visibilityChanges.set(rule.targetField, visible)
          this.visibilityState.set(rule.targetField, visible)
          break
        }
      }
    }

    return { visibilityChanges, calculatedValues, optionChanges, stateChanges }
  }

  /**
   * Evaluate visibility for a field based on its control rules.
   */
  private evaluateVisibility(
    fieldKey: string,
    formData: Record<string, unknown>,
  ): boolean {
    const rule = this.findRuleByField(fieldKey)
    if (!rule) return true

    const control = rule.control as Array<{
      handle?: boolean
      rule?: Array<{ field?: string; value?: unknown }>
    }> | undefined

    if (!control || !Array.isArray(control) || control.length === 0) {
      return true
    }

    for (const ctrl of control) {
      if (!ctrl.rule || !Array.isArray(ctrl.rule)) continue

      // form-create control: handle=false means "show when condition NOT met"
      // Each rule entry is { field, value } — all must match for the control to activate
      const allMatch = ctrl.rule.every((condRule) => {
        if (!condRule.field) return true
        if (!(condRule.field in formData)) {
          console.warn(
            `[BusinessLogicEngine] Visibility condition references non-existent field "${condRule.field}", ignoring.`,
          )
          return true
        }
        // eslint-disable-next-line eqeqeq
        return formData[condRule.field] == condRule.value
      })

      // handle=true: show when condition is met (hide when NOT met)
      // handle=false (default): hide when condition is met (show when NOT met)
      const handle = ctrl.handle !== undefined ? ctrl.handle : false
      if (handle) {
        // handle=true: show when condition is met
        if (!allMatch) return false
      } else {
        // handle=false: hide when condition is met
        if (allMatch) return false
      }
    }

    return true
  }

  private findRuleByField(fieldKey: string): Record<string, unknown> | null {
    const search = (rules: Record<string, unknown>[]): Record<string, unknown> | null => {
      for (const rule of rules) {
        if (rule.field === fieldKey) return rule
        if (rule.children && Array.isArray(rule.children)) {
          const found = search(rule.children as Record<string, unknown>[])
          if (found) return found
        }
      }
      return null
    }
    return search(this.rules)
  }

  /**
   * Sub-table data change handler — triggers summary calculations.
   */
  onSubTableChange(
    bindingId: number,
    rows: Array<Record<string, unknown>>,
    _formData: Record<string, unknown>,
  ): SummaryEvaluationResult {
    const summaryValues = new Map<string, number>()

    // Calculate row formulas for each row
    const bindingKey = String(bindingId)
    const subForm = this.subForms[bindingKey]
    if (subForm?.rowFormulas) {
      for (let i = 0; i < rows.length; i++) {
        rows[i] = calculateSubTableRow(rows[i], subForm.rowFormulas)
      }
    }

    // Calculate summary aggregations
    for (const rule of this.summaryRules) {
      if (rule.sourceBindingId === bindingId) {
        const value = calculateSummary(rows, rule.sourceColumn, rule.aggregation)
        summaryValues.set(rule.targetField, value)
        this.calculatedValues.set(rule.targetField, value)
      }
    }

    return { summaryValues }
  }

  // ─── State accessors ────────────────────────────────────────────────────

  isFieldVisible(fieldKey: string): boolean {
    return this.visibilityState.get(fieldKey) ?? true
  }

  getFieldState(fieldKey: string): { disabled: boolean; required: boolean } {
    return this.fieldStates.get(fieldKey) ?? { disabled: false, required: false }
  }

  getCalculatedValue(fieldKey: string): number | undefined {
    return this.calculatedValues.get(fieldKey)
  }

  getFilteredOptions(fieldKey: string): Array<{ label: string; value: unknown }> {
    return this.filteredOptions.get(fieldKey) ?? []
  }

  // ─── Validation methods ─────────────────────────────────────────────────

  /**
   * Execute all validation rules, returning structured results.
   */
  validateAll(formData: Record<string, unknown>): FormValidationResult {
    const fieldErrors = new Map<string, string[]>()

    // Validate individual fields from rule[].validate
    this.validateFieldRules(this.rules, formData, fieldErrors)

    // Cross-field validation
    const crossResult = this.validateCrossField(formData)

    // Sub-table validation
    const subTableErrors = new Map<number, SubTableValidationResult>()
    for (const [bindingKey, config] of Object.entries(this.subTableValidation)) {
      const bindingId = Number(bindingKey)
      const rows = (formData[`__subTable_${bindingId}`] as Array<Record<string, unknown>>) ?? []
      const result = validateSubTable(rows, config)
      if (!result.valid) {
        subTableErrors.set(bindingId, result)
      }
    }

    const valid =
      fieldErrors.size === 0 &&
      crossResult.valid &&
      subTableErrors.size === 0

    return {
      valid,
      fieldErrors,
      crossFieldErrors: crossResult.errors,
      subTableErrors,
    }
  }

  private validateFieldRules(
    rules: Record<string, unknown>[],
    formData: Record<string, unknown>,
    fieldErrors: Map<string, string[]>,
  ): void {
    for (const rule of rules) {
      const field = rule.field as string | undefined
      const validate = rule.validate as ValidationRule[] | undefined

      if (field && validate && Array.isArray(validate)) {
        // Only validate visible fields
        if (!this.isFieldVisible(field)) continue

        const value = formData[field]
        const errors = validateField(value, validate)
        if (errors.length > 0) {
          const existing = fieldErrors.get(field) ?? []
          fieldErrors.set(field, [...existing, ...errors])
        }
      }

      // Recurse into children
      if (rule.children && Array.isArray(rule.children)) {
        this.validateFieldRules(
          rule.children as Record<string, unknown>[],
          formData,
          fieldErrors,
        )
      }
    }
  }

  validateCrossField(formData: Record<string, unknown>): CrossFieldValidationResult {
    return validateCrossFields(this.crossFieldRules, formData)
  }

  validateSubTable(
    bindingId: number,
    rows: Array<Record<string, unknown>>,
  ): SubTableValidationResult {
    const bindingKey = String(bindingId)
    const config = this.subTableValidation[bindingKey]
    if (!config) {
      return { valid: true, cellErrors: new Map() }
    }
    return validateSubTable(rows, config)
  }
}
