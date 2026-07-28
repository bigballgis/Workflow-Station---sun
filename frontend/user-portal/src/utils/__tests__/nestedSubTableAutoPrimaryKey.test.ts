/**
 * Sub-table inside a sub-table: the grandchild row must go through the same
 * allocate-PK / seed-FK path as a top-level sub-table row.
 *
 * Regression: the nested field used to be rendered without tableId / fieldDefinitions, so
 * `finalizeSubTableRowOnSave` never ran for it. Grandchild rows were persisted with no auto
 * primary key and no parent FK; the flat `__subTables__` slice then received a server-side key
 * the nested copy never learned, and a later edit of the nested row was dropped on reload.
 */
import { describe, expect, it, vi } from 'vitest'
import { buildRowAddContext, finalizeSubTableRowOnSave } from '../subTableRowRuntime'

const PARENT_TABLE_ID = 50099
const CHILD_TABLE_ID = 50100

/** nst_package: Auto number PK + structural FK to the parent shipment row. */
const packageFields = [
  { fieldName: 'package_label' },
  {
    fieldName: 'id_idw',
    isPrimaryKey: true,
    pkGeneration: { strategy: 'autoIncrement' as const, startValue: 1 },
  },
  {
    fieldName: 'shipment_id',
    isForeignKey: true,
    refTableId: PARENT_TABLE_ID,
    refPrimaryKeyFields: ['id_idw'],
  },
]

/** nst_shipment: Auto number PK of its own — unallocated while its Add dialog is still open. */
const shipmentFields = [
  { fieldName: 'shipment_name' },
  {
    fieldName: 'id_idw',
    isPrimaryKey: true,
    pkGeneration: { strategy: 'autoIncrement' as const, startValue: 1 },
  },
]

function allocator(values: Record<number, string[]>) {
  const cursor: Record<number, number> = {}
  return vi.fn(async ({ tableId }: { tableId: number }) => {
    const pool = values[tableId] ?? []
    const i = cursor[tableId] ?? 0
    cursor[tableId] = i + 1
    return [pool[i] ?? `${tableId}-${i}`]
  })
}

describe('nested sub-table row save (sub-table in sub-table)', () => {
  it('allocates the grandchild auto PK and seeds the FK to its parent row', async () => {
    const parentRow = { shipment_name: 'SHP-A', id_idw: '7' }
    const result = await finalizeSubTableRowOnSave({
      row: { package_label: 'PKG-1' },
      fieldDefinitions: packageFields,
      rowAddContext: buildRowAddContext({}, [], parentRow, PARENT_TABLE_ID),
      tableId: CHILD_TABLE_ID,
      allocatePrimaryKeys: allocator({ [CHILD_TABLE_ID]: ['3'] }),
      parentTableId: PARENT_TABLE_ID,
    })

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.row.id_idw).toBe('3')
    expect(result.row.shipment_id).toBe('7')
    expect(result.parentRowPatch).toBeUndefined()
  })

  it('materializes the unsaved parent row PK and reports it back to the host dialog', async () => {
    // The shipment row is still being composed in its own Add dialog: no id_idw yet.
    const parentRow = { shipment_name: 'SHP-A' }
    const allocate = allocator({ [PARENT_TABLE_ID]: ['9'], [CHILD_TABLE_ID]: ['4'] })

    const result = await finalizeSubTableRowOnSave({
      row: { package_label: 'PKG-1' },
      fieldDefinitions: packageFields,
      rowAddContext: buildRowAddContext({}, [], parentRow, PARENT_TABLE_ID),
      tableId: CHILD_TABLE_ID,
      allocatePrimaryKeys: allocate,
      autoEnsurePrimaryRecord: true,
      parentTablesById: { [PARENT_TABLE_ID]: { fieldDefinitions: shipmentFields } },
      parentTableId: PARENT_TABLE_ID,
    })

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.row.id_idw).toBe('4')
    // FK points at the key the parent will actually be saved under…
    expect(result.row.shipment_id).toBe('9')
    // …which is only true because the host adopts this patch instead of allocating its own.
    expect(result.parentRowPatch).toMatchObject({ shipment_name: 'SHP-A', id_idw: '9' })
  })

  it('leaves an already-keyed parent row untouched (no second allocation)', async () => {
    const allocate = allocator({ [PARENT_TABLE_ID]: ['99'], [CHILD_TABLE_ID]: ['5'] })
    const result = await finalizeSubTableRowOnSave({
      row: { package_label: 'PKG-1' },
      fieldDefinitions: packageFields,
      rowAddContext: buildRowAddContext({}, [], { id_idw: '7' }, PARENT_TABLE_ID),
      tableId: CHILD_TABLE_ID,
      allocatePrimaryKeys: allocate,
      autoEnsurePrimaryRecord: true,
      parentTablesById: { [PARENT_TABLE_ID]: { fieldDefinitions: shipmentFields } },
      parentTableId: PARENT_TABLE_ID,
    })

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.parentRowPatch).toBeUndefined()
    expect(allocate).toHaveBeenCalledTimes(1)
    expect(allocate).toHaveBeenCalledWith(expect.objectContaining({ tableId: CHILD_TABLE_ID }))
  })
})
