import { describe, expect, it } from 'vitest'
import { ref, computed, nextTick } from 'vue'
import { useTableBindingForm } from './useTableBindingForm'
import type { TableBinding, TableDefinition } from '@/api/functionUnit'

/**
 * Selecting Link Mode = "MI Participant Row" must auto-fill Participant Row Field with the table PK,
 * overwriting any structural-FK value carried over. Editing an existing binding must keep its stored value.
 */
function makeHarness() {
  const tables: TableDefinition[] = [
    {
      id: 50327,
      tableName: 'ATM_Transaction',
      tableType: 'SUB',
      fieldDefinitions: [
        { fieldName: 'card_number', dataType: 'VARCHAR', isPrimaryKey: false, isForeignKey: false } as any,
        { fieldName: 'row_id', dataType: 'VARCHAR', isPrimaryKey: true, isForeignKey: false } as any,
        { fieldName: 'case_row_id', dataType: 'VARCHAR', isPrimaryKey: false, isForeignKey: true } as any,
      ],
    } as TableDefinition,
  ]
  const bindings = ref<TableBinding[]>([
    { id: 1, tableId: 50329, bindingType: 'PRIMARY' } as TableBinding,
  ])
  const form = useTableBindingForm({
    getTables: () => tables,
    bindings,
    restrictPrimarySubOnly: computed(() => false),
    tableTypeLabel: (s: string) => s,
    t: (k: string) => k,
  })
  return { form, bindings }
}

describe('useTableBindingForm — MI Participant Row auto-fills PK', () => {
  it('fills Participant Row Field with the table PK when user picks MI Participant Row', () => {
    const { form } = makeHarness()
    form.bindingForm.value.bindingType = 'SUB'
    form.bindingForm.value.tableId = 50327
    form.bindingForm.value.foreignKeyField = undefined

    form.handleBindingLinkModeChange('miParticipantRow')

    expect(form.bindingForm.value.foreignKeyField).toBe('row_id')
  })

  it('overwrites a carried-over structural FK value when switching to MI Participant Row', () => {
    const { form } = makeHarness()
    form.bindingForm.value.bindingType = 'SUB'
    form.bindingForm.value.tableId = 50327
    // e.g. structuralFk had auto-selected the FK column
    form.bindingForm.value.foreignKeyField = 'case_row_id'

    form.handleBindingLinkModeChange('miParticipantRow')

    expect(form.bindingForm.value.foreignKeyField).toBe('row_id')
  })

  it('re-derives PK on user table change while in MI Participant Row mode', () => {
    const { form } = makeHarness()
    form.bindingForm.value.bindingType = 'SUB'
    form.bindingForm.value.bindingLinkMode = 'miParticipantRow'
    form.bindingForm.value.tableId = 50327
    form.bindingForm.value.foreignKeyField = 'stale_field_from_prev_table'

    form.handleTableSelect(50327)

    expect(form.bindingForm.value.foreignKeyField).toBe('row_id')
  })

  it('does NOT overwrite the persisted value when editing an existing MI binding', async () => {
    const { form } = makeHarness()
    form.handleEdit({
      id: 9,
      tableId: 50327,
      bindingType: 'SUB',
      bindingMode: 'EDITABLE',
      bindingLinkMode: 'miParticipantRow',
      foreignKeyField: 'row_id',
      subMode: 'FULL',
    } as TableBinding)
    await nextTick()
    // No user radio/table interaction happened → stored value stays as-is.
    expect(form.bindingForm.value.foreignKeyField).toBe('row_id')
  })
})
