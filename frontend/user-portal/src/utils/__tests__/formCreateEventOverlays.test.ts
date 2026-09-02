import { describe, expect, it } from 'vitest'
import { createPortalFormApi } from '../formCreateEventRuntime'
import {
  isEffectivelyDisabled,
  mergeScriptLookupFilters,
  type FormEventChoiceOption,
  type FormEventLookupFilter,
  type FormEventNotification,
  type PortalFormDisabledState,
} from '../formCreateEventOverlays'

describe('formCreateEventOverlays', () => {
  it('disabled overlay wins over designer fallback and disabledStatus reads the flag', () => {
    const flags = new Map<string, boolean>()
    const state: PortalFormDisabledState = { flags }
    let tick = 0
    const api = createPortalFormApi(
      () => ({}),
      () => {},
      undefined,
      undefined,
      undefined,
      undefined,
      {
        disabled: {
          state,
          notify: () => {
            tick++
          },
          getAllFieldKeys: () => ['cost_center', 'notes'],
        },
      },
    )
    api.disabled(true, 'cost_center')
    expect(api.disabledStatus('cost_center')).toBe(true)
    expect(isEffectivelyDisabled('cost_center', false, flags)).toBe(true)
    expect(isEffectivelyDisabled('notes', false, flags)).toBe(false)
    api.disabled(false)
    expect(api.disabledStatus('cost_center')).toBe(false)
    expect(api.disabledStatus('notes')).toBe(false)
    expect(tick).toBe(2)
  })

  it('setOptions / addOption / clearOptions / resetOptions; clear does not change field value', () => {
    const form: Record<string, unknown> = { scenario: 'A' }
    const optionState = new Map<string, FormEventChoiceOption[]>()
    const designer: FormEventChoiceOption[] = [
      { label: 'A', value: 'A' },
      { label: 'B', value: 'B' },
    ]
    const api = createPortalFormApi(
      () => form,
      (patch) => Object.assign(form, patch),
      undefined,
      undefined,
      undefined,
      undefined,
      {
        options: {
          state: optionState,
          notify: () => {},
          getDesignerOptions: () => designer,
        },
      },
    )
    api.setOptions('scenario', [{ label: 'A only', value: 'A' }])
    expect(optionState.get('scenario')).toEqual([{ label: 'A only', value: 'A' }])
    api.addOption('scenario', { label: 'C', value: 'C' })
    expect(optionState.get('scenario')?.map((o) => o.value)).toEqual(['A', 'C'])
    api.clearOptions('scenario')
    expect(optionState.get('scenario')).toEqual([])
    expect(form.scenario).toBe('A')
    api.resetOptions('scenario')
    expect(optionState.has('scenario')).toBe(false)
  })

  it('form notification set/clear; lookup filter AND merge; refresh bumps handler', () => {
    const notes: FormEventNotification[] = []
    const filters = new Map<string, FormEventLookupFilter[]>()
    const refreshed: string[] = []
    const api = createPortalFormApi(
      () => ({}),
      () => {},
      undefined,
      undefined,
      undefined,
      undefined,
      {
        notifications: {
          set: (item) => {
            notes.splice(0, notes.length, item)
          },
          clear: (id) => {
            const i = notes.findIndex((n) => n.uniqueId === id)
            if (i >= 0) notes.splice(i, 1)
          },
        },
        lookupFilter: {
          set: (key, conditions) => {
            filters.set(key, conditions)
          },
          clear: (key) => {
            filters.delete(key)
          },
          refresh: (key) => {
            refreshed.push(key)
          },
        },
      },
    )
    api.setFormNotification('Check the title', 'ERROR', 'title-err')
    expect(notes[0]).toEqual({ uniqueId: 'title-err', level: 'ERROR', message: 'Check the title' })
    api.clearFormNotification('title-err')
    expect(notes).toEqual([])
    api.setLookupFilter('vendor', [{ fieldName: 'status', value: 'Active', matchType: 'eq' }])
    const merged = mergeScriptLookupFilters(
      [{ fieldName: 'bu', value: 'HQ' }],
      filters.get('vendor'),
    )
    expect(merged).toEqual([
      { fieldName: 'bu', value: 'HQ' },
      { fieldName: 'status', value: 'Active', matchType: 'eq' },
    ])
    api.refresh('vendor')
    expect(refreshed).toEqual(['vendor'])
  })

  it('setLabel / getLabel / resetLabel', () => {
    const labels = new Map<string, string>()
    const api = createPortalFormApi(
      () => ({}),
      () => {},
      undefined,
      undefined,
      undefined,
      undefined,
      {
        labels: {
          state: labels,
          notify: () => {},
          getDesignerLabel: () => 'Cost center',
        },
      },
    )
    expect(api.getLabel('cost_center')).toBe('Cost center')
    api.setLabel('cost_center', 'Charge code')
    expect(api.getLabel('cost_center')).toBe('Charge code')
    api.resetLabel('cost_center')
    expect(api.getLabel('cost_center')).toBe('Cost center')
  })
})
