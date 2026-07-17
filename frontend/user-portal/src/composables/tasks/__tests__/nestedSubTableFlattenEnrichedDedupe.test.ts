import { describe, it, expect } from 'vitest'
import { flattenNestedSubTableRowsIntoPayload } from '../miLinkChildScrub'

/**
 * ATM Demo regression (#1483): an attachment row added ONLY inside the transaction row's nested
 * sub-table is hoisted into the flat `__subTables__["50130"]` slice on persist; backend PK
 * enrichment then adds `row_id` (and possibly an FK) to the FLAT copy only. On the next persist the
 * merge (pk=null → id/rowId/content-fingerprint keys) could not re-match the enriched flat copy with
 * its un-enriched nested origin, so the attachment doubled in the TODO's top-level table.
 */
describe('flattenNestedSubTableRowsIntoPayload — enriched flat copy dedupe', () => {
  const FILE = '/api/v1/upload/files/ae520469.json?originalName=AI+Function+Unit+Generation.json'

  function atmSubTables(flatAttachmentRows: any[]) {
    return {
      '50128': [
        {
          card_number: '1',
          transaction_number: 'ATM-DC-PW-TRANS-000001',
          __subTables__: {
            '50130': [{ file: FILE, comment: '111' }],
            'ATM Attachment & Comment': [{ file: FILE, comment: '111' }],
          },
        },
      ],
      '50130': flatAttachmentRows,
    } as Record<string, unknown>
  }

  it('merges the nested origin into its row_id-enriched flat copy instead of appending', () => {
    const st = atmSubTables([{ file: FILE, row_id: 'Attachment-000002', comment: '111' }])
    flattenNestedSubTableRowsIntoPayload(st)
    const rows = st['50130'] as any[]
    expect(rows).toHaveLength(1)
    expect(rows[0].row_id).toBe('Attachment-000002')
    expect(rows[0].file).toBe(FILE)
  })

  it('stays a single row across repeated persist cycles (draft → enrich → resubmit)', () => {
    const st = atmSubTables([{ file: FILE, comment: '111' }])
    flattenNestedSubTableRowsIntoPayload(st)
    expect(st['50130'] as any[]).toHaveLength(1)
    // Backend enriches the flat copy between persists.
    ;(st['50130'] as any[])[0].row_id = 'Attachment-000002'
    flattenNestedSubTableRowsIntoPayload(st)
    expect(st['50130'] as any[]).toHaveLength(1)
    flattenNestedSubTableRowsIntoPayload(st)
    expect(st['50130'] as any[]).toHaveLength(1)
  })

  it('keeps genuinely distinct flat rows (different upload) alongside the hoisted nested row', () => {
    const st = atmSubTables([
      { file: '/api/v1/upload/files/other.json', row_id: 'Attachment-000001', comment: '111' },
    ])
    flattenNestedSubTableRowsIntoPayload(st)
    expect(st['50130'] as any[]).toHaveLength(2)
  })

  it('absorbs the dropped nested copy\'s grandchild slices into the flat row', () => {
    const st = {
      order: [
        {
          order_no: 'O-1',
          __subTables__: {
            shipment: [
              {
                shipment_no: 'S-1',
                __subTables__: { pkg: [{ pkg_no: 'P-1' }] },
              },
            ],
          },
        },
      ],
      shipment: [{ shipment_no: 'S-1', row_id: 'SHIP-001' }],
    } as Record<string, unknown>
    flattenNestedSubTableRowsIntoPayload(st)
    const shipments = st['shipment'] as any[]
    expect(shipments).toHaveLength(1)
    expect(shipments[0].row_id).toBe('SHIP-001')
    // Grandchild rows hoisted to a top-level slice via the surviving flat copy.
    expect(st['pkg'] as any[]).toEqual([{ pkg_no: 'P-1' }])
  })

  it('still collapses thin id-keyed nested rows into the matching flat row (MI link-child shape)', () => {
    const st = {
      parent: [{ id: 7, __subTables__: { child: [{ id: 5 }] } }],
      child: [{ id: 5, name: 'x' }],
    } as Record<string, unknown>
    flattenNestedSubTableRowsIntoPayload(st)
    const rows = st['child'] as any[]
    expect(rows).toHaveLength(1)
    expect(rows[0].name).toBe('x')
  })
})
