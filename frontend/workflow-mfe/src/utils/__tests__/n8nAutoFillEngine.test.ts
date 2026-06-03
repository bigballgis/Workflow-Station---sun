import { describe, it, expect } from 'vitest'
import { applyAutoFill } from '../n8nAutoFillEngine'
import type {
  SubTableBinding,
  SubTableMappingEntry,
  FieldMappingEntry,
  OutputMappingEntry,
} from '../n8nAutoFillEngine'

/**
 * Unit tests for AutoFill_Engine with travel expense regression data.
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 9.5
 */

// ============ Shared test fixtures ============

const EXPENSE_ITEMS_BINDING_ID = 101
const INVOICES_BINDING_ID = 102

/** Real travel expense N8N response fixture */
const travelExpenseN8nResponse = {
  InvoiceRecognitionResults: [
    {
      invoiceType: '火车票',
      invoiceDate: '2024-01-15',
      totalAmount: 350.0,
      invoiceNumber: '12345678',
      description: '北京-上海',
    },
    {
      invoiceType: '酒店住宿发票',
      invoiceDate: '2024-01-15',
      totalAmount: 480.0,
      invoiceNumber: '87654321',
      description: '北京某酒店',
    },
    {
      invoiceType: '出租车发票',
      invoiceDate: '2024-01-16',
      totalAmount: 65.0,
      invoiceNumber: '11223344',
      description: '机场-酒店',
    },
    {
      invoiceType: '餐饮发票',
      invoiceDate: '2024-01-16',
      totalAmount: 120.0,
      invoiceNumber: '55667788',
      description: '工作午餐',
    },
    {
      invoiceType: '机票行程单',
      invoiceDate: '2024-01-17',
      totalAmount: 1200.0,
      invoiceNumber: '99887766',
      description: '上海-北京',
    },
  ],
}

/** OutputMapping config matching the new frontendOutputMapping format */
const travelExpenseOutputMapping: OutputMappingEntry[] = [
  {
    targetType: 'sub_table',
    targetBindingId: EXPENSE_ITEMS_BINDING_ID,
    sourceArrayKey: 'InvoiceRecognitionResults',
    fillMode: 'append',
    fieldMappings: [
      {
        sourceField: 'invoiceType',
        targetField: 'expense_type',
        valueMapping: {
          '火车票': '交通',
          '机票行程单': '交通',
          '出租车发票': '交通',
          '酒店住宿发票': '住宿',
          '餐饮发票': '餐饮',
        },
        defaultValue: '其他',
      },
      { sourceField: 'invoiceDate', targetField: 'expense_date' },
      { sourceField: 'totalAmount', targetField: 'amount' },
      { sourceField: 'description', targetField: 'description' },
    ],
  },
  {
    targetType: 'sub_table',
    targetBindingId: INVOICES_BINDING_ID,
    sourceArrayKey: 'InvoiceRecognitionResults',
    fillMode: 'update',
    fieldMappings: [
      {
        targetField: 'description',
        formatTemplate: '{invoiceType} | No.{invoiceNumber} | ¥{totalAmount} | {invoiceDate}',
        separator: ' | ',
      },
    ],
  },
  {
    targetType: 'field',
    source: 'sum:InvoiceRecognitionResults.totalAmount',
    targetField: 'total_amount',
  },
]

/** Empty expense_items sub-table binding */
function makeExpenseItemsBinding(data: any[] = []): SubTableBinding {
  return {
    bindingId: EXPENSE_ITEMS_BINDING_ID,
    tableName: 'expense_items',
    columns: [
      { field: 'expense_type', label: '费用类型' },
      { field: 'expense_date', label: '日期' },
      { field: 'amount', label: '金额' },
      { field: 'description', label: '描述' },
    ],
    data: JSON.parse(JSON.stringify(data)),
  }
}

/** Invoices sub-table binding with pre-existing rows (for update mode) */
function makeInvoicesBinding(count: number): SubTableBinding {
  const data = Array.from({ length: count }, (_, i) => ({
    file: `https://example.com/invoice_${i}.pdf`,
    file_name: `invoice_${i}.pdf`,
    description: '',
  }))
  return {
    bindingId: INVOICES_BINDING_ID,
    tableName: 'invoices',
    columns: [
      { field: 'file', label: '文件' },
      { field: 'file_name', label: '文件名' },
      { field: 'description', label: '描述' },
    ],
    data,
  }
}

// ============ Tests ============

describe('AutoFill_Engine regression: travel expense scenario', () => {
  it('should produce correct expense_items rows with value mappings applied', () => {
    const bindings = [makeExpenseItemsBinding(), makeInvoicesBinding(5)]
    const formData: Record<string, any> = {}

    const result = applyAutoFill(
      travelExpenseN8nResponse,
      travelExpenseOutputMapping,
      bindings,
      formData
    )

    // Verify expense_items sub-table was appended with 5 rows
    const expenseBinding = result.updatedBindings.find(
      b => b.bindingId === EXPENSE_ITEMS_BINDING_ID
    )!
    expect(expenseBinding.data).toHaveLength(5)

    // Verify value mappings for expense_type
    expect(expenseBinding.data[0].expense_type).toBe('交通')   // 火车票 → 交通
    expect(expenseBinding.data[1].expense_type).toBe('住宿')   // 酒店住宿发票 → 住宿
    expect(expenseBinding.data[2].expense_type).toBe('交通')   // 出租车发票 → 交通
    expect(expenseBinding.data[3].expense_type).toBe('餐饮')   // 餐饮发票 → 餐饮
    expect(expenseBinding.data[4].expense_type).toBe('交通')   // 机票行程单 → 交通

    // Verify other fields are mapped directly
    expect(expenseBinding.data[0].expense_date).toBe('2024-01-15')
    expect(expenseBinding.data[0].amount).toBe(350.0)
    expect(expenseBinding.data[0].description).toBe('北京-上海')

    expect(expenseBinding.data[3].expense_date).toBe('2024-01-16')
    expect(expenseBinding.data[3].amount).toBe(120.0)
    expect(expenseBinding.data[3].description).toBe('工作午餐')
  })

  it('should update invoices rows with formatted descriptions', () => {
    const bindings = [makeExpenseItemsBinding(), makeInvoicesBinding(5)]
    const formData: Record<string, any> = {}

    const result = applyAutoFill(
      travelExpenseN8nResponse,
      travelExpenseOutputMapping,
      bindings,
      formData
    )

    const invoiceBinding = result.updatedBindings.find(
      b => b.bindingId === INVOICES_BINDING_ID
    )!

    // Row count should remain 5 (update mode, not append)
    expect(invoiceBinding.data).toHaveLength(5)

    // Verify formatted descriptions
    expect(invoiceBinding.data[0].description).toBe(
      '火车票 | No.12345678 | ¥350 | 2024-01-15'
    )
    expect(invoiceBinding.data[1].description).toBe(
      '酒店住宿发票 | No.87654321 | ¥480 | 2024-01-15'
    )
    expect(invoiceBinding.data[2].description).toBe(
      '出租车发票 | No.11223344 | ¥65 | 2024-01-16'
    )

    // Verify unspecified fields are preserved
    expect(invoiceBinding.data[0].file).toBe('https://example.com/invoice_0.pdf')
    expect(invoiceBinding.data[0].file_name).toBe('invoice_0.pdf')
  })

  it('should set total_amount to the sum of all totalAmount values', () => {
    const bindings = [makeExpenseItemsBinding(), makeInvoicesBinding(5)]
    const formData: Record<string, any> = {}

    const result = applyAutoFill(
      travelExpenseN8nResponse,
      travelExpenseOutputMapping,
      bindings,
      formData
    )

    // 350 + 480 + 65 + 120 + 1200 = 2215
    expect(result.updatedFormData.total_amount).toBe(2215)
  })

  it('should report correct filledCount', () => {
    const bindings = [makeExpenseItemsBinding(), makeInvoicesBinding(5)]
    const formData: Record<string, any> = {}

    const result = applyAutoFill(
      travelExpenseN8nResponse,
      travelExpenseOutputMapping,
      bindings,
      formData
    )

    // 5 appended expense rows + 5 updated invoice rows + 1 field mapping = 11
    expect(result.filledCount).toBe(11)
  })
})

describe('AutoFill_Engine edge cases', () => {
  it('should handle missing sourceArrayKey gracefully — no error, empty result', () => {
    const bindings = [makeExpenseItemsBinding()]
    const formData: Record<string, any> = {}

    const mapping: SubTableMappingEntry = {
      targetType: 'sub_table',
      targetBindingId: EXPENSE_ITEMS_BINDING_ID,
      sourceArrayKey: 'NonExistentKey',
      fillMode: 'append',
      fieldMappings: [{ sourceField: 'a', targetField: 'b' }],
    }

    // Should not throw
    const result = applyAutoFill({}, [mapping], bindings, formData)

    const binding = result.updatedBindings.find(
      b => b.bindingId === EXPENSE_ITEMS_BINDING_ID
    )!
    expect(binding.data).toHaveLength(0)
    expect(result.filledCount).toBe(0)
  })

  it('should handle empty source array — no rows appended, filledCount = 0', () => {
    const bindings = [makeExpenseItemsBinding()]
    const formData: Record<string, any> = {}

    const mapping: SubTableMappingEntry = {
      targetType: 'sub_table',
      targetBindingId: EXPENSE_ITEMS_BINDING_ID,
      sourceArrayKey: 'items',
      fillMode: 'append',
      fieldMappings: [{ sourceField: 'a', targetField: 'b' }],
    }

    const result = applyAutoFill({ items: [] }, [mapping], bindings, formData)

    const binding = result.updatedBindings.find(
      b => b.bindingId === EXPENSE_ITEMS_BINDING_ID
    )!
    expect(binding.data).toHaveLength(0)
    expect(result.filledCount).toBe(0)
  })

  it('should skip gracefully when targetBindingId is unmatched', () => {
    const bindings = [makeExpenseItemsBinding()]
    const formData: Record<string, any> = {}

    const mapping: SubTableMappingEntry = {
      targetType: 'sub_table',
      targetBindingId: 99999, // does not exist
      sourceArrayKey: 'items',
      fillMode: 'append',
      fieldMappings: [{ sourceField: 'a', targetField: 'b' }],
    }

    const result = applyAutoFill(
      { items: [{ a: 'value' }] },
      [mapping],
      bindings,
      formData
    )

    // No changes to any binding
    expect(result.updatedBindings).toEqual(bindings)
    expect(result.filledCount).toBe(0)
  })

  it('should handle formatTemplate with all null placeholders — empty string, field empty', () => {
    const invoicesBinding = makeInvoicesBinding(1)
    const bindings = [invoicesBinding]
    const formData: Record<string, any> = {}

    const mapping: SubTableMappingEntry = {
      targetType: 'sub_table',
      targetBindingId: INVOICES_BINDING_ID,
      sourceArrayKey: 'items',
      fillMode: 'update',
      fieldMappings: [
        {
          targetField: 'description',
          formatTemplate: '{fieldA} | {fieldB} | {fieldC}',
          separator: ' | ',
        },
      ],
    }

    // All placeholder fields are null/missing
    const result = applyAutoFill(
      { items: [{ fieldA: null, fieldB: null, fieldC: null }] },
      [mapping],
      bindings,
      formData
    )

    const resultBinding = result.updatedBindings.find(
      b => b.bindingId === INVOICES_BINDING_ID
    )!

    // formatTemplate with all null → empty string → value is not written (null check)
    // The description should remain unchanged since empty string is falsy but not null
    // Actually applyFormatTemplate returns '' for all-null, and applyFieldMapping returns ''
    // In update mode, the code checks `if (value != null)` — '' is not null, so it gets written
    expect(resultBinding.data[0].description).toBe('')
  })
})
