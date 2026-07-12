// Feature: sub-table-position-control, Property 1: parseFormConfig round-trip for subTable rules

import { describe, it, expect, vi } from 'vitest'
import * as fc from 'fast-check'
import { extractFieldsRecursive, parseFormConfigToTabs, extractTabsFromTabsRule, parseFormRulesLayout, collectPlacedSubTableBindingIds, collectSubTableFieldsFromLayout, mergeMissingSubTableFieldsIntoLayout, ensureSubTableBindingsOnFormLayout, removeSubTableFieldsByBindingIds, flattenAllFormFieldSegments, legacyBindingIdAliases } from '../formRendererHelpers'

// fast-check properties here run hundreds of parse/merge iterations; under full-suite parallel
// workers they intermittently exceed the 5s default timeout (they pass in isolation).
vi.setConfig({ testTimeout: 30_000 })

// A no-op converter — regular fields are not the focus of this property test
const noopConverter = () => null

describe('Property 1: parseFormConfig round-trip for subTable rules', () => {
  /**
   * Validates: Requirements 1.1, 1.2
   *
   * For any rule array containing one or more entries with `type: "subTable"` and
   * a numeric `_bindingId`, extractFieldsRecursive should produce a FormField with
   * `type: "subTable"` and the same `_bindingId` value for each such entry.
   */
  it('produces a subTable FormField with the correct _bindingId for each subTable rule', () => {
    fc.assert(
      fc.property(
        fc.array(fc.integer({ min: 1, max: 9999 }), { minLength: 1, maxLength: 5 }),
        (bindingIds) => {
          const rules = bindingIds.map(id => ({ type: 'subTable', _bindingId: id }))
          const fields = extractFieldsRecursive(rules, noopConverter)
          const subTableFields = fields.filter(f => f.type === 'subTable')
          expect(subTableFields.length).toBe(bindingIds.length)
          subTableFields.forEach((f, i) => {
            expect(f._bindingId).toBe(bindingIds[i])
          })
        },
      ),
      { numRuns: 100 },
    )
  })

  it('reads subTable binding id from props when designer keeps it there', () => {
    fc.assert(
      fc.property(
        fc.array(fc.integer({ min: 1, max: 9999 }), { minLength: 1, maxLength: 5 }),
        (bindingIds) => {
          const rules = bindingIds.map(id => ({ type: 'subTable', props: { _bindingId: id } }))
          const fields = extractFieldsRecursive(rules, noopConverter)
          const subTableFields = fields.filter(f => f.type === 'subTable')
          expect(subTableFields.map(f => f._bindingId)).toEqual(bindingIds)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('finds subTable placeholders nested inside layout containers', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 9999 }),
        (bindingId) => {
          const rules = [{
            type: 'group',
            children: [{
              type: 'el-row',
              children: [{
                type: 'el-col',
                children: [{ type: 'subTable', _bindingId: bindingId }],
              }],
            }],
          }]
          const fields = extractFieldsRecursive(rules, noopConverter)
          const walk = (arr: typeof fields): boolean =>
            arr.some(f =>
              (f.type === 'subTable' && f._bindingId === bindingId)
              || (Array.isArray(f.children) && walk(f.children)))
          expect(walk(fields)).toBe(true)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('deduplicates duplicate el-tab-pane names while keeping labels', () => {
    const rules = [{
      type: 'el-tabs',
      children: [
        { type: 'el-tab-pane', props: { name: 'TabPane', label: 'TabPane' }, children: [{ type: 'fcTitle', title: 'A' }] },
        { type: 'el-tab-pane', props: { name: 'TabPane', label: 'TabPane' }, children: [{ type: 'fcTitle', title: 'B' }] },
      ],
    }]
    const tabs = extractTabsFromTabsRule(rules[0], (items) => extractFieldsRecursive(items, noopConverter))
    expect(tabs).toHaveLength(2)
    expect(tabs[0].name).not.toBe(tabs[1].name)
    expect(tabs[0].label).toBe('TabPane')
    expect(tabs[1].label).toBe('TabPane')
  })

  it('preserves fcTitle auxiliary nodes inside tab panes', () => {
    const rules = [{
      type: 'el-tabs',
      children: [{
        type: 'el-tab-pane',
        props: { name: 'tab1', label: 'Tab 1' },
        children: [
          { type: 'fcTitle', title: 'Title' },
          { type: 'input', field: 'id', title: 'id As' },
        ],
      }],
    }]
    const tabs = extractTabsFromTabsRule(rules[0], (items) => extractFieldsRecursive(items, (item) => ({
      key: String(item.field),
      label: String(item.title ?? item.field),
      type: 'text',
      span: 24,
    })))
    expect(tabs[0].fields[0].type).toBe('title')
    expect(tabs[0].fields[0].label).toBe('Title')
    expect(tabs[0].fields[1].key).toBe('id')
  })

  it('collectPlacedSubTableBindingIds finds subTable inside card in fieldsAfterTabs', () => {
    const fieldsAfterTabs = [{
      type: 'card',
      key: 'card1',
      label: 'Title',
      children: [{ type: 'subTable', key: '__subTable_64', _bindingId: 64 }],
    }]
    const ids = collectPlacedSubTableBindingIds([], [], fieldsAfterTabs as any)
    expect(ids.has(64)).toBe(true)
  })

  it('mergeMissingSubTableFieldsIntoLayout restores FU subTable after task-form overwrite', () => {
    const fuField = { type: 'subTable' as const, key: '__subTable_64', label: '', _bindingId: 64, span: 24 }
    const layout = { fields: [{ key: 'a', label: 'a', type: 'text' as const }], tabs: [], fieldsAfterTabs: [] as any[] }
    mergeMissingSubTableFieldsIntoLayout(layout, [fuField], new Set([64]))
    expect(collectPlacedSubTableBindingIds(layout.fields, layout.tabs, layout.fieldsAfterTabs).has(64)).toBe(true)
  })

  it('ensureSubTableBindingsOnFormLayout appends widget when binding exists but layout has none', () => {
    const layout = { fields: [], tabs: [], fieldsAfterTabs: [] as any[] }
    ensureSubTableBindingsOnFormLayout(layout, [{ bindingId: 88 }], {})
    expect(collectPlacedSubTableBindingIds(layout.fields, layout.tabs, layout.fieldsAfterTabs).has(88)).toBe(true)
    expect(layout.fieldsAfterTabs[0].type).toBe('subTable')
  })

  it('mergeMissingSubTableFieldsIntoLayout accepts native binding id array (not only Set)', () => {
    const fuField = { type: 'subTable' as const, key: '__subTable_66', label: '', _bindingId: 66, span: 24 }
    const layout = { fields: [], tabs: [], fieldsAfterTabs: [] as any[] }
    expect(() => mergeMissingSubTableFieldsIntoLayout(layout, [fuField], [66, 103])).not.toThrow()
    expect(collectPlacedSubTableBindingIds(layout.fields, layout.tabs, layout.fieldsAfterTabs).has(66)).toBe(true)
  })

  it('removeSubTableFieldsByBindingIds accepts native binding id array (not only Set)', () => {
    const layout = {
      fields: [{ type: 'subTable' as const, key: '__subTable_66', _bindingId: 66 }],
      tabs: [],
      fieldsAfterTabs: [{ type: 'subTable' as const, key: '__subTable_103', _bindingId: 103 }],
    }
    expect(() => removeSubTableFieldsByBindingIds(layout, [66, 103])).not.toThrow()
    expect(collectPlacedSubTableBindingIds(layout.fields, layout.tabs, layout.fieldsAfterTabs).size).toBe(0)
  })

  it('collectSubTableFieldsFromLayout walks nested card children', () => {
    const fieldsAfterTabs = [{
      type: 'card' as const,
      key: 'c1',
      label: 'Card',
      children: [{ type: 'subTable' as const, key: 'st', label: '', _bindingId: 12, span: 24 }],
    }]
    const found = collectSubTableFieldsFromLayout([], [], fieldsAfterTabs)
    expect(found).toHaveLength(1)
    expect(found[0]._bindingId).toBe(12)
  })

  it('preserves elCard and elCollapse after tabs (designer camelCase layout)', () => {
    const toField = (item: Record<string, unknown>) => ({
      key: String(item.field),
      label: String(item.title ?? item.field),
      type: 'text' as const,
      span: 24,
    })
    const rules = [
      { type: 'elTabs', children: [{ type: 'elTabPane', props: { label: 'TabPane' }, children: [] }] },
      { type: 'elCard', props: { header: 'Title' }, children: [{ type: 'subTable', _bindingId: 64 }] },
      { type: 'div', _fc_drag_tag: 'space', style: { height: '20px' } },
      { type: 'elCard', props: { header: 'Title' }, children: [{ type: 'input', field: 'a', title: 'a' }] },
      {
        type: 'elCollapse',
        children: [{
          type: 'elCollapseItem',
          props: { title: 'CollapseItem' },
          children: [{ type: 'input', field: 'b', title: 'b' }],
        }],
      },
    ]
    const layout = parseFormRulesLayout(rules, (items) => extractFieldsRecursive(items, toField))
    expect(layout.fieldsAfterTabs).toHaveLength(4)
    expect(layout.fieldsAfterTabs[0].type).toBe('card')
    expect(layout.fieldsAfterTabs[0].label).toBe('Title')
    expect(layout.fieldsAfterTabs[1].type).toBe('space')
    expect(layout.fieldsAfterTabs[2].type).toBe('card')
    expect(layout.fieldsAfterTabs[3].type).toBe('collapse')
    expect(layout.fieldsAfterTabs[3].collapsePanels?.[0].label).toBe('CollapseItem')
    expect(layout.fieldsAfterTabs[3].collapsePanels?.[0].fields[0].key).toBe('b')
  })

  it('recognizes designer camelCase elTabs / elTabPane (form-create saved JSON)', () => {
    const rules = [
      {
        type: 'fcRow',
        children: [
          { type: 'col', props: { span: 12 }, children: [{ type: 'input', field: 'test1', title: 'test1' }] },
          { type: 'col', props: { span: 12 }, children: [{ type: 'input', field: 'test2', title: 'test2' }] },
        ],
      },
      {
        type: 'elTabs',
        children: [
          {
            type: 'elTabPane',
            props: { label: 'TabPane' },
            children: [{ type: 'input', field: 'id', title: 'id As' }],
          },
          {
            type: 'elTabPane',
            props: { label: 'TabPane' },
            children: [{ type: 'upload', field: 'fileupload', title: 'fileupload' }],
          },
        ],
      },
      {
        type: 'elCard',
        props: { header: 'Title' },
        children: [{ type: 'subTable', _bindingId: 64, title: 'Sub-Table' }],
      },
    ]
    const toField = (item: Record<string, unknown>) => ({
      key: String(item.field),
      label: String(item.title ?? item.field),
      type: 'text' as const,
      span: 24,
    })
    const layout = parseFormRulesLayout(rules, (items) => extractFieldsRecursive(items, toField))
    expect(layout.tabs).toHaveLength(2)
    expect(layout.tabs[0].label).toBe('TabPane')
    expect(layout.tabs[0].fields.some(f => f.key === 'id')).toBe(true)
    expect(layout.tabs[1].fields.some(f => f.key === 'fileupload')).toBe(true)
    expect(layout.fields.some(f => f.type === 'row')).toBe(true)
    expect(layout.fieldsAfterTabs.some(f => f.type === 'card')).toBe(true)
  })

  it('preserves fcRow/fcCol column layout instead of flattening fields', () => {
    const rules = [{
      type: 'fcRow',
      props: { gutter: 20 },
      children: [
        {
          type: 'fcCol',
          props: { span: 12 },
          children: [
            { type: 'input', field: 'test1', title: 'test1' },
            { type: 'input', field: 'test3', title: 'test3' },
          ],
        },
        {
          type: 'fcCol',
          props: { span: 12 },
          children: [{ type: 'input', field: 'test2', title: 'test2' }],
        },
      ],
    }]
    const fields = extractFieldsRecursive(rules, (item) => ({
      key: String(item.field),
      label: String(item.title ?? item.field),
      type: 'text',
      span: 24,
    }))
    expect(fields).toHaveLength(1)
    expect(fields[0].type).toBe('row')
    expect(fields[0].children).toHaveLength(2)
    expect(fields[0].children?.[0].type).toBe('col')
    expect(fields[0].children?.[0].children?.map(f => f.key)).toEqual(['test1', 'test3'])
    expect(fields[0].children?.[1].children?.map(f => f.key)).toEqual(['test2'])
  })

  it('preserves card layout containers with their nested fields', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 9999 }),
        fc.string({ minLength: 1, maxLength: 20 }),
        (bindingId, title) => {
          const rules = [{
            type: 'el-card',
            title,
            children: [{ type: 'subTable', props: { _bindingId: bindingId } }],
          }]
          const fields = extractFieldsRecursive(rules, noopConverter)
          expect(fields).toHaveLength(1)
          expect(fields[0].type).toBe('card')
          expect(fields[0].label).toBe(title)
          expect(fields[0].children?.some(f => f.type === 'subTable' && f._bindingId === bindingId)).toBe(true)
        },
      ),
      { numRuns: 100 },
    )
  })
})

describe('Property 6: Tab-pane subTable placeholder produces FormField in correct tab', () => {
  /**
   * Validates: Requirements 6.1, 6.2
   *
   * For any tabbed form config where a subTable rule appears inside a specific
   * el-tab-pane, parseFormConfigToTabs should include a FormField with
   * type: "subTable" and the correct _bindingId in that tab's fields array,
   * and not in any other tab's fields array.
   */
  it('places subTable FormField in the correct tab and not in other tabs', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 9999 }),
        // Exclude 'other' to avoid collision with the second tab's name
        fc.string({ minLength: 1 }).filter(s => s !== 'other'),
        (bindingId, tabName) => {
          const config = {
            rule: [{
              type: 'el-tabs',
              children: [
                {
                  type: 'el-tab-pane',
                  props: { name: tabName, label: tabName },
                  children: [{ type: 'subTable', _bindingId: bindingId }]
                },
                {
                  type: 'el-tab-pane',
                  props: { name: 'other', label: 'Other' },
                  children: []
                }
              ]
            }]
          }
          const tabs = parseFormConfigToTabs(JSON.stringify(config))
          const targetTab = tabs.find(t => t.name === tabName)
          const otherTab = tabs.find(t => t.name === 'other')
          expect(targetTab?.fields.some(f => f.type === 'subTable' && f._bindingId === bindingId)).toBe(true)
          expect(otherTab?.fields.some(f => f.type === 'subTable')).toBe(false)
        },
      ),
      { numRuns: 100 },
    )
  })
})

describe('flattenAllFormFieldSegments includes before/after tab fields', () => {
  it('merges fields, tab panes, and fieldsAfterTabs for initFormData', () => {
    const beforeTabs = [{
      type: 'row',
      key: '__layout_row',
      children: [
        { type: 'input', key: 'test1', label: 'Test 1' },
        { type: 'input', key: 'test2', label: 'Test 2' },
        { type: 'input', key: 'test3', label: 'Test 3' },
      ],
    }] as any[]
    const tabs = [{
      name: 'tab1',
      label: 'Tab 1',
      fields: [{ type: 'input', key: 'inTab', label: 'In Tab' }],
    }]
    const afterTabs = [{
      type: 'card',
      key: '__layout_card',
      label: 'Card',
      children: [{ type: 'input', key: 'afterTab', label: 'After Tab' }],
    }] as any[]

    const keys = flattenAllFormFieldSegments(beforeTabs, tabs, afterTabs).map(f => f.key)
    expect(keys).toEqual(expect.arrayContaining(['test1', 'test2', 'test3', 'inTab', 'afterTab']))
    expect(keys.filter(k => k === 'test2')).toHaveLength(1)
  })
})

describe('legacyBindingIdAliases', () => {
  it('maps dw binding ids to designer short keys', () => {
    expect(legacyBindingIdAliases(50066)).toEqual(expect.arrayContaining([50066, 66]))
    expect(legacyBindingIdAliases(50103)).toEqual(expect.arrayContaining([50103, 103]))
    expect(legacyBindingIdAliases(64)).toEqual([64])
  })
})

describe('resolveSubTableRowsForBinding — assignment sibling binding id', () => {
  it('resolves rows from sibling binding id and table name when own key is missing', async () => {
    const { resolveSubTableRowsForBinding } = await import('@/composables/tasks/shared')
    const rows64 = [{ id: 1, assignee: 'u1' }, { id: 2, assignee: 'u2' }]
    const saved = {
      '64': rows64,
      'Sub Task': rows64,
    }
    const rtMap = new Map<number, number | null>([
      [64, 20],
      [66, 20],
    ])
    const binding = {
      bindingId: 66,
      tableName: 'Sub Task',
      physicalTableName: 'subtable',
      tableId: 20,
      columns: [{ field: 'assignee' }, { field: 'task_status' }],
    }
    const bySibling = resolveSubTableRowsForBinding(saved, binding, {
      forbidNameFallback: true,
      bindingTableById: rtMap,
      mergeSiblingSlices: false,
    })
    expect(bySibling).toEqual(rows64)

    const byName = resolveSubTableRowsForBinding(saved, binding, {
      forbidNameFallback: false,
      bindingTableById: rtMap,
      mergeSiblingSlices: false,
    })
    expect(byName).toEqual(rows64)
  })
})
