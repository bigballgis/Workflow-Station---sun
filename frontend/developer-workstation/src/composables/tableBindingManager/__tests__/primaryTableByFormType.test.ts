import { describe, expect, it } from 'vitest'
import { computed, ref } from 'vue'
import { useTableBindingForm } from '../useTableBindingForm'
import type { TableBinding, TableDefinition } from '@/api/functionUnit'

/**
 * Which tables the PRIMARY binding may target depends on the form type.
 *
 * <p>A PROCESS / TASK form renders a request, whose primary table is the MAIN one. A DETAIL form
 * renders a single row of a sub-table — MAIN rows open the request detail page instead — so its
 * primary table is a SUB table. Offering only MAIN to a DETAIL form left no valid choice, and
 * picking MAIN produced a form that no view could ever open.
 */
const tables = [
  { id: 1, tableName: 'main', tableDisplayName: 'Meeting', tableType: 'MAIN', fieldDefinitions: [] },
  { id: 2, tableName: 'subtable', tableDisplayName: 'Participants', tableType: 'SUB', fieldDefinitions: [] },
  { id: 3, tableName: 'attachment', tableDisplayName: 'Attachment', tableType: 'SUB', fieldDefinitions: [] },
  { id: 4, tableName: 'remark', tableDisplayName: 'Remark', tableType: 'ACTION', fieldDefinitions: [] },
] as unknown as TableDefinition[]

function primaryOptionsFor(formType: string) {
  const form = useTableBindingForm({
    getTables: () => tables,
    bindings: ref([] as TableBinding[]),
    restrictPrimarySubOnly: computed(() => formType === 'PROCESS' || formType === 'TASK'),
    primaryTableIsSubTable: computed(() => formType === 'DETAIL'),
    tableTypeLabel: (type: string) => type,
    t: (key: string) => key,
  })
  form.bindingForm.value.bindingType = 'PRIMARY'
  return form.filteredAvailableTables.value.map(t => t.id)
}

describe('PRIMARY binding table options by form type', () => {
  it('offers the MAIN table to a TASK form', () => {
    expect(primaryOptionsFor('TASK')).toEqual([1])
  })

  /** The reported bug: a Participants detail form could only bind Meeting, the MAIN table. */
  it('offers the SUB tables to a DETAIL form, never the MAIN one', () => {
    expect(primaryOptionsFor('DETAIL')).toEqual([2, 3])
  })

  it('leaves other form types on the MAIN table', () => {
    expect(primaryOptionsFor('ACTION')).toEqual([1])
  })
})
