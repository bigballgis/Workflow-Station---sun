import { describe, expect, it } from 'vitest'
import {
  mergeRowsFromRichestUnclaimedNumericSlice,
} from '../subTableSliceAssignment'
import { hydrateBindingsRowsFromVariablesBySharedRelationTableId } from '../subTableVariableHydration'

/**
 * Regression: an ATM sub-table binding with a real tableId must never absorb the sys_users RELATED
 * slice (tableId = -1000000001) that pools every case sub-table row, which surfaced "-" ghost rows in
 * My Request (application detail). Mirrors the tid guard already in mergeRowsFromSoleUnclaimedNumericSlice.
 */
describe('richest unclaimed slice — tableId guard', () => {
  // sys_users RELATED slice (bindingId 50528, tid -1000000001) mixes transaction + attachment + comment rows.
  const pooledRelatedSlice = [
    { row_id: 'ATM-DC-PW-TRANS-000005', card_number: '4', merchant_name: '44', case_row_id: 'ATM-DC-PW-000005', created_by: 'Dev', created_at: '13:43:08' },
    { row_id: 'Attachment-000005', file: '/api/v1/upload/files/x.jpg', case_row_id: 'ATM-DC-PW-000005', created_by: 'Dev', created_at: '13:43:17' },
    { row_id: 'Comment-000005', comment: 'ppll', case_row_id: 'ATM-DC-PW-000005', created_by: 'Dev', created_at: '13:43:23' },
  ]

  it('does not pull a tid-mismatched slice when the binding tableId is known', () => {
    const bindingTableById = new Map<number, number | null>([
      [50530, 50326], // ATM_Comment
      [50528, -1000000001], // sys_users RELATED (pooled)
    ])
    const savedSubTables: Record<string, unknown> = {
      '50528': pooledRelatedSlice,
      // no 50326-tid slice present for the empty binding
    }
    const rows = mergeRowsFromRichestUnclaimedNumericSlice(
      { bindingId: 50530 },
      savedSubTables,
      new Set<number>(),
      bindingTableById,
      50326, // selfTid known
    )
    expect(rows).toEqual([])
  })

  it('still falls back to the richest slice when the binding tableId is unknown (copied form)', () => {
    const bindingTableById = new Map<number, number | null>([
      [50528, -1000000001],
    ])
    const rows = mergeRowsFromRichestUnclaimedNumericSlice(
      { bindingId: 99999 },
      { '50528': pooledRelatedSlice },
      new Set<number>(),
      bindingTableById,
      null, // selfTid unknown
    )
    expect(rows.length).toBe(3)
  })

  it('end-to-end: empty ATM_Comment binding does not absorb pooled sys_users slice', () => {
    const bindings = [
      { bindingId: 50530, tableId: 50326, data: [] as any[], primaryKeyFields: ['row_id'] },
    ]
    const bindingTableById = new Map<number, number | null>([
      [50530, 50326],
      [50528, -1000000001],
    ])
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(
      bindings,
      { '50528': pooledRelatedSlice },
      bindingTableById,
    )
    // No tid=50326 slice exists → binding stays empty rather than absorbing the pooled RELATED rows.
    expect(bindings[0].data).toEqual([])
  })
})
