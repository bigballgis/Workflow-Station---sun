import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createTaskDetailMiLinkChild } from '../useTaskDetailMiLinkChild'
import type { TaskDetailCtx } from '../context'
import { scopeLinkChildRowsToMiHostRow } from '@/composables/tasks/miLinkChildRows'
import { miChildFkConfigOfBinding, resolveMiChildStructuralParentFk } from '@/composables/tasks/miLinkChildIdentity'

type Row = Record<string, unknown>
function fixture(rows: Row[], savedRows: Row[], nestedRows: Row[] = []) {
  const child = {
    bindingId: 30, tableId: 3, tableName: 'messages', designerTableName: 'messages',
    bindingLinkMode: 'structuralFk', foreignKeyField: 'transaction_ref',
    filterFkFieldName: 'transaction_ref', filterFkRefTableId: 2,
    primaryKeyFields: ['record_key'], data: rows,
    fieldDefinitions: [
      { fieldName: 'record_key', isPrimaryKey: true },
      { fieldName: 'case_ref', isForeignKey: true, refTableId: 1 },
      { fieldName: 'transaction_ref', isForeignKey: true, refTableId: 2 },
    ],
  }
  const ctx = {
    subTableBindings: ref([
      { bindingId: 20, tableId: 2, tableName: 'transactions', bindingLinkMode: 'miParticipantRow',
        primaryKeyFields: ['transaction_key'],
        data: [{ transaction_key: 'Transaction-A', __subTables__: { 'dw:messages': nestedRows } }] },
      child,
    ]),
    previousForms: ref([]), miSubProcessScope: ref({ subTableName: 'transactions' }),
    miFullSubTablesSnapshotRef: ref({ 'dw:messages': savedRows }),
    lastBindingRelationTableMap: ref(new Map()), functionUnitIdRef: ref(null),
    currentMiRowId: ref('Transaction-A'), miCollectionPrimaryKeyFields: () => ['transaction_key'],
    miRowBelongsToCurrentParticipant: () => false,
    getSavedSubTableRows: (snapshot: Record<string, unknown>) => snapshot['dw:messages'],
  } as unknown as TaskDetailCtx
  return { child, seed: () => createTaskDetailMiLinkChild(ctx).seedMiParticipantScopedBindingForeignKeys('Transaction-A') }
}

describe('MI same-table bindings preserve saved ownership', () => {
  it('uses this binding filter when a row legitimately has both foreign keys', () => {
    const { child } = fixture([], [])
    expect(resolveMiChildStructuralParentFk(
      { case_ref: 'Case-1', transaction_ref: 'Transaction-A' }, miChildFkConfigOfBinding(child),
    )).toBe('Transaction-A')
  })
  it.each([undefined, null, ''])('does not seed a saved case row with missing child FK (%s)', async fk => {
    const saved = { record_key: 'Record-1', case_ref: 'Case-1', transaction_ref: fk }
    const { child, seed } = fixture([{ ...saved }], [saved])
    await seed()
    expect(child.data).toEqual([saved])
  })

  it('recognizes a saved row through platform UUID when its configured PK is absent', async () => {
    const saved = { platformRowUuid: 'platform-row-1', case_ref: 'Case-1' }
    const { child, seed } = fixture([{ ...saved }], [saved])
    await seed()
    expect(child.data).toEqual([saved])
  })

  it('does not claim a new case row from the canonical pool', async () => {
    const draft = { record_key: 'New-Case', case_ref: 'Case-1' }
    const { child, seed } = fixture([draft], [])
    await seed()
    expect(child.data).toEqual([draft])
  })

  it('seeds a proven local nested draft with a preallocated primary key', async () => {
    const draft = { record_key: 'New-Allocated', text: 'New local message' }
    const { child, seed } = fixture([draft], [], [draft])
    await seed()
    expect(child.data).toEqual([{ ...draft, transaction_ref: 'Transaction-A' }])
  })

  it('projects only this transaction, allowing a legal dual-owned row', () => {
    const savedCase = { record_key: 'Saved-Case', case_ref: 'Case-1' }
    const preallocated = { record_key: 'New-Allocated', transaction_ref: 'Transaction-A' }
    const dual = { ...savedCase, transaction_ref: 'Transaction-A' }
    const other = { record_key: 'Other', transaction_ref: 'Transaction-B' }
    const { child } = fixture([], [])
    expect(scopeLinkChildRowsToMiHostRow(
      { transaction_key: 'Transaction-A' }, [savedCase, preallocated, dual, other],
      miChildFkConfigOfBinding(child, 2), ['transaction_key'],
    )).toEqual([preallocated, dual])
  })
})
