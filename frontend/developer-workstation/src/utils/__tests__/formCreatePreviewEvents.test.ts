import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import {
  attachPreviewMountedDefaultSync,
  dispatchPreviewFieldValueChange,
  injectPreviewFieldLoadDefaults,
  materializePreviewComponentEvents,
  mergeComponentEventsFromSavedRules,
  sanitizePreviewRuleHandlers,
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

  it('mergeComponentEventsFromSavedRules keeps raw string handlers off form-create on/hook (freeze guard)', () => {
    // Regression: serialized $FNX: strings on rule.on / rule.hook make form-create call a
    // string as a function ("w is not a function") on every render tick and freeze Form Preview.
    // Saved handlers must land only in the designer shadow buckets _on / _hook.
    const live: Array<Record<string, unknown>> = [{ type: 'lookup', field: 'test' }]
    const saved = [
      {
        type: 'lookup',
        field: 'test',
        on: { change: '$FNX:\n$inject.api.setValue("x", 1)' },
        hook: { load: '$FNX:\n$inject.api.setValue("y", 2)' },
      },
    ]
    mergeComponentEventsFromSavedRules(live, saved)
    // form-create-facing buckets must NOT receive raw strings
    expect(live[0].on).toBeUndefined()
    expect(live[0].hook).toBeUndefined()
    // shadow buckets carry the serialized handlers for the preview pipeline to compile later
    expect((live[0]._on as Record<string, unknown>).change).toBe('$FNX:\n$inject.api.setValue("x", 1)')
    expect((live[0]._hook as Record<string, unknown>).load).toBe('$FNX:\n$inject.api.setValue("y", 2)')
  })

  it('sanitizePreviewRuleHandlers removes non-function on/hook entries but keeps functions and _on/_hook', () => {
    // The base form-create preview instance can't run "$FNX:" strings; leaving them on
    // on/hook throws "w is not a function" every render tick and freezes the preview.
    const kept = () => {}
    const rules: Array<Record<string, unknown>> = [
      {
        type: 'input',
        field: 'a',
        on: { change: '$FNX:\nfoo()', focus: kept },
        hook: { mounted: '$FNX:\nbar()', load: ['$FNX:\nbaz()'] },
        _on: { change: '$FNX:\nfoo()' },
        _hook: { mounted: '$FNX:\nbar()' },
        children: [
          { type: 'input', field: 'b', hook: { value: '$FNX:\nqux()' } },
        ],
      },
    ]
    sanitizePreviewRuleHandlers(rules)
    const root = rules[0]
    // string handler removed, function handler kept
    expect((root.on as Record<string, unknown>).change).toBeUndefined()
    expect((root.on as Record<string, unknown>).focus).toBe(kept)
    // hook bucket had only non-functions → removed entirely
    expect(root.hook).toBeUndefined()
    // shadow buckets untouched (preview runtime compiles them later)
    expect((root._on as Record<string, unknown>).change).toBe('$FNX:\nfoo()')
    expect((root._hook as Record<string, unknown>).mounted).toBe('$FNX:\nbar()')
    // nested child sanitized too
    expect((root.children as Array<Record<string, unknown>>)[0].hook).toBeUndefined()
  })
})
