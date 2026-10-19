import { describe, expect, it } from 'vitest'
import { useSubTableLinkFormScope } from '../useSubTableLinkFormScope'
import type { SubTableBinding, SubTableFieldProps } from '../subTableFieldTypes'

describe('linked child rows are scoped by their declared FK', () => {
  it('keeps an owning child without a copy of the participant primary key', () => {
    const binding = { bindingId: 42, tableId: 51, tableName: 'Child', primaryKeyFields: ['entry_key'],
      bindingType: 'SUB', bindingMode: 'READONLY', tableType: 'SUB', tableDescription: '', columns: [], data: [],
      foreignKeyField: 'owner_ref', fieldDefinitions: [{ fieldName: 'owner_ref', isForeignKey: true, refTableId: 50 }],
    } as SubTableBinding
    const scope = useSubTableLinkFormScope({ tableId: 50, primaryKeyFields: ['id_idw'] } as SubTableFieldProps,
      { resolveSubTableRowPk: row => row?.id_idw as string })
    const own = { entry_key: 'child-one', owner_ref: 'participant-one', amount: 3 }
    const foreign = { entry_key: 'child-two', owner_ref: 'participant-two', amount: 9 }
    expect(scope.pickBestLinkedChildRowsForParentRow(
      { id_idw: 'participant-one', task_status: 'IN_PROGRESS' }, [own, foreign], binding)).toEqual([own])
  })

  it('uses a renamed participant PK and structural FK, not unrelated familiar column names', () => {
    const binding = { bindingId: 42, tableId: 51, tableName: 'Child', primaryKeyFields: ['entry_key'],
      bindingType: 'SUB', bindingMode: 'READONLY', tableType: 'SUB', tableDescription: '', columns: [], data: [],
      foreignKeyField: 'owner_ref', fieldDefinitions: [{ fieldName: 'owner_ref', isForeignKey: true, refTableId: 50 }],
    } as SubTableBinding
    const scope = useSubTableLinkFormScope({ tableId: 50, primaryKeyFields: ['participant_key'] } as SubTableFieldProps,
      { resolveSubTableRowPk: row => row?.participant_key as string })
    const parent = { participant_key: 'participant-one', id_idw: 'ordinary-business-value', task_status: 'IN_PROGRESS' }
    const own = { entry_key: 'child-one', owner_ref: 'participant-one', sub_task_id: 'ordinary-business-value', amount: 3 }
    const foreign = { entry_key: 'child-two', owner_ref: 'participant-two', sub_task_id: 'ordinary-business-value', amount: 9 }
    expect(scope.pickBestLinkedChildRowsForParentRow(parent, [own, foreign], binding)).toEqual([own])
    expect(scope.filterRowsByMiLinkFormParent(parent, [foreign], binding)).toEqual([])
  })

  it('uses the declared filter relationship when two structural FKs target the collection', () => {
    const binding: SubTableBinding = { bindingId: 42, tableId: 51, tableName: 'Child', primaryKeyFields: ['entry_key'],
      bindingType: 'SUB', bindingMode: 'READONLY', tableType: 'SUB', tableDescription: '', columns: [], data: [],
      filterFkFieldName: 'owner_ref', fieldDefinitions: [
        { fieldName: 'owner_ref', isForeignKey: true, refTableId: 50 },
        { fieldName: 'other_ref', isForeignKey: true, refTableId: 50 },
      ],
    }
    const scope = useSubTableLinkFormScope({ tableId: 50, primaryKeyFields: ['participant_key'] } as SubTableFieldProps,
      { resolveSubTableRowPk: row => row?.participant_key as string })
    const own = { entry_key: 'one', owner_ref: 'participant-one', other_ref: 'participant-two' }
    const foreign = { entry_key: 'two', owner_ref: 'participant-two', other_ref: 'participant-one' }
    expect(scope.filterRowsByMiLinkFormParent({ participant_key: 'participant-one', custom_state: 'IN_PROGRESS' },
      [own, foreign], binding)).toEqual([own])
    const ambiguous = { ...binding, filterFkFieldName: undefined }
    expect(scope.filterRowsByMiLinkFormParent({ participant_key: 'participant-one' },
      [own, foreign], ambiguous)).toEqual([])
  })
})
