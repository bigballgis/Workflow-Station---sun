import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type {
  FormBusinessLogicConfig,
  FormulaRule,
  LinkageRule,
  ConditionExpression,
  CrossFieldRule,
  SummaryRule,
  SubTableValidationConfig,
  SubFormConfig,
  RowFormulaRule,
  ValidationRule,
} from '../formRendererHelpers'

// Feature: function-unit-design-review, Property 1: configJson 序列化/反序列化 round-trip
// **Validates: Requirements 35.2, 35.3, 35.4**

// ─── Arbitraries ────────────────────────────────────────────────────────────

const fieldNameArb = fc.string({ minLength: 1, maxLength: 30 }).filter((s) => /^[a-zA-Z]/.test(s))

const expressionArb = fc.constantFrom(
  'quantity * unit_price',
  'a + b',
  'SUM(a, b, c)',
  'ROUND(total, 2)',
  'IF(a > 0, a, 0)',
  'price * (1 + tax_rate)',
  'a - b + c',
)

const formulaRuleArb: fc.Arbitrary<FormulaRule> = fc.record({
  targetField: fieldNameArb,
  expression: expressionArb,
  dependsOn: fc.array(fieldNameArb, { minLength: 1, maxLength: 5 }),
})

const conditionOperatorArb = fc.constantFrom(
  'equals' as const,
  'not-equals' as const,
  'contains' as const,
  'greater-than' as const,
  'less-than' as const,
  'is-empty' as const,
  'is-not-empty' as const,
)

const logicArb = fc.constantFrom('AND' as const, 'OR' as const)

// Non-recursive ConditionExpression (leaf node)
const leafConditionArb: fc.Arbitrary<ConditionExpression> = fc.record({
  field: fieldNameArb,
  operator: conditionOperatorArb,
  value: fc.oneof(fc.string({ maxLength: 20 }), fc.integer(), fc.boolean(), fc.constant(null)),
})

// ConditionExpression with optional children (one level deep to keep generation tractable)
const conditionWithChildrenArb: fc.Arbitrary<ConditionExpression> = fc.record({
  field: fieldNameArb,
  operator: conditionOperatorArb,
  value: fc.option(fc.oneof(fc.string({ maxLength: 20 }), fc.integer(), fc.boolean()), { nil: undefined }),
  logic: fc.option(logicArb, { nil: undefined }),
  children: fc.option(fc.array(leafConditionArb, { minLength: 1, maxLength: 3 }), { nil: undefined }),
})

const linkageTypeArb = fc.constantFrom(
  'option-filtering' as const,
  'value-auto-fill' as const,
  'field-state-change' as const,
)

const filterOperatorArb = fc.constantFrom('equals' as const, 'contains' as const, 'in' as const)

const linkageRuleArb: fc.Arbitrary<LinkageRule> = fc.record({
  sourceField: fieldNameArb,
  targetField: fieldNameArb,
  linkageType: linkageTypeArb,
  filterConfig: fc.option(
    fc.record({
      filterField: fieldNameArb,
      filterOperator: filterOperatorArb,
      filterSource: fc.constant('$source' as const),
    }),
    { nil: undefined },
  ),
  valueMapping: fc.option(
    fc.dictionary(fc.string({ minLength: 1, maxLength: 10 }), fc.oneof(fc.string(), fc.integer())),
    { nil: undefined },
  ),
  stateConfig: fc.option(
    fc.record({
      condition: conditionWithChildrenArb,
      disabled: fc.option(fc.boolean(), { nil: undefined }),
      required: fc.option(fc.boolean(), { nil: undefined }),
    }),
    { nil: undefined },
  ),
})

const crossFieldOperatorArb = fc.constantFrom(
  'greater-than' as const,
  'less-than' as const,
  'equals' as const,
  'not-equals' as const,
  'date-after' as const,
  'date-before' as const,
)

const crossFieldRuleArb: fc.Arbitrary<CrossFieldRule> = fc.record({
  fields: fc.array(fieldNameArb, { minLength: 2, maxLength: 4 }),
  operator: crossFieldOperatorArb,
  message: fc.string({ minLength: 1, maxLength: 50 }),
  targetField: fieldNameArb,
})

const aggregationArb = fc.constantFrom('SUM' as const, 'AVG' as const, 'COUNT' as const, 'MIN' as const, 'MAX' as const)

const summaryRuleArb: fc.Arbitrary<SummaryRule> = fc.record({
  sourceBindingId: fc.integer({ min: 1, max: 1000 }),
  sourceColumn: fieldNameArb,
  targetField: fieldNameArb,
  aggregation: aggregationArb,
})

const validationRuleTypeArb = fc.constantFrom(
  'required' as const,
  'pattern' as const,
  'number' as const,
  'email' as const,
  'phone' as const,
  'custom' as const,
)

const validationRuleArb: fc.Arbitrary<ValidationRule> = fc.record({
  type: validationRuleTypeArb,
  pattern: fc.option(fc.string({ minLength: 1, maxLength: 30 }), { nil: undefined }),
  min: fc.option(fc.integer({ min: -1000, max: 1000 }), { nil: undefined }),
  max: fc.option(fc.integer({ min: -1000, max: 1000 }), { nil: undefined }),
  minLength: fc.option(fc.nat({ max: 100 }), { nil: undefined }),
  maxLength: fc.option(fc.nat({ max: 500 }), { nil: undefined }),
  message: fc.string({ minLength: 1, maxLength: 50 }),
})

const subTableValidationConfigArb: fc.Arbitrary<SubTableValidationConfig> = fc.record({
  minRows: fc.option(fc.nat({ max: 10 }), { nil: undefined }),
  maxRows: fc.option(fc.integer({ min: 1, max: 100 }), { nil: undefined }),
  columnRules: fc.option(
    fc.dictionary(fieldNameArb, fc.array(validationRuleArb, { minLength: 1, maxLength: 3 })),
    { nil: undefined },
  ),
})

const rowFormulaRuleArb: fc.Arbitrary<RowFormulaRule> = fc.record({
  targetColumn: fieldNameArb,
  expression: expressionArb,
  dependsOn: fc.array(fieldNameArb, { minLength: 1, maxLength: 4 }),
})

// Simplified form-create rule arbitrary (rule is typed as any[])
const formCreateRuleArb = fc.record({
  type: fc.constantFrom('input', 'select', 'radio', 'switch', 'datePicker', 'inputNumber'),
  field: fieldNameArb,
  title: fc.string({ minLength: 1, maxLength: 20 }),
})

const subFormConfigArb: fc.Arbitrary<SubFormConfig> = fc.record({
  rule: fc.array(formCreateRuleArb, { minLength: 0, maxLength: 5 }),
  options: fc.option(
    fc.dictionary(fc.string({ minLength: 1, maxLength: 10 }), fc.oneof(fc.string(), fc.integer(), fc.boolean())),
    { nil: undefined },
  ),
  rowFormulas: fc.option(fc.array(rowFormulaRuleArb, { minLength: 1, maxLength: 3 }), { nil: undefined }),
})

// ─── Helpers ─────────────────────────────────────────────────────────────────

// Numeric string key for subForms / subTableValidation (e.g. "1", "42", "100")
const numericKeyArb = fc.integer({ min: 1, max: 9999 }).map(String)

// ─── Full FormBusinessLogicConfig arbitrary ──────────────────────────────────

const formBusinessLogicConfigArb: fc.Arbitrary<FormBusinessLogicConfig> = fc.record({
  rule: fc.array(formCreateRuleArb, { minLength: 0, maxLength: 5 }),
  options: fc.dictionary(fc.string({ minLength: 1, maxLength: 10 }), fc.oneof(fc.string(), fc.integer(), fc.boolean())),
  subForms: fc.dictionary(numericKeyArb, subFormConfigArb),
  // All extension fields are optional — randomly include or exclude them
  formulas: fc.option(fc.array(formulaRuleArb, { minLength: 1, maxLength: 5 }), { nil: undefined }),
  linkages: fc.option(fc.array(linkageRuleArb, { minLength: 1, maxLength: 4 }), { nil: undefined }),
  crossFieldRules: fc.option(fc.array(crossFieldRuleArb, { minLength: 1, maxLength: 3 }), { nil: undefined }),
  summaryRules: fc.option(fc.array(summaryRuleArb, { minLength: 1, maxLength: 3 }), { nil: undefined }),
  subTableValidation: fc.option(
    fc.dictionary(numericKeyArb, subTableValidationConfigArb),
    { nil: undefined },
  ),
})

// ─── Property Test ──────────────────────────────────────────────────────────

describe('Property 1: configJson round-trip', () => {
  it('JSON.stringify → JSON.parse produces a deeply equal FormBusinessLogicConfig', () => {
    fc.assert(
      fc.property(formBusinessLogicConfigArb, (config) => {
        const serialized = JSON.stringify(config)
        const deserialized = JSON.parse(serialized) as FormBusinessLogicConfig
        expect(deserialized).toEqual(config)
      }),
      { numRuns: 100 },
    )
  })

  it('round-trip preserves config with all extension fields present', () => {
    // Ensure we test configs that always have all optional fields
    const fullConfigArb = fc.record({
      rule: fc.array(formCreateRuleArb, { minLength: 1, maxLength: 3 }),
      options: fc.dictionary(fc.string({ minLength: 1, maxLength: 10 }), fc.string()),
      subForms: fc.dictionary(numericKeyArb, subFormConfigArb),
      formulas: fc.array(formulaRuleArb, { minLength: 1, maxLength: 3 }),
      linkages: fc.array(linkageRuleArb, { minLength: 1, maxLength: 3 }),
      crossFieldRules: fc.array(crossFieldRuleArb, { minLength: 1, maxLength: 2 }),
      summaryRules: fc.array(summaryRuleArb, { minLength: 1, maxLength: 2 }),
      subTableValidation: fc.dictionary(numericKeyArb, subTableValidationConfigArb),
    })

    fc.assert(
      fc.property(fullConfigArb, (config) => {
        const serialized = JSON.stringify(config)
        const deserialized = JSON.parse(serialized) as FormBusinessLogicConfig
        expect(deserialized).toEqual(config)
      }),
      { numRuns: 100 },
    )
  })

  it('round-trip preserves config with no extension fields (backward compatibility)', () => {
    // Simulate old-style configJson with no business logic extensions
    const legacyConfigArb = fc.record({
      rule: fc.array(formCreateRuleArb, { minLength: 0, maxLength: 5 }),
      options: fc.dictionary(fc.string({ minLength: 1, maxLength: 10 }), fc.oneof(fc.string(), fc.integer())),
      subForms: fc.dictionary(
        numericKeyArb,
        fc.record({
          rule: fc.array(formCreateRuleArb, { minLength: 0, maxLength: 3 }),
        }),
      ),
    })

    fc.assert(
      fc.property(legacyConfigArb, (config) => {
        const serialized = JSON.stringify(config)
        const deserialized = JSON.parse(serialized)
        expect(deserialized).toEqual(config)
      }),
      { numRuns: 100 },
    )
  })
})
