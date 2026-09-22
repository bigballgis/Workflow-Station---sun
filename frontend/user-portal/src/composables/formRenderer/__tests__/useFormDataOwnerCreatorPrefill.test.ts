import { describe, it, expect, vi } from 'vitest'
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

describe('useFormData Owner Creator default', () => {
  it('seeds empty Creator from defaultValue and keeps Case Handler empty', () => {
    const allFields = computed(() => [
      {
        key: 'case_creator',
        label: 'Creator',
        type: 'owner',
        defaultValue: 'user:u-hmdc',
        _ownerConfig: '{"source":"CREATOR"}',
        _ownerPrefillDisplay: 'hase-hmdc',
      },
      {
        key: 'current_handler',
        label: 'Current Handler',
        type: 'owner',
        _ownerConfig: '{"source":"CASE_HANDLER"}',
      },
    ] as FormField[])

    const api = useFormData({
      formRef: ref(undefined),
      allFields,
      modelValue: () => ({}),
      readonly: () => false,
      config: () => undefined,
      getInternalUpdate: () => false,
      setInternalUpdate: () => {},
      emitChange: () => {},
      emitModelValue: () => {},
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

    expect(api.formData.value.case_creator).toBe('user:u-hmdc')
    expect(api.formData.value.case_creator__display).toBe('hase-hmdc')
    expect(api.formData.value.current_handler).toBeNull()
  })

  it('does not replace a Creator already on the parent model', () => {
    const allFields = computed(() => [
      {
        key: 'case_creator',
        label: 'Creator',
        type: 'owner',
        defaultValue: 'user:u-hmdc',
        _ownerPrefillDisplay: 'hase-hmdc',
      },
    ] as FormField[])

    const api = useFormData({
      formRef: ref(undefined),
      allFields,
      modelValue: () => ({ case_creator: 'user:u-zhangwei', case_creator__display: '张伟' }),
      readonly: () => false,
      config: () => undefined,
      getInternalUpdate: () => false,
      setInternalUpdate: () => {},
      emitChange: () => {},
      emitModelValue: () => {},
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

    expect(api.formData.value.case_creator).toBe('user:u-zhangwei')
    expect(api.formData.value.case_creator__display).toBe('张伟')
  })

  it('keeps a generated MAIN pk that is not a canvas field across initFormData', () => {
    const allFields = computed(() => [
      { key: 'title', label: 'Title', type: 'input' },
    ] as FormField[])
    const parent = ref<Record<string, unknown>>({ title: 'P0 dual write' })
    const api = useFormData({
      formRef: ref(undefined),
      allFields,
      modelValue: () => parent.value,
      readonly: () => false,
      config: () => undefined,
      getInternalUpdate: () => false,
      setInternalUpdate: () => {},
      emitChange: () => {},
      emitModelValue: () => {},
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
      primaryKeyFields: () => ['id'],
    })
    api.initFormData()
    api.handlePrimaryFormDataPatch({ id: 'Case-000021' })
    parent.value = { title: 'P0 dual write', id: 'Case-000021' }
    api.initFormData()
    expect(api.formData.value.id).toBe('Case-000021')
    expect(api.formData.value.title).toBe('P0 dual write')
  })
})
