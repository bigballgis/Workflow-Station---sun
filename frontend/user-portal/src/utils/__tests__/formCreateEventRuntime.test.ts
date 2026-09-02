import { describe, expect, it } from 'vitest'
import {
  createFormEventOptionsBridge,
  createPortalFormApi,
  createFieldKeyResolver,
  parseFormCreateFunction,
  runFormOnChangeHandler,
  wrapFormLevelOnChangeForFormCreate,
  type PortalFormApi,
} from '../formCreateEventRuntime'

describe('formCreateEventRuntime', () => {
  it('parses FORM-CREATE-PREFIX onChange and syncs test3 -> test2 via api alias', () => {
    const raw =
      "[[FORM-CREATE-PREFIX-function (field, value, options){\n  if (field === 'test3') {\n     api.setValue('test2', value)\n   }\n}-FORM-CREATE-SUFFIX]]"

    const formData: Record<string, unknown> = { test2: '', test3: '' }
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
    )

    runFormOnChangeHandler(raw, 'test3', 'hello', api)

    expect(formData.test3).toBe('')
    expect(formData.test2).toBe('hello')
  })

  it('parses handler using options.setValue', () => {
    const raw =
      "[[FORM-CREATE-PREFIX-function (field, value, options){\n  if (field === 'a') options.setValue('b', value)\n}-FORM-CREATE-SUFFIX]]"

    const formData: Record<string, unknown> = {}
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
    )

    runFormOnChangeHandler(raw, 'a', 42, api)
    expect(formData.b).toBe(42)
  })

  it('rejects dangerous keywords', () => {
    const raw =
      '[[FORM-CREATE-PREFIX-function (field, value, options){ eval("1") }-FORM-CREATE-SUFFIX]]'
    expect(parseFormCreateFunction(raw)).toBeNull()
  })

  it('resolves setValue target by field label when bound key differs', () => {
    const formData: Record<string, unknown> = { tes2: '', test3: '' }
    const resolver = createFieldKeyResolver(() => [
      { key: 'tes2', label: 'test2' },
      { key: 'test3', label: 'test3' },
    ])
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
      resolver,
    )

    api.setValue('test2', 'hello')

    expect(formData.tes2).toBe('hello')
    expect(formData.test2).toBeUndefined()
  })

  it('supports hidden/display with label-based field resolution', () => {
    const vis = { hidden: new Map<string, boolean>(), display: new Map<string, boolean>() }
    let tick = 0
    const resolver = createFieldKeyResolver(() => [
      { key: 'tes2', label: 'test2' },
      { key: 'test3', label: 'test3' },
    ])
    const api = createPortalFormApi(
      () => ({}),
      () => {},
      resolver,
      {
        state: vis,
        notify: () => { tick++ },
        getAllFieldKeys: () => ['tes2', 'test3'],
      },
    )

    api.hidden(true, 'test2')
    expect(tick).toBe(1)
    expect(vis.hidden.get('tes2')).toBe(true)
    expect(api.hiddenStatus('test2')).toBe(true)

    api.hidden(false, 'test2')
    expect(vis.hidden.has('tes2')).toBe(false)

    api.display(true, 'test2')
    expect(vis.display.get('tes2')).toBe(false)
    expect(api.displayStatus('test2')).toBe(false)
  })

  it('runs onChange hidden when test3 equals 1', () => {
    const raw =
      "[[FORM-CREATE-PREFIX-function (field, value, options){\n  const test3Val = field === 'test3' ? value : options.getValue('test3')\n  const show = test3Val === 1 || test3Val === '1'\n  options.hidden(!show, 'test2')\n}-FORM-CREATE-SUFFIX]]"
    const vis = { hidden: new Map<string, boolean>(), display: new Map<string, boolean>() }
    const resolver = createFieldKeyResolver(() => [
      { key: 'tes2', label: 'test2' },
      { key: 'test3', label: 'test3' },
    ])
    const formData: Record<string, unknown> = { test3: '1' }
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
      resolver,
      {
        state: vis,
        notify: () => {},
        getAllFieldKeys: () => ['tes2', 'test3'],
      },
    )

    runFormOnChangeHandler(raw, 'test3', '1', api)
    expect(vis.hidden.has('tes2')).toBe(false)

    runFormOnChangeHandler(raw, 'test3', '2', api)
    expect(vis.hidden.get('tes2')).toBe(true)
  })

  it('wrapFormLevelOnChangeForFormCreate bridges form-create inject bag to options.hidden', () => {
    const raw =
      "[[FORM-CREATE-PREFIX-function onChange(field, value, options){\n  options.hidden(true, 'Dept')\n}-FORM-CREATE-SUFFIX]]"
    const hiddenCalls: Array<{ status: boolean; field?: string | string[] }> = []
    const fcApi = createPortalFormApi(
      () => ({}),
      () => {},
      undefined,
      {
        state: { hidden: new Map(), display: new Map() },
        notify: () => {},
        getAllFieldKeys: () => ['Dept'],
      },
    )
    fcApi.hidden = (status, field) => {
      hiddenCalls.push({ status, field })
    }
    const wrapped = wrapFormLevelOnChangeForFormCreate(raw) as (
      field: string,
      value: unknown,
      inject: { api: PortalFormApi; rule?: Record<string, unknown> },
    ) => void
    wrapped('Paid_By_Type', '1', { api: fcApi, rule: {} })
    expect(hiddenCalls).toEqual([{ status: true, field: 'Dept' }])
  })

  it('setFieldError and clearFieldError invoke field error callbacks', () => {
    const errors: string[] = []
    const api = createPortalFormApi(
      () => ({}),
      () => {},
      undefined,
      undefined,
      {
        setFieldError: (key, msg) => errors.push(`${key}:${msg}`),
        clearFieldError: (key) => errors.push(`clear:${key}`),
      },
    )
    api.setFieldError('enddate', 'Start date must be before end date.')
    api.clearFieldError('enddate')
    expect(errors).toEqual([
      'enddate:Start date must be before end date.',
      'clear:enddate',
    ])
  })

  it('runs $FNX component body with bare api (designer Event panel style)', () => {
    const vis = { hidden: new Map<string, boolean>(), display: new Map<string, boolean>() }
    const formData: Record<string, unknown> = { select: '1', lookup: null }
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
      undefined,
      {
        state: vis,
        notify: () => {},
        getAllFieldKeys: () => ['select', 'lookup'],
      },
    )
    runFormOnChangeHandler(
      '$FNX:\napi.hidden(true, "lookup")',
      'select',
      '1',
      api,
    )
    expect(vis.hidden.get('lookup')).toBe(true)
  })

  it('required/requiredStatus overlay designer required and resolve labels', () => {
    const flags = new Map<string, boolean>()
    const state = { flags }
    let tick = 0
    const resolver = createFieldKeyResolver(() => [
      { key: 'start_date', label: 'Start date' },
      { key: 'end_date', label: 'Need-by date' },
    ])
    const api = createPortalFormApi(
      () => ({}),
      () => {},
      resolver,
      undefined,
      undefined,
      {
        state,
        notify: () => { tick++ },
        getAllFieldKeys: () => ['start_date', 'end_date'],
      },
    )

    api.required(true, 'Start date')
    expect(tick).toBe(1)
    expect(flags.get('start_date')).toBe(true)
    expect(api.requiredStatus('Start date')).toBe(true)
    expect(api.requiredStatus('start_date')).toBe(true)

    api.required(false, ['start_date', 'end_date'])
    expect(flags.get('start_date')).toBe(false)
    expect(flags.get('end_date')).toBe(false)
    expect(api.requiredStatus('end_date')).toBe(false)
  })

  it('runs $FNX api.required like the designer Event panel', () => {
    const state = { flags: new Map<string, boolean>() }
    const api = createPortalFormApi(
      () => ({ scenario: 'A' }),
      () => {},
      undefined,
      undefined,
      undefined,
      {
        state,
        notify: () => {},
        getAllFieldKeys: () => ['scenario', 'start_date', 'end_date'],
      },
    )
    runFormOnChangeHandler(
      '$FNX:\nvar on = $inject.value === \'A\'\n$inject.api.required(on, [\'start_date\', \'end_date\'])',
      'scenario',
      'A',
      api,
    )
    expect(state.flags.get('start_date')).toBe(true)
    expect(state.flags.get('end_date')).toBe(true)
  })

  it('native form-create bridge api.required sets $required and syncs array fields', () => {
    const rules: Record<string, Record<string, unknown>> = {
      start_date: { field: 'start_date' },
      end_date: { field: 'end_date' },
    }
    const synced: string[] = []
    const effects: Array<[string, string, unknown]> = []
    const fcApi = {
      setValue: () => {},
      getValue: () => undefined,
      form: {},
      hidden: () => {},
      display: () => {},
      hiddenStatus: () => false,
      displayStatus: () => true,
      setFieldError: () => {},
      clearFieldError: () => {},
      mergeRule: (field, patch) => {
        Object.assign(rules[field], patch)
      },
      setEffect: (field, attr, value) => {
        effects.push([field, attr, value])
      },
      sync: (field) => {
        synced.push(field)
      },
    }
    const bridge = createFormEventOptionsBridge(fcApi)
    bridge.required(true, ['start_date', 'end_date'])
    expect(rules.start_date.$required).toBe(true)
    expect(rules.end_date.$required).toBe(true)
    expect(synced).toEqual(['start_date', 'end_date'])
    expect(effects).toEqual([
      ['start_date', 'required', true],
      ['end_date', 'required', true],
    ])
    bridge.required(false, 'start_date')
    expect(rules.start_date.$required).toBe(false)
  })
})
