// ---------------------------------------------------------------------------
// Business Logic Config types — configJson 完整结构与子类型
// 所有新增字段均为可选（optional），确保旧版 configJson 向后兼容
// ---------------------------------------------------------------------------
// ⚠️ 约定一致：本文件与 user-portal/src/components/formRendererHelpers.ts
//   中的同名接口保持完全一致（convention-based sharing，非 npm 包共享）。
//   修改任一侧时，请同步更新另一侧。
// ---------------------------------------------------------------------------

/** configJson 完整结构 */
export interface FormBusinessLogicConfig {
  rule: any[]  // FormCreateRule (form-create rule objects)
  options: Record<string, any>
  subForms: Record<string, SubFormConfig>
  // 业务逻辑扩展（所有字段可选，向后兼容旧版 configJson）
  formulas?: FormulaRule[]
  linkages?: LinkageRule[]
  crossFieldRules?: CrossFieldRule[]
  summaryRules?: SummaryRule[]
  subTableValidation?: Record<string, SubTableValidationConfig>
}

export interface FormulaRule {
  targetField: string
  expression: string       // mathjs 安全表达式
  dependsOn: string[]
}

export interface LinkageRule {
  sourceField: string
  targetField: string
  linkageType: 'option-filtering' | 'value-auto-fill' | 'field-state-change'
  filterConfig?: {
    filterField: string
    filterOperator: 'equals' | 'contains' | 'in'
    filterSource: '$source'
  }
  valueMapping?: Record<string, any>
  stateConfig?: {
    condition: ConditionExpression
    disabled?: boolean
    required?: boolean
  }
}

export interface ConditionExpression {
  field: string
  operator: 'equals' | 'not-equals' | 'contains' | 'greater-than' | 'less-than' | 'is-empty' | 'is-not-empty'
  value?: any
  logic?: 'AND' | 'OR'
  children?: ConditionExpression[]
}

export interface CrossFieldRule {
  fields: string[]
  operator: 'greater-than' | 'less-than' | 'equals' | 'not-equals' | 'date-after' | 'date-before'
  message: string
  targetField: string
}

export interface SummaryRule {
  sourceBindingId: number
  sourceColumn: string
  targetField: string
  aggregation: 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'
}

export interface SubTableValidationConfig {
  minRows?: number
  maxRows?: number
  columnRules?: Record<string, ValidationRule[]>
}

export interface SubFormConfig {
  rule: any[]  // FormCreateRule
  options?: Record<string, any>
  rowFormulas?: RowFormulaRule[]
}

export interface RowFormulaRule {
  targetColumn: string
  expression: string       // mathjs 安全表达式
  dependsOn: string[]
}

export interface ValidationRule {
  type: 'required' | 'pattern' | 'number' | 'email' | 'phone' | 'custom'
  pattern?: string
  min?: number
  max?: number
  minLength?: number
  maxLength?: number
  message: string
}
