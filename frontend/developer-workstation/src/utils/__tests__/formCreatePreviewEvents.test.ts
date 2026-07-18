import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import {
  attachPreviewMountedDefaultSync,
  dispatchPreviewFieldValueChange,
  injectPreviewFieldLoadDefaults,
  materializePreviewComponentEvents,
  mergeComponentEventsFromSavedRules,
  syncDesignerComponentEventsForFcPreview,
} from '../formCreatePreviewEvents'

describe('formCreatePreviewEvents', () => {
  it('binds blur handler so preview can toggle legal_hold on exact match', () => {
    const previewData = ref<Record<string, unknown>>({
      case_number: '',
      legal_hold: false,
    })
    const rules = [
      {
        type: 'input',
        field: 'case_number',
        on: {
          blur: '$FNX:\nif ($inject.value === "abc") { $inject.api.setValue("legal_hold", true) } else { $inject.api.setValue("legal_hold", false) }',
        },
      },
      { type: 'switch', field: 'legal_hold' },
    ]
    materializePreviewComponentEvents(rules, previewData)
    previewData.value.case_number = 'abc'
    const on = rules[0].on as Record<string, () => void>
    expect(typeof on.blur).toBe('function')
    on.blur()
    expect(previewData.value.legal_hold).toBe(true)
    previewData.value.case_number = 'abcd'
    on.blur()
    expect(previewData.value.legal_hold).toBe(false)
  })

  it('injectPreviewFieldLoadDefaults sets select via hook.load api', () => {
    const previewData = ref<Record<string, unknown>>({ select: 1 })
    const rules = [
      {
        type: 'select',
        field: 'select',
        options: [
          { label: 'Option01', value: 1 },
          { label: 'Option02', value: 2 },
        ],
      },
    ]
    injectPreviewFieldLoadDefaults(rules, previewData)
    const hook = rules[0].hook as { load: (inject: { api: { setValue: (f: string, v: unknown) => void }; rule: { field: string } }) => void }
    const setValues: unknown[] = []
    hook.load({
      api: { setValue: (_f: string, v: unknown) => { setValues.push(v) } },
      rule: { field: 'select' },
    })
    expect(setValues).toEqual([1])
  })

  it('attachPreviewMountedDefaultSync applies previewData on mount', () => {
    const previewData = ref<Record<string, unknown>>({ select: 1 })
    const option = attachPreviewMountedDefaultSync({}, previewData)
    const setValues: Record<string, unknown> = {}
    const onMounted = option.onMounted as (api: { setValue: (f: string, v: unknown) => void }) => void
    onMounted({
      setValue: (f: string, v: unknown) => {
        setValues[f] = v
      },
    })
    expect(setValues.select).toBe(1)
  })

  it('dispatchPreviewFieldValueChange runs lookup change handler and sets sibling field', () => {
    const previewData = ref<Record<string, unknown>>({
      test: null,
      testvalue: '',
    })
    const rules = [
      {
        type: 'lookup',
        field: 'test',
        on: {
          change: '$FNX:\nif ($inject.value != null && String($inject.value).trim() !== \'\') { $inject.api.setValue(\'testvalue\', \'successful\') }',
        },
      },
      { type: 'input', field: 'testvalue' },
    ]
    dispatchPreviewFieldValueChange(rules, 'test', { id: 'row-1', name: 'Demo' }, previewData)
    expect(previewData.value.test).toEqual({ id: 'row-1', name: 'Demo' })
    expect(previewData.value.testvalue).toBe('successful')
  })

  it('syncDesignerComponentEventsForFcPreview copies _on.change onto on for fc-designer preview', () => {
    const rules = [
      {
        type: 'lookup',
        field: 'test',
        _on: {
          change: '$FNX:\n$inject.api.setValue("testvalue", "fc-preview")',
        },
      },
    ]
    syncDesignerComponentEventsForFcPreview(rules)
    expect((rules[0].on as Record<string, unknown>).change).toBeDefined()
  })

  it('mergeComponentEventsFromSavedRules overlays persisted handlers when live getRule omits them', () => {
    const live = [{ type: 'lookup', field: 'test' }]
    const saved = [
      {
        type: 'lookup',
        field: 'test',
        on: {
          change: '$FNX:\n$inject.api.setValue("testvalue", "from-saved")',
        },
      },
    ]
    mergeComponentEventsFromSavedRules(live, saved)
    const previewData = ref<Record<string, unknown>>({ test: null, testvalue: '' })
    dispatchPreviewFieldValueChange(live, 'test', { id: 1 }, previewData)
    expect(previewData.value.testvalue).toBe('from-saved')
  })
})
