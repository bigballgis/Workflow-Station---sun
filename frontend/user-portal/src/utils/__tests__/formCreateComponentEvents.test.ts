import { describe, expect, it } from 'vitest'
import {
  collectFieldComponentEventsFromRules,
  runComponentFieldEvents,
  runComponentFieldEventsOnValueChange,
} from '../formCreateComponentEvents'
import { createPortalFormApi } from '../formCreateEventRuntime'

const emptyFn = (params: string, body = '') =>
  `[[FORM-CREATE-PREFIX-function (${params}){\n  ${body}\n}-FORM-CREATE-SUFFIX]]`

describe('formCreateComponentEvents', () => {
  it('collects blur from _on when on.blur is missing (designer persist gap)', () => {
    const rules = [
      {
        type: 'input',
        field: 'case_number',
        _on: {
          blur: '$FNX:\nif ($inject.value === "abc") { $inject.api.setValue("legal_hold", true) } else { $inject.api.setValue("legal_hold", false) }',
        },
      },
      { type: 'switch', field: 'legal_hold' },
    ]
    const map = collectFieldComponentEventsFromRules(rules)
    const blur = map.get('case_number')?.on.blur
    expect(typeof blur).toBe('string')
    expect(String(blur)).toContain('legal_hold')
  })

  it('collects on/_hook by field', () => {
    const rules = [
      {
        type: 'input',
        field: 'case_number',
        on: { change: emptyFn('value, api') },
        _hook: { value: emptyFn('api') },
      },
    ]
    const map = collectFieldComponentEventsFromRules(rules)
    expect(map.get('case_number')?.on.change).toBeDefined()
    expect(map.get('case_number')?.hook.value).toBeDefined()
  })

  it('runs non-empty change handler', () => {
    const rules = [
      {
        type: 'input',
        field: 'case_number',
        on: {
          change: emptyFn('value, api', 'if (value === 1) api.setValue("legal_hold", true)'),
        },
      },
      { type: 'switch', field: 'legal_hold' },
    ]
    const formData: Record<string, unknown> = { case_number: '', legal_hold: false }
    const map = collectFieldComponentEventsFromRules(rules)
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
    )
    runComponentFieldEvents(map.get('case_number'), {
      field: 'case_number',
      value: 1,
      api,
      onEvent: 'change',
    })
    expect(formData.legal_hold).toBe(true)
  })

  it('mirrors on.blur when lookup value changes', () => {
    const rules = [
      {
        type: 'lookup',
        field: 'test',
        on: {
          change: '$FNX:\nif ($inject.value != null) { $inject.api.setValue("testvalue", "successful") }',
        },
      },
      { type: 'input', field: 'testvalue' },
    ]
    const formData: Record<string, unknown> = { test: null, testvalue: '' }
    const map = collectFieldComponentEventsFromRules(rules)
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
    )
    runComponentFieldEventsOnValueChange(map.get('test'), {
      field: 'test',
      value: { id: 'row-1', name: 'Demo' },
      api,
      onEvent: 'change',
      fieldType: 'lookup',
    })
    expect(formData.testvalue).toBe('successful')
  })

  it('mirrors on.blur when select value changes (designer blur on select)', () => {
    const rules = [
      {
        type: 'select',
        field: 'select',
        on: {
          blur: '$FNX:\nvar hide = $inject.value === 1 || $inject.value === \'1\'\n$inject.api.hidden(hide, \'fileupload\')',
        },
      },
      { type: 'upload', field: 'fileupload' },
    ]
    const formData: Record<string, unknown> = { select: '', fileupload: '' }
    const map = collectFieldComponentEventsFromRules(rules)
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
      undefined,
      {
        state: { hidden: new Map(), display: new Map() },
        notify: () => {},
        getAllFieldKeys: () => ['select', 'fileupload'],
      },
    )
    runComponentFieldEventsOnValueChange(map.get('select'), {
      field: 'select',
      value: '1',
      api,
      onEvent: 'change',
      fieldType: 'select',
    })
    expect(api.hiddenStatus('fileupload')).toBe(true)
  })

  it('runs blur handler on field blur with final value', () => {
    const rules = [
      {
        type: 'input',
        field: 'case_number',
        on: {
          blur: emptyFn(
            'value, api',
            'if (value === "abc") api.setValue("legal_hold", true); else api.setValue("legal_hold", false)',
          ),
        },
      },
      { type: 'switch', field: 'legal_hold' },
    ]
    const formData: Record<string, unknown> = { case_number: 'abcd', legal_hold: true }
    const map = collectFieldComponentEventsFromRules(rules)
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
    )
    runComponentFieldEvents(map.get('case_number'), {
      field: 'case_number',
      value: 'abcd',
      api,
      onEvent: 'blur',
    })
    expect(formData.legal_hold).toBe(false)
  })

  it('collects SubTable on.change by __subTable_${bindingId} when rule has no field', () => {
    const rules = [
      {
        type: 'subTable',
        _bindingId: 42,
        on: {
          change: emptyFn('value, api', 'api.setValue("flag", Array.isArray(value) ? value.length : 0)'),
        },
      },
      { type: 'input', field: 'flag' },
    ]
    const map = collectFieldComponentEventsFromRules(rules)
    expect(map.get('__subTable_42')?.on.change).toBeDefined()

    const formData: Record<string, unknown> = { flag: 0 }
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
    )
    runComponentFieldEventsOnValueChange(map.get('__subTable_42'), {
      field: '__subTable_42',
      value: [{ id: 1 }, { id: 2 }],
      api,
      onEvent: 'change',
      hookEvent: 'value',
      fieldType: 'subTable',
    })
    expect(formData.flag).toBe(2)
  })
})
