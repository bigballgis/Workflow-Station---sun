import { describe, it, expect } from 'vitest'
import {
  collectPlacedSubTableBindingIds,
  collectSubTableFieldsFromLayout,
  collectRuleBindingIds,
  removeSubTableFieldsByBindingIds,
  ensureSubTableBindingsOnFormLayout,
  filterLinkOnlyStandaloneSubTableFields,
} from '../formRendererHelpers/formRendererSubTableBindings'
import type { FormField, FormLayoutBuckets } from '../formRendererHelpers'

/**
 * An `inlineSubForm` node must count as "placed" everywhere a `subTable` node does.
 * If collectPlacedSubTableBindingIds misses it, the binding is filtered out of
 * subTableBindings upstream, resolveBinding() returns undefined, and the widget renders
 * nothing at all — with no error anywhere. That is the single most likely way this
 * component silently breaks.
 */

const inlineField = (bindingId = 66): FormField => ({
  key: `__inlineSubForm_${bindingId}`,
  label: '',
  type: 'inlineSubForm',
  _bindingId: bindingId,
})

const subTableField = (bindingId = 77): FormField => ({
  key: `__subTable_${bindingId}`,
  label: '',
  type: 'subTable',
  _bindingId: bindingId,
})

const buckets = (fields: FormField[]): FormLayoutBuckets => ({
  fields,
  tabs: [],
  fieldsAfterTabs: [],
})

describe('collectPlacedSubTableBindingIds', () => {
  it('counts an inlineSubForm node as placing its binding', () => {
    const ids = collectPlacedSubTableBindingIds([inlineField(66)])
    expect([...ids]).toEqual([66])
  })

  it('still counts subTable nodes', () => {
    const ids = collectPlacedSubTableBindingIds([subTableField(77), inlineField(66)])
    expect([...ids].sort()).toEqual([66, 77])
  })

  it('finds an inlineSubForm nested inside a layout container', () => {
    const nested: FormField = {
      key: 'col1', label: '', type: 'col', children: [inlineField(66)],
    }
    expect([...collectPlacedSubTableBindingIds([nested])]).toEqual([66])
  })
})

describe('collectSubTableFieldsFromLayout', () => {
  it('returns inlineSubForm nodes alongside subTable nodes', () => {
    const out = collectSubTableFieldsFromLayout([subTableField(77), inlineField(66)])
    expect(out.map(f => f.type).sort()).toEqual(['inlineSubForm', 'subTable'])
  })
})

describe('collectRuleBindingIds (raw designer rules)', () => {
  it('reads _bindingId off a top-level inlineSubForm rule', () => {
    const ids = collectRuleBindingIds([{ type: 'inlineSubForm', _bindingId: 66 }])
    expect([...ids]).toEqual([66])
  })

  it('reads _bindingId out of props when the top-level copy is absent', () => {
    const ids = collectRuleBindingIds([{ type: 'inlineSubForm', props: { _bindingId: 66 } }])
    expect([...ids]).toEqual([66])
  })
})

describe('removeSubTableFieldsByBindingIds', () => {
  // Mutates the layout in place and returns void.
  it('strips an inlineSubForm node whose binding is being removed', () => {
    const layout = buckets([inlineField(66), { key: 'note', label: 'Note', type: 'text' }])
    removeSubTableFieldsByBindingIds(layout, new Set([66]))
    expect(layout.fields.map(f => f.key)).toEqual(['note'])
  })

  it('leaves an inlineSubForm node whose binding is not being removed', () => {
    const layout = buckets([inlineField(66)])
    removeSubTableFieldsByBindingIds(layout, new Set([99]))
    expect(layout.fields.map(f => f.key)).toEqual(['__inlineSubForm_66'])
  })
})

describe('ensureSubTableBindingsOnFormLayout', () => {
  /**
   * The anti-double-render assertion: because the inlineSubForm node makes the binding count
   * as placed, no duplicate standalone subTable grid may be appended for the same binding.
   */
  it('does not append a duplicate standalone subTable for a binding already inlined', () => {
    const layout = buckets([inlineField(66)])
    ensureSubTableBindingsOnFormLayout(layout, [{ bindingId: 66, bindingType: 'SUB' }] as never)
    const appended = [...layout.fields, ...layout.fieldsAfterTabs]
      .filter(f => f.type === 'subTable')
    expect(appended).toHaveLength(0)
  })

  it('still appends a grid for a binding that is not placed at all', () => {
    const layout = buckets([{ key: 'note', label: 'Note', type: 'text' }])
    ensureSubTableBindingsOnFormLayout(layout, [{ bindingId: 66, bindingType: 'SUB' }] as never)
    const appended = [...layout.fields, ...layout.fieldsAfterTabs]
      .filter(f => f.type === 'subTable')
    expect(appended).toHaveLength(1)
  })
})

describe('filterLinkOnlyStandaloneSubTableFields', () => {
  // The filter early-returns true for anything that is not `subTable`, so the widget is
  // preserved by default. Locked down here so a future edit to that guard cannot drop it.
  it('preserves inlineSubForm nodes', () => {
    const out = filterLinkOnlyStandaloneSubTableFields(
      [inlineField(66)],
      [] as never,
      new Set<number>(),
      undefined as never,
    )
    expect(out.map(f => f.key)).toEqual(['__inlineSubForm_66'])
  })
})
