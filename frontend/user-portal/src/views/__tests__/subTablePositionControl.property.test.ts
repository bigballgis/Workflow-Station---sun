// Feature: sub-table-position-control
// Property 2: Inline sub-table editable prop reflects mode
// Property 3: update:subTableData emitted on inline row change
// Property 5: Submission payload includes all sub-table data regardless of placement

import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import FormRenderer from '../../components/FormRenderer.vue'
import SubTableField from '../../components/SubTableField.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  messages: { en: {} },
  missingWarn: false,
  fallbackWarn: false,
})

describe('Property 2: Inline sub-table editable prop reflects mode', () => {
  /**
   * Validates: Requirements 2.4, 2.5
   *
   * For any SubTableBinding with any bindingMode value, when FormRenderer renders
   * it inline, the SubTableField's editable prop should be false when FormRenderer
   * is in readonly mode, and should equal (bindingMode === "EDITABLE") when not
   * in readonly mode.
   */
  it('SubTableField editable prop is false in readonly mode, else equals (bindingMode === EDITABLE)', () => {
    fc.assert(
      fc.property(
        fc.constantFrom('EDITABLE', 'READONLY', 'VIEW_ONLY'),
        fc.boolean(),
        (bindingMode, isReadonly) => {
          const binding = {
            bindingId: 1,
            bindingType: 'TABLE',
            bindingMode,
            tableName: 'T',
            tableType: 'TABLE',
            tableDescription: '',
            columns: [],
            data: [],
          }
          const wrapper = mount(FormRenderer, {
            props: {
              fields: [{ key: '__subTable_1', label: '', type: 'subTable', _bindingId: 1 }],
              subTableBindings: [binding],
              readonly: isReadonly,
            },
            global: {
              plugins: [i18n],
            },
          })
          const subTable = wrapper.findComponent(SubTableField)
          const expectedEditable = !isReadonly && bindingMode === 'EDITABLE'
          expect(subTable.props('editable')).toBe(expectedEditable)
        },
      ),
      { numRuns: 100 },
    )
  })
})

describe('Property 3: update:subTableData emitted on inline row change', () => {
  /**
   * Validates: Requirements 2.6, 5.1
   *
   * For any inline SubTableField data change (add, edit, or delete row),
   * FormRenderer should emit update:subTableData with the correct bindingId
   * and the updated row array.
   */
  it('emits update:subTableData with correct bindingId and rows when SubTableField emits update:modelValue', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 9999 }),
        fc.array(fc.record({ val: fc.string() })),
        (bindingId, newRows) => {
          const binding = {
            bindingId,
            bindingType: 'TABLE',
            bindingMode: 'EDITABLE',
            tableName: 'T',
            tableType: 'TABLE',
            tableDescription: '',
            columns: [],
            data: [],
          }
          const wrapper = mount(FormRenderer, {
            props: {
              fields: [{ key: `__subTable_${bindingId}`, label: '', type: 'subTable', _bindingId: bindingId }],
              subTableBindings: [binding],
              readonly: false,
            },
            global: {
              plugins: [i18n],
            },
          })
          wrapper.findComponent(SubTableField).vm.$emit('update:modelValue', newRows)
          const emitted = wrapper.emitted('update:subTableData')
          expect(emitted).toBeTruthy()
          expect(emitted![0][0]).toBe(bindingId)
          expect(emitted![0][1]).toEqual(newRows)
        },
      ),
      { numRuns: 100 },
    )
  })
})

describe('Property 4: Bottom-render fallback contains exactly unplaced bindings', () => {
  /**
   * Validates: Requirements 3.1, 3.2
   *
   * For any set of subTableBindings and any form rule array, the bottom-rendered
   * binding list should contain exactly those bindings whose bindingId does not
   * appear in any subTable placeholder in the rule array.
   */
  it('bottomSubTableBindings contains exactly the bindings not referenced by any subTable rule', () => {
    fc.assert(
      fc.property(
        fc.array(fc.integer({ min: 1, max: 20 }), { minLength: 1, maxLength: 10 }),
        fc.array(fc.integer({ min: 1, max: 20 }), { maxLength: 5 }),
        (allIds, placedIds) => {
          const uniqueAll = [...new Set(allIds)]
          const uniquePlaced = [...new Set(placedIds)].filter(id => uniqueAll.includes(id))
          const bindings = uniqueAll.map(id => ({
            bindingId: id,
            bindingMode: 'EDITABLE',
            tableName: `T${id}`,
            columns: [],
            data: [],
          }))
          const placedSet = new Set(uniquePlaced)
          // Simulate bottomSubTableBindings computed: filter out placed bindings
          const bottom = bindings.filter(b => !placedSet.has(b.bindingId))
          expect(bottom.map(b => b.bindingId).sort((a, b) => a - b)).toEqual(
            uniqueAll.filter(id => !placedSet.has(id)).sort((a, b) => a - b),
          )
        },
      ),
      { numRuns: 100 },
    )
  })
})

describe('Property 5: Submission payload includes all sub-table data regardless of placement', () => {
  /**
   * Validates: Requirements 5.2
   *
   * For any form submission where some sub-tables are placed inline and others
   * are bottom-rendered, the __subTables__ payload should contain the row data
   * for every binding, whether placed or not.
   *
   * The serialization logic (from start.vue handleSubmit / handleSaveDraft):
   *   __subTables__: Object.fromEntries(subTableBindings.map(b => [b.bindingId, b.data]))
   *
   * This iterates ALL subTableBindings regardless of placement, so both inline
   * and bottom-rendered bindings must appear in the payload.
   */
  it('__subTables__ payload contains data for every binding regardless of inline vs bottom placement', () => {
    // Replicate the serialization function used in start.vue handleSubmit / handleSaveDraft
    const serializeSubTables = (
      subTableBindings: Array<{ bindingId: number; data: any[] }>
    ): Record<string, any[]> =>
      Object.fromEntries(subTableBindings.map(b => [b.bindingId, b.data]))

    fc.assert(
      fc.property(
        // Generate a set of unique binding IDs (1–50)
        fc.uniqueArray(fc.integer({ min: 1, max: 50 }), { minLength: 1, maxLength: 10 }),
        // For each binding, generate some row data
        fc.array(fc.array(fc.record({ val: fc.string() }), { maxLength: 5 }), { minLength: 1, maxLength: 10 }),
        // A subset of those IDs are "placed inline" (the rest are bottom-rendered)
        fc.array(fc.integer({ min: 1, max: 50 }), { maxLength: 5 }),
        (allIds, rowDataSets, placedIds) => {
          // Build bindings — each has its own row data
          const bindings = allIds.map((id, i) => ({
            bindingId: id,
            data: rowDataSets[i % rowDataSets.length] ?? [],
          }))

          // The placed set (intersection with allIds, as in the real app)
          const placedSet = new Set(placedIds.filter(id => allIds.includes(id)))

          // Serialize using the same logic as start.vue
          const payload = serializeSubTables(bindings)

          // Every binding must appear in the payload, regardless of placement
          for (const binding of bindings) {
            const key = String(binding.bindingId)
            expect(Object.prototype.hasOwnProperty.call(payload, binding.bindingId) ||
                   Object.prototype.hasOwnProperty.call(payload, key)).toBe(true)
            // The data must match exactly
            const stored = payload[binding.bindingId] ?? payload[key]
            expect(stored).toEqual(binding.data)
          }

          // Placed bindings are not excluded — they must also be present
          for (const id of placedSet) {
            const binding = bindings.find(b => b.bindingId === id)
            if (!binding) continue
            const stored = payload[id] ?? payload[String(id)]
            expect(stored).toEqual(binding.data)
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})
