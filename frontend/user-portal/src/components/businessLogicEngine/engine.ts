/**
 * BusinessLogicEngine — Runtime engine class (Task 4.1).
 */

import type {
  FormBusinessLogicConfig,
  FormulaRule,
  LinkageRule,
  CrossFieldRule,
  SummaryRule,
  SubTableValidationConfig,
  RowFormulaRule,
  ValidationRule,
} from '../formRendererHelpers'
import type {
  EvaluationResult,
  SummaryEvaluationResult,
  FormValidationResult,
  CrossFieldValidationResult,
  SubTableValidationResult,
} from './types'
import { evaluateFormula } from './formula'
import { processLinkage } from './linkage'
import { validateField, validateCrossFields } from './validation'
import { calculateSubTableRow, calculateSummary, validateSubTable } from './subTable'
import { DependencyGraph } from './dependencyGraph'

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
