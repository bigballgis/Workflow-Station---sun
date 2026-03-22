// Feature: sub-table-position-control, Property 1: parseFormConfig round-trip for subTable rules

import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { extractFieldsRecursive, parseFormConfigToTabs } from '../formRendererHelpers'

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
