import { describe, it, expect, vi } from 'vitest'
import { ref, nextTick, reactive } from 'vue'
import { buildComputedFieldDefinition } from '@platform-shared/computedFieldConfig'
import { useSubTableDialogForm } from '../useSubTableDialogForm'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'

function computedDef(
  fieldName: string,
  source: string,
  onError: 'null' | 'fail' = 'null',
): BindingFieldDefinition {
  const built = buildComputedFieldDefinition(source, 'row', onError)
  if (!built.ok) throw new Error(`test formula does not parse: ${source} — ${built.message}`)
  return {
    fieldName,
    isComputed: true,
    computedField: built.value as unknown as Record<string, unknown>,
  } as unknown as BindingFieldDefinition
}

function plainDef(fieldName: string): BindingFieldDefinition {
  return { fieldName } as unknown as BindingFieldDefinition
}

function setup(fieldDefinitions: BindingFieldDefinition[], initialData?: Record<string, unknown>) {
  const formData = ref<Record<string, any>>({})
  const props = reactive({
    visible: true,
    columns: [
      { field: 'qty', label: 'Qty', type: 'number' },
      { field: 'price', label: 'Price', type: 'number' },
      { field: 'amount', label: 'Amount', type: 'number', readonly: true },
    ],
    mode: 'add' as const,
    initialData,
    fieldDefinitions,
  })
  const emit = vi.fn() as never
  const api = useSubTableDialogForm(props as never, emit, (k: string) => k, {
    formData,
    resetUploadNames: () => {},
    backfillUploadNames: () => {},
    resetLookupState: () => {},
    destroyEditors: () => {},
    fetchDepartmentTree: () => {},
  })
  return { api, formData }
}

describe('useSubTableDialogForm — computed columns', () => {
  const amountDefs = [
    computedDef('amount', 'qty * price'),
    plainDef('qty'),
    plainDef('price'),
  ]

  it('previews the formula over the seeded row when the dialog opens', () => {
    const { api, formData } = setup(amountDefs, { qty: 3, price: 4 })
    api.initDialogFormState('open')
    expect(formData.value.amount).toBe('12')
    expect(api.computedFieldErrors.value).toEqual({})
  })

  it('recomputes when a dependency changes', async () => {
    const { api, formData } = setup(amountDefs, { qty: 3, price: 4 })
    api.initDialogFormState('open')

    formData.value.qty = 10
    await nextTick()
    expect(formData.value.amount).toBe('40')
  })

  it('leaves the row untouched when the table has no computed column', async () => {
    const { api, formData } = setup([plainDef('qty'), plainDef('price')], { qty: 3, price: 4 })
    api.initDialogFormState('open')

    formData.value.qty = 10
    await nextTick()
    expect(formData.value.amount).toBeUndefined()
  })

  it('surfaces an inline error and blanks the value for onError=fail formulas', () => {
    const { api, formData } = setup([computedDef('amount', 'qty / 0', 'fail'), plainDef('qty')], {
      qty: 3,
    })
    api.initDialogFormState('open')
    expect(api.computedFieldErrors.value.amount).toBeTruthy()
    expect(formData.value.amount).toBeNull()
  })

  it('nulls the value without an error for onError=null formulas', () => {
    const { api, formData } = setup([computedDef('amount', 'qty / 0', 'null'), plainDef('qty')], {
      qty: 3,
    })
    api.initDialogFormState('open')
    expect(api.computedFieldErrors.value).toEqual({})
    expect(formData.value.amount).toBeNull()
  })

  it('previews a date difference from YYYY-MM-DD picker strings without TYPE_MISMATCH', () => {
    const dateDefs = [
      computedDef('day', 'enddate - startdate', 'fail'),
      plainDef('startdate'),
      plainDef('enddate'),
    ]
    const { api, formData } = setup(dateDefs, {
      startdate: '2026-08-16',
      enddate: '2026-08-18',
    })
    api.initDialogFormState('open')
    expect(api.computedFieldErrors.value).toEqual({})
    expect(formData.value.day).toBe('2')
  })

  it('previews a MAIN-table column via table.column from hostPrimaryFormData', () => {
    const defs = [
      computedDef('requester', 'leave_request.name', 'fail'),
      plainDef('startdate'),
    ]
    const formData = ref<Record<string, any>>({})
    const props = reactive({
      visible: true,
      columns: [
        { field: 'startdate', label: 'Start', type: 'text' },
        { field: 'requester', label: 'Requester', type: 'text', readonly: true },
      ],
      mode: 'add' as const,
      initialData: {},
      fieldDefinitions: defs,
      hostPrimaryFormData: { name: 'Vin' },
    })
    const api = useSubTableDialogForm(props as never, vi.fn() as never, (k: string) => k, {
      formData,
      resetUploadNames: () => {},
      backfillUploadNames: () => {},
      resetLookupState: () => {},
      destroyEditors: () => {},
      fetchDepartmentTree: () => {},
    })
    api.initDialogFormState('open')
    expect(api.computedFieldErrors.value).toEqual({})
    expect(formData.value.requester).toBe('Vin')
  })
})
