import { describe, expect, it } from 'vitest'
import {
  collectFieldComponentEventsFromRules,
  resolveComponentEventFieldKey,
  subTableComponentEventFieldKey,
} from '../formCreateComponentEvents'

describe('formCreateComponentEvents (DW)', () => {
  it('resolveComponentEventFieldKey uses __subTable_${bindingId} for field-less subTable', () => {
    expect(subTableComponentEventFieldKey(9)).toBe('__subTable_9')
    expect(resolveComponentEventFieldKey({
      type: 'subTable',
      _bindingId: 9,
    })).toBe('__subTable_9')
    expect(resolveComponentEventFieldKey({
      type: 'subTable',
      props: { _bindingId: 11 },
    })).toBe('__subTable_11')
  })

  it('collectFieldComponentEventsFromRules indexes SubTable by bindingId', () => {
    const map = collectFieldComponentEventsFromRules([
      {
        type: 'subTable',
        _bindingId: 3,
        on: { change: '$FNX:\n$inject.api.setValue("x", 1)' },
      },
    ])
    expect(map.has('__subTable_3')).toBe(true)
    expect(map.get('__subTable_3')?.on.change).toBeDefined()
  })
})
