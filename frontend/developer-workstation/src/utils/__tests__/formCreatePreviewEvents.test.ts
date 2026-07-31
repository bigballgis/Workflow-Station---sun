import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import {
  attachPreviewMountedDefaultSync,
  dispatchPreviewFieldValueChange,
  injectPreviewFieldLoadDefaults,
  materializePreviewComponentEvents,
  mergeComponentEventsFromSavedRules,
  preserveSerializedHandlersInShadowBuckets,
  registerPreviewVisibilityBridge,
  sanitizePreviewRuleHandlers,
  syncDesignerComponentEventsForFcPreview,
  unregisterPreviewVisibilityBridge,
} from '../formCreatePreviewEvents'
import { parseFormCreateEventHandler } from '../formCreateEventRuntime'

type FcTaggedHandler = ((inject: unknown) => void) & {
  __json?: unknown
  __inject?: boolean
}

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

  it('materialized select change + deferred bridge hides lookup via api.hidden', () => {
    const previewData = ref<Record<string, unknown>>({ select: 1, lookup: null })
    const hidden = new Map<string, boolean>()
    const display = new Map<string, boolean>()
    let tick = 0
    const bridge = {
      state: { hidden, display },
      notify: () => { tick++ },
      getAllFieldKeys: () => ['select', 'lookup'],
    }
    const hideSrc =
      '$FNX:\n$inject.api.hidden(true, \'lookup\')'
    const rules = [
      {
        type: 'select',
        field: 'select',
        _on: { change: hideSrc },
      },
      { type: 'lookup', field: 'lookup' },
    ]
    // Build-time materialize (before FormPreviewItems mount) — same as useFormPreviewBuild
    materializePreviewComponentEvents(rules, previewData)
    registerPreviewVisibilityBridge(bridge)
    try {
      const on = rules[0].on as Record<string, (...args: unknown[]) => void>
      expect(typeof on.change).toBe('function')
      on.change()
      expect(hidden.get('lookup')).toBe(true)
      expect(tick).toBeGreaterThan(0)

      // dispatch path (form-create @change) must also honor visibility via tagged source
      hidden.delete('lookup')
      tick = 0
      dispatchPreviewFieldValueChange(rules, 'select', 2, previewData, { visibility: bridge })
      expect(hidden.get('lookup')).toBe(true)
      expect(tick).toBeGreaterThan(0)
    } finally {
      unregisterPreviewVisibilityBridge(bridge)
    }
  })

  it('parseFormCreateEventHandler re-runs __hermesFormEventSource with ctx.api', () => {
    const calls: unknown[] = []
    const api = {
      setValue: () => {},
      getValue: () => undefined,
      form: {},
      hidden: (status: boolean, field?: string) => { calls.push([status, field]) },
      display: () => {},
      hiddenStatus: () => false,
      displayStatus: () => true,
      setFieldError: () => {},
      clearFieldError: () => {},
    }
    const wrapped = (() => {
      // stale closed-over api would push elsewhere — must not be used
    }) as (() => void) & { __hermesFormEventSource?: string }
    wrapped.__hermesFormEventSource = '$FNX:\n$inject.api.hidden(true, \'lookup\')'
    const fn = parseFormCreateEventHandler(wrapped)
    expect(fn).toBeTruthy()
    fn!({ field: 'select', value: 1, api, rule: {} })
    expect(calls).toEqual([[true, 'lookup']])
  })

  it('dispatchPreviewFieldValueChange api.hidden toggles preview visibility for __subTable_*', () => {
    const previewData = ref<Record<string, unknown>>({ user: null })
    const hidden = new Map<string, boolean>()
    const display = new Map<string, boolean>()
    let tick = 0
    const rules = [
      {
        type: 'lookup',
        field: 'user',
        hook: {
          value:
            '$FNX:\nvar v = $inject.value\nvar hasUser = v != null && v !== \'\' && !(Array.isArray(v) && v.length === 0)\n$inject.api.hidden(hasUser, \'__subTable_271\')',
        },
      },
    ]
    dispatchPreviewFieldValueChange(rules, 'user', { id: 'u1' }, previewData, {
      visibility: {
        state: { hidden, display },
        notify: () => { tick++ },
        getAllFieldKeys: () => ['user', '__subTable_271'],
      },
    })
    expect(hidden.get('__subTable_271')).toBe(true)
    expect(tick).toBeGreaterThan(0)

    dispatchPreviewFieldValueChange(rules, 'user', null, previewData, {
      visibility: {
        state: { hidden, display },
        notify: () => { tick++ },
        getAllFieldKeys: () => ['user', '__subTable_271'],
      },
    })
    expect(hidden.has('__subTable_271')).toBe(false)
  })

  it('syncDesignerComponentEventsForFcPreview compiles $FNX blur into callable on.blur/on.change', () => {
    const form: Record<string, unknown> = { case_type: 1, card_number: '' }
    const api = {
      setValue: (field: string, value: unknown) => { form[field] = value },
      getValue: (field: string) => form[field],
      form,
      hidden: () => {},
      display: () => {},
      hiddenStatus: () => false,
      displayStatus: () => true,
      setFieldError: () => {},
      clearFieldError: () => {},
    }
    const blurSrc =
      '$FNX:\nvar v = $inject.value\nif (v === 1 || v === \'1\') { $inject.api.setValue(\'card_number\', \'111\') }'
    const rules = [
      {
        type: 'select',
        field: 'case_type',
        _on: { blur: blurSrc },
      },
      { type: 'input', field: 'card_number' },
    ]
    syncDesignerComponentEventsForFcPreview(rules)
    expect(rules[0].inject).toBe(true)
    const on = rules[0].on as Record<string, FcTaggedHandler>
    expect(typeof on.blur).toBe('function')
    expect(typeof on.change).toBe('function')
    // openPreview getJson uses __json — without it Case Type events die after parseJson
    expect(on.blur.__json).toBe(blurSrc)
    expect(on.change.__json).toBe(blurSrc)
    expect(on.change.__inject).toBe(true)
    // form-create inject call style
    on.change({
      api,
      args: [1],
      value: 1,
    })
    expect(form.card_number).toBe('111')
  })

  it('syncDesignerComponentEventsForFcPreview handlers survive form-create toJson/parseJson roundtrip', async () => {
    const { toJson, parseJson } = await import('@form-create/utils/lib/json')
    const blurSrc =
      '$FNX:\nvar v = $inject.value\nif (v === 1 || v === \'1\') { $inject.api.setValue(\'card_number\', \'111\') }'
    const rules = [
      {
        type: 'select',
        field: 'case_type',
        _on: { blur: blurSrc },
      },
      { type: 'input', field: 'card_number' },
    ]
    syncDesignerComponentEventsForFcPreview(rules)
    // Mimic fc-designer openPreview: getJson → parseJson (functions without __json break here)
    const roundtripped = parseJson(toJson(rules)) as Array<Record<string, unknown>>
    const change = (roundtripped[0].on as Record<string, (inject: unknown) => void>).change
    expect(typeof change).toBe('function')
    const form: Record<string, unknown> = { case_type: 1, card_number: '' }
    change({
      api: {
        setValue: (field: string, value: unknown) => { form[field] = value },
        getValue: (field: string) => form[field],
      },
      value: 1,
      args: [1],
    })
    expect(form.card_number).toBe('111')
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

  it('dispatchPreviewFieldValueChange runs SubTable change via __subTable_${bindingId}', () => {
    const rules = [
      {
        type: 'subTable',
        _bindingId: 7,
        on: {
          change: '$FNX:\n$inject.api.setValue("rowCount", Array.isArray($inject.value) ? $inject.value.length : 0)',
        },
      },
    ]
    const previewData = ref<Record<string, unknown>>({ rowCount: 0 })
    dispatchPreviewFieldValueChange(rules, '__subTable_7', [{ a: 1 }], previewData)
    expect(previewData.value.rowCount).toBe(1)
    expect(previewData.value.__subTable_7).toEqual([{ a: 1 }])
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

  it('preserve + sanitize + sync recovers events for SubTableFormDialog path', () => {
    const form: Record<string, unknown> = { case_type: 'CNP', card_number: '' }
    const api = {
      setValue: (field: string, value: unknown) => { form[field] = value },
      getValue: (field: string) => form[field],
      form,
      hidden: () => {},
      display: () => {},
      hiddenStatus: () => false,
      displayStatus: () => true,
      setFieldError: () => {},
      clearFieldError: () => {},
    }
    const rules: Array<Record<string, unknown>> = [
      {
        type: 'select',
        field: 'case_type',
        // Designer sometimes only stores the serialized body on `on` (no `_on` yet).
        on: {
          blur: '$FNX:\nvar v = $inject.value\nif (v === \'CNP\') { $inject.api.setValue(\'card_number\', \'111\') }',
        },
      },
      { type: 'input', field: 'card_number' },
    ]
    preserveSerializedHandlersInShadowBuckets(rules)
    sanitizePreviewRuleHandlers(rules)
    syncDesignerComponentEventsForFcPreview(rules)
    ;((rules[0].on as Record<string, unknown>).change as (inject: unknown) => void)({
      api,
      args: ['CNP'],
      value: 'CNP',
    })
    expect(form.card_number).toBe('111')
  })
})
