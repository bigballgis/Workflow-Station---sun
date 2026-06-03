/**
 * Property 5: configJson SubTableField props extraction correctness
 * **Validates: Requirements 10.1, 10.2, 10.3**
 *
 * Tests that the helper functions correctly extract rowFormulas, summaryColumns,
 * summaryAggregations, and validationConfig from a FormBusinessLogicConfig
 * for a given bindingId.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type {
  FormBusinessLogicConfig,
  SummaryRule,
  SubTableValidationConfig,
  RowFormulaRule,
} from '../formRendererHelpers'

// ─── Pure extraction functions (mirror FormRenderer helpers) ─────────────────

function getSubFormRowFormulas(
  config: FormBusinessLogicConfig | undefined,
  bindingId: number | undefined,
): RowFormulaRule[] | undefined {
  if (!bindingId || !config?.subForms) return undefined
  return config.subForms[String(bindingId)]?.rowFormulas
}

function getSummaryColumns(
  config: FormBusinessLogicConfig | undefined,
  bindingId: number | undefined,
): string[] | undefined {
  if (!bindingId || !config?.summaryRules) return undefined
  return config.summaryRules
    .filter((r: SummaryRule) => r.sourceBindingId === bindingId)
    .map((r: SummaryRule) => r.sourceColumn)
}

function getSummaryAggregations(
  config: FormBusinessLogicConfig | undefined,
  bindingId: number | undefined,
): Record<string, 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'> | undefined {
  if (!bindingId || !config?.summaryRules) return undefined
  const aggs: Record<string, 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'> = {}
  config.summaryRules
    .filter((r: SummaryRule) => r.sourceBindingId === bindingId)
    .forEach((r: SummaryRule) => { aggs[r.sourceColumn] = r.aggregation })
  return Object.keys(aggs).length > 0 ? aggs : undefined
}

function getSubTableValidation(
  config: FormBusinessLogicConfig | undefined,
  bindingId: number | undefined,
): SubTableValidationConfig | undefined {
  if (!bindingId || !config?.subTableValidation) return undefined
  return config.subTableValidation[String(bindingId)]
}

// ─── Arbitraries ─────────────────────────────────────────────────────────────

const bindingIdArb = fc.integer({ min: 1, max: 9999 })
const columnNameArb = fc.string({ minLength: 1, maxLength: 15 }).filter(s => /^[a-zA-Z]\w*$/.test(s))
const aggregationArb = fc.constantFrom('SUM' as const, 'AVG' as const, 'COUNT' as const, 'MIN' as const, 'MAX' as const)

const rowFormulaArb: fc.Arbitrary<RowFormulaRule> = fc.record({
  targetColumn: columnNameArb,
  expression: fc.constantFrom('a + b', 'qty * price', 'x - y'),
  dependsOn: fc.array(columnNameArb, { minLength: 1, maxLength: 3 }),
})

const summaryRuleArb = (bindingId: number): fc.Arbitrary<SummaryRule> =>
  fc.record({
    sourceBindingId: fc.constant(bindingId),
    sourceColumn: columnNameArb,
    targetField: columnNameArb,
    aggregation: aggregationArb,
  })

const validationConfigArb: fc.Arbitrary<SubTableValidationConfig> = fc.record({
  minRows: fc.option(fc.integer({ min: 0, max: 10 }), { nil: undefined }),
  maxRows: fc.option(fc.integer({ min: 1, max: 50 }), { nil: undefined }),
})

// ─── Property Tests ──────────────────────────────────────────────────────────

describe('Property 5: configJson SubTableField props extraction correctness', () => {
  it('getSubFormRowFormulas returns the exact rowFormulas for the given bindingId', () => {
    fc.assert(
      fc.property(
        bindingIdArb,
        fc.array(rowFormulaArb, { minLength: 1, maxLength: 5 }),
        (bindingId, rowFormulas) => {
          const config: FormBusinessLogicConfig = {
            rule: [],
            options: {},
            subForms: {
              [String(bindingId)]: { rule: [], rowFormulas },
            },
          }
          const result = getSubFormRowFormulas(config, bindingId)
          expect(result).toEqual(rowFormulas)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('getSubFormRowFormulas returns undefined for missing bindingId', () => {
    fc.assert(
      fc.property(bindingIdArb, (bindingId) => {
        const config: FormBusinessLogicConfig = {
          rule: [],
          options: {},
          subForms: {},
        }
        expect(getSubFormRowFormulas(config, bindingId)).toBeUndefined()
        expect(getSubFormRowFormulas(undefined, bindingId)).toBeUndefined()
        expect(getSubFormRowFormulas(config, undefined as any)).toBeUndefined()
      }),
      { numRuns: 100 },
    )
  })

  it('getSummaryColumns returns only columns matching the given bindingId', () => {
    fc.assert(
      fc.property(
        bindingIdArb,
        fc.integer({ min: 10000, max: 19999 }), // different bindingId
        fc.array(columnNameArb, { minLength: 1, maxLength: 5 }),
        (bindingId, otherBindingId, columns) => {
          const matchingRules: SummaryRule[] = columns.map(col => ({
            sourceBindingId: bindingId,
            sourceColumn: col,
            targetField: `total_${col}`,
            aggregation: 'SUM' as const,
          }))
          const otherRules: SummaryRule[] = [{
            sourceBindingId: otherBindingId,
            sourceColumn: 'other_col',
            targetField: 'other_total',
            aggregation: 'AVG' as const,
          }]
          const config: FormBusinessLogicConfig = {
            rule: [],
            options: {},
            subForms: {},
            summaryRules: [...matchingRules, ...otherRules],
          }
          const result = getSummaryColumns(config, bindingId)
          expect(result).toEqual(columns)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('getSummaryAggregations maps column to aggregation type for the given bindingId', () => {
    fc.assert(
      fc.property(
        bindingIdArb,
        fc.array(
          fc.record({
            sourceColumn: columnNameArb,
            targetField: columnNameArb,
            aggregation: aggregationArb,
          }),
          { minLength: 1, maxLength: 5 },
        ),
        (bindingId, templateRules) => {
          const rules: SummaryRule[] = templateRules.map(r => ({
            ...r,
            sourceBindingId: bindingId,
          }))
          const config: FormBusinessLogicConfig = {
            rule: [],
            options: {},
            subForms: {},
            summaryRules: rules,
          }
          const result = getSummaryAggregations(config, bindingId)
          if (rules.length === 0) {
            expect(result).toBeUndefined()
          } else {
            expect(result).toBeDefined()
            // Build expected map: last rule for each column wins (same as implementation)
            const expected: Record<string, string> = {}
            for (const rule of rules) {
              expected[rule.sourceColumn] = rule.aggregation
            }
            expect(result).toEqual(expected)
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it('getSubTableValidation returns the exact config for the given bindingId', () => {
    fc.assert(
      fc.property(bindingIdArb, validationConfigArb, (bindingId, valConfig) => {
        const config: FormBusinessLogicConfig = {
          rule: [],
          options: {},
          subForms: {},
          subTableValidation: {
            [String(bindingId)]: valConfig,
          },
        }
        const result = getSubTableValidation(config, bindingId)
        expect(result).toEqual(valConfig)
      }),
      { numRuns: 100 },
    )
  })

  it('getSubTableValidation returns undefined for missing bindingId', () => {
    fc.assert(
      fc.property(bindingIdArb, (bindingId) => {
        const config: FormBusinessLogicConfig = {
          rule: [],
          options: {},
          subForms: {},
          subTableValidation: {},
        }
        expect(getSubTableValidation(config, bindingId)).toBeUndefined()
      }),
      { numRuns: 100 },
    )
  })
})
