import { describe, expect, it } from 'vitest'
import {
  createPortalFormApi,
  createFieldKeyResolver,
  parseFormCreateFunction,
  runFormOnChangeHandler,
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
})
