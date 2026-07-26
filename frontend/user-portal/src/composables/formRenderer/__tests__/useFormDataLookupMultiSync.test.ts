import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref, computed } from 'vue'
import type { FormField } from '@/components/formRendererHelpers'
import { useFormData } from '../useFormData'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (k: string) => k, locale: { value: 'en' } }),
  createI18n: () => ({
    global: { t: (k: string) => k, locale: 'en' },
    install: () => {},
  }),
}))

vi.mock('@/components/lookup/useLookupBehaviors', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/components/lookup/useLookupBehaviors')>()
  return {
    ...actual,
    resolveDerivedLookup: vi.fn(async () => ({ skip: true })),
  }
})

function multiLookupField(key: string): FormField {
  return {
    key,
    label: key,
    type: 'lookup',
    _lookupTableId: 45,
    _lookupSearchFields: ['status_id'],
    _lookupDisplayFields: ['status_name'],
    _lookupSelectedDisplayField: 'status_name',
    _lookupMultiple: true,
    _lookupFilterConditions: [],
  } as FormField
}

describe('useFormData multi LOOKUP parent sync', () => {
  const emitModelValue = vi.fn()
  let parentModel: Record<string, unknown>

  beforeEach(() => {
    emitModelValue.mockReset()
    parentModel = { stage: null, test_status: null }
  })

  function setup() {
    const allFields = computed(() => [
      { key: 'stage', label: 'stage', type: 'lookup' } as FormField,
      multiLookupField('test_status'),
    ])
    const api = useFormData({
      formRef: ref(undefined),
      allFields,
      modelValue: () => parentModel,
      readonly: () => false,
      config: () => undefined,
      getInternalUpdate: () => false,
      setInternalUpdate: () => {},
      emitChange: () => {},
      emitModelValue: (v) => {
        parentModel = v
        emitModelValue(v)
      },
      emitSubTableData: () => {},
      runComponentEventsOnFieldChange: () => {},
      formOptionsOnChange: () => undefined,
      fieldComponentEventsHas: () => false,
      runFormOptionsOnChange: () => {},
      engineOnFieldChange: () => ({}),
      applyEngineResult: () => {},
      engineOnSubTableChange: () => ({ summaryValues: new Map() }),
      engineCalculatedValues: ref(new Map()),
      requestIdConfig: () => undefined,
    })
    api.initFormData()
    emitModelValue.mockClear()
    return api
  }

  it('handleLookupModelUpdate flushes multi array to parent immediately', () => {
    const api = setup()
    const rows = [
      { status_id: '4', status_name: 'A llll' },
      { status_id: '5', status_name: 'A d dddd' },
    ]
    api.handleLookupModelUpdate('test_status', rows)
    expect(api.formData.value.test_status).toEqual(rows)
    expect(parentModel.test_status).toEqual(rows)
    expect(emitModelValue).toHaveBeenCalled()
  })

  it('handleLookupSelect for multi flushes parent without overwriting the array', async () => {
    const api = setup()
    const rows = [{ status_id: '4', status_name: 'A llll' }]
    api.formData.value.test_status = rows
    await api.handleLookupSelect('test_status', rows[0])
    expect(api.formData.value.test_status).toEqual(rows)
    expect(parentModel.test_status).toEqual(rows)
  })
})
