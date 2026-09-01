import { describe, expect, it } from 'vitest'
import { pickMiLinkChildRowsForParent } from '../miLinkChildRows'
import { miParentRowAlignsWithChildRow } from '../miLinkChildIdentity'
import { seedLinkChildForeignKeysFromParentRow } from '@/utils/subTableRowRuntime'

/**
 * #1441 Details mapping does not depend on any live id_idw such as Test-000060.
 * Unprocessed People: id stays empty, sub_task_id = parent participant id.
 * Completed People: allocated UUID id, sub_task_id still = parent participant id.
 */
describe('#1441 Details field mapping with generic participant ids', () => {
  const OPEN = 'MI-OPEN-1'
  const DONE = 'MI-DONE-1'
  const ALLOCATED = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'

  const peopleFields = [
    { fieldName: 'id', isPrimaryKey: true },
    { fieldName: 'sub_task_id', isForeignKey: true, refTableId: 20, refPrimaryKeyFields: ['id_idw'] },
  ]

  it('unprocessed link-child gets parent id on sub_task_id and does not copy it onto child id', () => {
    const row = seedLinkChildForeignKeysFromParentRow(
      { age: '' },
      peopleFields,
      {
        bindingForeignKeyField: 'id',
        bindingLinkMode: 'structuralFk',
        primaryKeyFields: ['id'],
        parentParticipantRow: { id_idw: OPEN },
        parentTableId: 20,
        legacyFkSeed: OPEN,
      },
    )
    expect(row.id).toBeUndefined()
    expect(row.sub_task_id).toBe(OPEN)
  })

  it('completed link-child keeps allocated UUID and still points at the parent participant', () => {
    const parent = { id_idw: DONE }
    const child = { id: ALLOCATED, sub_task_id: DONE, age: '41' }
    expect(miParentRowAlignsWithChildRow(parent, child)).toBe(true)
    const picked = pickMiLinkChildRowsForParent(parent, [child, { id: 'x', sub_task_id: OPEN }], ['id'])
    expect(picked).toHaveLength(1)
    expect(picked[0].id).toBe(ALLOCATED)
    expect(picked[0].sub_task_id).toBe(DONE)
  })
})
