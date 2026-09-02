import { describe, expect, it } from 'vitest'
import { createPortalFormApi } from '../formCreateEventRuntime'
import {
  isEffectivelyDisabled,
  mergeScriptLookupFilters,
  type FormEventChoiceOption,
  type PortalFormDisabledState,
} from '../formCreateEventOverlays'

describe('formCreateEventOverlays (DW)', () => {
  it('disabled overlay and options overlay match portal contract', () => {
    const flags = new Map<string, boolean>()
    const state: PortalFormDisabledState = { flags }
    const optionState = new Map<string, FormEventChoiceOption[]>()
    const form: Record<string, unknown> = { scenario: 'A' }
    const api = createPortalFormApi(
      () => form,
      (patch) => Object.assign(form, patch),
      undefined,
      undefined,
      undefined,
      undefined,
      {
        disabled: {
          state,
          notify: () => {},
          getAllFieldKeys: () => ['scenario'],
        },
        options: {
          state: optionState,
          notify: () => {},
          getDesignerOptions: () => [{ label: 'A', value: 'A' }],
        },
      },
    )
    api.disabled(true, 'scenario')
    expect(isEffectivelyDisabled('scenario', false, flags)).toBe(true)
    api.clearOptions('scenario')
    expect(optionState.get('scenario')).toEqual([])
    expect(form.scenario).toBe('A')
  })

  it('mergeScriptLookupFilters appends script conditions', () => {
    expect(
      mergeScriptLookupFilters(
        [{ fieldName: 'bu', value: 'HQ' }],
        [{ fieldName: 'status', value: 'Active' }],
      ),
    ).toEqual([
      { fieldName: 'bu', value: 'HQ' },
      { fieldName: 'status', value: 'Active', matchType: undefined },
    ])
  })
})
