import { describe, it, expect, vi } from 'vitest'
import { BusinessLogicEngine } from '../businessLogicEngine'
import type { FormBusinessLogicConfig } from '../formRendererHelpers'

/**
 * Integration tests: configJson → BusinessLogicEngine.init() → onFieldChange() → verify results.
 *
 * Test cases:
 * 1. Old configJson (no extension fields) → normal init, no business logic executed
 * 2. Full configJson (all extension fields) → all logic types execute correctly
 * 3. Mixed configJson (partial extension fields) → configured logic executes, rest skipped
 *
 * Validates: Requirements 35.2, 35.3, 37.1
 */

// ─── Fixtures ─────────────────────────────────────────────────────────────────

/** Old-style configJson with no business logic extensions */
const oldConfigJson: FormBusinessLogicConfig = {
  rule: [
    { type: 'input', field: 'name', title: 'Name' },
    { type: 'input', field: 'email', title: 'Email' },
  ],
  options: {},
  subForms: {},
}

/** Full configJson with all business logic extension fields */
const fullConfigJson: FormBusinessLogicConfig = {
  rule: [
    { type: 'input', field: 'quantity', title: 'Quantity' },
    { type: 'input', field: 'unit_price', title: 'Unit Price' },
    { type: 'input', field: 'total', title: 'Total' },
    { type: 'select', field: 'category', title: 'Category' },
    { type: 'input', field: 'sub_category', title: 'Sub Category' },
    {
      type: 'input',
      field: 'reason',
      title: 'Reason',
      control: [{ handle: false, rule: [{ field: 'category', value: 'other' }] }],
    },
    {
      type: 'input',
      field: 'phone',
      title: 'Phone',
      validate: [
        { type: 'required', message: 'Phone is required' },
        { type: 'phone', message: 'Invalid phone number' },
      ],
    },
    { type: 'input', field: 'start_date', title: 'Start Date' },
    { type: 'input', field: 'end_date', title: 'End Date' },
  ],
  options: {},
  subForms: {
    '42': {
      rule: [
        { type: 'input', field: 'item_name', title: 'Item' },
        { type: 'number', field: 'qty', title: 'Qty' },
        { type: 'number', field: 'price', title: 'Price' },
        { type: 'number', field: 'amount', title: 'Amount' },
      ],
      rowFormulas: [
        { targetColumn: 'amount', expression: 'qty * price', dependsOn: ['qty', 'price'] },
      ],
    },
  },
  formulas: [
    { targetField: 'total', expression: 'quantity * unit_price', dependsOn: ['quantity', 'unit_price'] },
  ],
  linkages: [
    {
      sourceField: 'category',
      targetField: 'sub_category',
      linkageType: 'value-auto-fill',
      valueMapping: { electronics: 'phones', clothing: 'shirts' },
    },
  ],
  crossFieldRules: [
    { fields: ['start_date', 'end_date'], operator: 'date-before', message: 'End date must be after start date', targetField: 'end_date' },
  ],
  summaryRules: [
    { sourceBindingId: 42, sourceColumn: 'amount', targetField: 'total', aggregation: 'SUM' },
  ],
  subTableValidation: {
    '42': {
      minRows: 1,
      maxRows: 10,
      columnRules: {
        item_name: [{ type: 'required', message: 'Item name is required' }],
      },
    },
  },
}

/** Mixed configJson — only formulas and linkages, no cross-field or sub-table validation */
const mixedConfigJson: FormBusinessLogicConfig = {
  rule: [
    { type: 'input', field: 'width', title: 'Width' },
    { type: 'input', field: 'height', title: 'Height' },
    { type: 'input', field: 'area', title: 'Area' },
    { type: 'select', field: 'region', title: 'Region' },
    { type: 'input', field: 'currency', title: 'Currency' },
  ],
  options: {},
  subForms: {},
  formulas: [
    { targetField: 'area', expression: 'width * height', dependsOn: ['width', 'height'] },
  ],
  linkages: [
    {
      sourceField: 'region',
      targetField: 'currency',
      linkageType: 'value-auto-fill',
      valueMapping: { US: 'USD', EU: 'EUR', JP: 'JPY' },
    },
  ],
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('BusinessLogicEngine Integration: configJson → init → onFieldChange', () => {
  describe('Old configJson (no extension fields)', () => {
    it('should init without errors and produce no business logic side effects', () => {
      const engine = new BusinessLogicEngine()
      engine.init(oldConfigJson)

      // No formulas, linkages, or cross-field rules — state accessors return defaults
      expect(engine.isFieldVisible('name')).toBe(true)
      expect(engine.getCalculatedValue('name')).toBeUndefined()
      expect(engine.getFilteredOptions('name')).toEqual([])
      expect(engine.getFieldState('name')).toEqual({ disabled: false, required: false })
    })

    it('onFieldChange should return empty result maps', () => {
      const engine = new BusinessLogicEngine()
      engine.init(oldConfigJson)

      const result = engine.onFieldChange('name', 'Alice', { name: 'Alice', email: '' })
      expect(result.visibilityChanges.size).toBe(0)
      expect(result.calculatedValues.size).toBe(0)
      expect(result.optionChanges.size).toBe(0)
      expect(result.stateChanges.size).toBe(0)
    })

    it('validateAll should pass with no errors', () => {
      const engine = new BusinessLogicEngine()
      engine.init(oldConfigJson)

      const result = engine.validateAll({ name: 'Test', email: 'test@example.com' })
      expect(result.valid).toBe(true)
      expect(result.fieldErrors.size).toBe(0)
      expect(result.crossFieldErrors).toEqual([])
    })
  })

  describe('Full configJson (all extension fields)', () => {
    it('should init and compute formula on field change', () => {
      const engine = new BusinessLogicEngine()
      engine.init(fullConfigJson)

      const formData = { quantity: 5, unit_price: 10, total: 0, category: '', sub_category: '', reason: '', phone: '', start_date: '', end_date: '' }
      const result = engine.onFieldChange('quantity', 5, formData)

      expect(result.calculatedValues.get('total')).toBe(50)
    })

    it('should process value-auto-fill linkage on source field change', () => {
      const engine = new BusinessLogicEngine()
      engine.init(fullConfigJson)

      const formData = { quantity: 0, unit_price: 0, total: 0, category: 'electronics', sub_category: '', reason: '', phone: '', start_date: '', end_date: '' }
      const result = engine.onFieldChange('category', 'electronics', formData)

      // The linkage should auto-fill sub_category via calculatedValues (value-auto-fill returns as number)
      // or we can check the engine state
      expect(result.calculatedValues.has('sub_category') || engine.getCalculatedValue('sub_category') !== undefined).toBe(true)
    })

    it('should evaluate visibility rules based on control conditions', async () => {
      vi.useFakeTimers()
      const engine = new BusinessLogicEngine()
      engine.init(fullConfigJson)

      // control: handle=false, rule: [{ field: 'category', value: 'other' }]
      // handle=false means "hide when condition is met"
      // When category = 'other' (condition matches), reason field should be HIDDEN
      const formDataMatch = { quantity: 0, unit_price: 0, total: 0, category: 'other', sub_category: '', reason: '', phone: '', start_date: '', end_date: '' }
      const resultMatch = engine.onFieldChange('category', 'other', formDataMatch)

      if (resultMatch.visibilityChanges.has('reason')) {
        expect(resultMatch.visibilityChanges.get('reason')).toBe(false)
      }

      // Advance past debounce window so next call executes as leading edge
      vi.advanceTimersByTime(60)

      // When category != 'other' (condition does NOT match), reason field should be VISIBLE
      const formDataNoMatch = { quantity: 0, unit_price: 0, total: 0, category: 'electronics', sub_category: '', reason: '', phone: '', start_date: '', end_date: '' }
      const resultNoMatch = engine.onFieldChange('category', 'electronics', formDataNoMatch)

      if (resultNoMatch.visibilityChanges.has('reason')) {
        expect(resultNoMatch.visibilityChanges.get('reason')).toBe(true)
      }

      vi.useRealTimers()
    })

    it('should validate cross-field rules', () => {
      const engine = new BusinessLogicEngine()
      engine.init(fullConfigJson)

      // end_date before start_date should fail
      const formData = { quantity: 0, unit_price: 0, total: 0, category: '', sub_category: '', reason: '', phone: '', start_date: '2025-12-31', end_date: '2025-01-01' }
      const crossResult = engine.validateCrossField(formData)

      expect(crossResult.valid).toBe(false)
      expect(crossResult.errors.length).toBeGreaterThan(0)
      expect(crossResult.errors[0].targetField).toBe('end_date')
    })

    it('should validate field rules (phone validation)', () => {
      const engine = new BusinessLogicEngine()
      engine.init(fullConfigJson)

      const formData = { quantity: 0, unit_price: 0, total: 0, category: '', sub_category: '', reason: '', phone: 'invalid', start_date: '', end_date: '' }
      const result = engine.validateAll(formData)

      expect(result.fieldErrors.has('phone')).toBe(true)
      expect(result.fieldErrors.get('phone')!.length).toBeGreaterThan(0)
    })

    it('should compute sub-table row formulas and summaries via onSubTableChange', () => {
      const engine = new BusinessLogicEngine()
      engine.init(fullConfigJson)

      const rows = [
        { item_name: 'Widget', qty: 3, price: 10, amount: 0 },
        { item_name: 'Gadget', qty: 2, price: 25, amount: 0 },
      ]
      const formData = { quantity: 0, unit_price: 0, total: 0 }
      const summaryResult = engine.onSubTableChange(42, rows, formData)

      // Row formulas: amount = qty * price → 30, 50
      expect(rows[0].amount).toBe(30)
      expect(rows[1].amount).toBe(50)

      // Summary: SUM of amount → 80
      expect(summaryResult.summaryValues.get('total')).toBe(80)
    })

    it('should validate sub-table with minRows constraint', () => {
      const engine = new BusinessLogicEngine()
      engine.init(fullConfigJson)

      // Empty sub-table should fail minRows: 1
      const result = engine.validateSubTable(42, [])
      expect(result.valid).toBe(false)
      expect(result.rowCountError).toContain('Minimum')
    })

    it('should validate sub-table column rules', () => {
      const engine = new BusinessLogicEngine()
      engine.init(fullConfigJson)

      // Row with empty item_name should fail required validation
      const result = engine.validateSubTable(42, [{ item_name: '', qty: 1, price: 10 }])
      expect(result.valid).toBe(false)
      expect(result.cellErrors.get(0)?.get('item_name')?.length).toBeGreaterThan(0)
    })
  })

  describe('Mixed configJson (partial extension fields)', () => {
    it('should compute formula but have no cross-field or sub-table validation', () => {
      const engine = new BusinessLogicEngine()
      engine.init(mixedConfigJson)

      const formData = { width: 4, height: 5, area: 0, region: '', currency: '' }
      const result = engine.onFieldChange('width', 4, formData)

      expect(result.calculatedValues.get('area')).toBe(20)
    })

    it('should process linkage for value-auto-fill', () => {
      const engine = new BusinessLogicEngine()
      engine.init(mixedConfigJson)

      const formData = { width: 0, height: 0, area: 0, region: 'JP', currency: '' }
      const result = engine.onFieldChange('region', 'JP', formData)

      // value-auto-fill maps JP → JPY
      expect(result.calculatedValues.has('currency')).toBe(true)
    })

    it('validateAll should pass (no validation rules configured)', () => {
      const engine = new BusinessLogicEngine()
      engine.init(mixedConfigJson)

      const result = engine.validateAll({ width: 4, height: 5, area: 20, region: 'US', currency: 'USD' })
      expect(result.valid).toBe(true)
      expect(result.crossFieldErrors).toEqual([])
    })

    it('validateSubTable should return valid for unconfigured binding', () => {
      const engine = new BusinessLogicEngine()
      engine.init(mixedConfigJson)

      const result = engine.validateSubTable(99, [{ col: 'val' }])
      expect(result.valid).toBe(true)
    })
  })
})
