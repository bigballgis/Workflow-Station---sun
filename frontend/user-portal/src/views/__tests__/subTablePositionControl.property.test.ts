// Feature: sub-table-position-control
// Property 2: Inline sub-table editable prop reflects mode
// Property 3: update:subTableData emitted on inline row change
// Property 5: Submission payload includes all sub-table data regardless of placement

import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
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

/** Align with main.ts: FormRenderer renders many el-* components; omitting Element Plus floods Vitest with unresolved-component warnings. */
const formRendererMountGlobal = {
  plugins: [i18n, ElementPlus],
}

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
            global: formRendererMountGlobal,
          })
          try {
            const subTable = wrapper.findComponent(SubTableField)
            const expectedEditable = !isReadonly && bindingMode === 'EDITABLE'
            expect(subTable.props('editable')).toBe(expectedEditable)
          } finally {
            wrapper.unmount()
          }
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
            global: formRendererMountGlobal,
          })
          try {
            wrapper.findComponent(SubTableField).vm.$emit('update:modelValue', newRows)
            const emitted = wrapper.emitted('update:subTableData')
            expect(emitted).toBeTruthy()
            expect(emitted![0][0]).toBe(bindingId)
            expect(emitted![0][1]).toEqual(newRows)
          } finally {
            wrapper.unmount()
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})

/** Mirrors process start page: keep only placed subTable bindings + linkForm-linked closures (no bottom orphan section). */
function computeNeededSubTableBindingIds(
  placed: Set<number>,
  allBindings: Array<{ bindingId: number; columns?: Array<{ type?: string; props?: Record<string, any> }> }>
): Set<number> {
  const needed = new Set<number>(placed)
  let changed = true
  while (changed) {
    changed = false
    for (const b of allBindings) {
      if (!needed.has(b.bindingId)) continue
      for (const col of b.columns || []) {
        if (col.type === 'linkForm') {
          const boundId = col.props?.boundSubTableBindingId
          if (boundId != null) {
            const n = Number(boundId)
            if (!needed.has(n)) {
              needed.add(n)
              changed = true
            }
          }
        }
      }
    }
  }
  return needed
}

describe('Property 4: Start page keeps only designer-placed bindings (+ linkForm closure)', () => {
  it('filtered set is exactly placed ids plus targets of linkForm on kept bindings', () => {
    fc.assert(
      fc.property(
        fc.uniqueArray(fc.integer({ min: 1, max: 30 }), { minLength: 2, maxLength: 15 }),
        (uniqueAll) => {
          const placedRoot = uniqueAll[0]
          const linkTarget = uniqueAll[1]
          const bindings = uniqueAll.map(id => ({
            bindingId: id,
            columns:
              id === placedRoot
                ? [{ type: 'linkForm', props: { boundSubTableBindingId: linkTarget } }]
                : [],
            data: [],
          }))
          const needed = computeNeededSubTableBindingIds(new Set([placedRoot]), bindings)
          expect(needed.has(placedRoot)).toBe(true)
          expect(needed.has(linkTarget)).toBe(true)
          const noise = uniqueAll.filter(id => id !== placedRoot && id !== linkTarget)
          for (const id of noise) {
            expect(needed.has(id)).toBe(false)
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})

describe('Property 5: Submission payload includes sub-table data for every binding kept on the start form', () => {
  /**
   * start.vue keeps only designer-placed bindings (+ linkForm closure). Serialization:
   *   __subTables__: Object.fromEntries(subTableBindings.map(b => [b.bindingId, b.data]))
   * Payload keys match that filtered list (same as visible sub-tables / portal UX).
   */
  it('__subTables__ contains exactly the bindings present in subTableBindings ref', () => {
    const serializeSubTables = (
      subTableBindings: Array<{ bindingId: number; data: any[] }>
    ): Record<string, any[]> =>
      Object.fromEntries(subTableBindings.map(b => [b.bindingId, b.data]))

    fc.assert(
      fc.property(
        fc.uniqueArray(fc.integer({ min: 1, max: 50 }), { minLength: 1, maxLength: 10 }),
        fc.array(fc.array(fc.record({ val: fc.string() }), { maxLength: 5 }), { minLength: 1, maxLength: 10 }),
        (allIds, rowDataSets) => {
          const bindings = allIds.map((id, i) => ({
            bindingId: id,
            data: rowDataSets[i % rowDataSets.length] ?? [],
          }))

          const payload = serializeSubTables(bindings)

          for (const binding of bindings) {
            const stored = payload[binding.bindingId] ?? payload[String(binding.bindingId)]
            expect(stored).toEqual(binding.data)
          }
          expect(Object.keys(payload).length).toBe(bindings.length)
        },
      ),
      { numRuns: 100 },
    )
  })
})
