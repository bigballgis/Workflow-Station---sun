import { describe, it, expect } from 'vitest'
import { applySharedAttachmentFinalizeAndMaterialize } from '../sharedAttachmentSubTable'

/**
 * Companion to `sharedAttachmentGhostRowFilter.test.ts`, which covers the row filter that actually
 * fixed the FU 50005 ghost Attachment row. This file pins the OTHER half of the contract: the
 * materialization step must not go looking for rows to adopt when the attachment slice is
 * legitimately empty.
 *
 * `applySharedAttachmentFinalizeAndMaterialize` has a "canonical.length === 0" rescue that widens
 * the search to sibling binding ids, table-name aliases and nested slices. That widening is correct
 * for a real attachment stored under a sibling binding (last case below), but must never let a
 * link-child row (People, keyed `sub_task_id`) into an attachment grid. These cases passed before
 * the filter fix too — they are here to keep that rescue from being widened further.
 */

const ATTACHMENT_BINDING = {
  bindingId: 50548,
  tableId: 50330,
  tableName: 'attachment',
  physicalTableName: 'attachment',
  foreignKeyField: 'main_id',
  primaryKeyFields: ['id'],
  columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
  data: [] as any[],
}

/** The real People row from instance a784cdde-…: no `file`, FK to participant Test-000002. */
const PEOPLE_ROW = {
  id: 'a1701c2f-4fc4-4ae4-9a38-8cb9364610e5',
  age: '18',
  sex: true,
  sub_task_id: 'Test-000002',
}

function attachmentBinding() {
  return { ...ATTACHMENT_BINDING, data: [] as any[] }
}

describe('shared attachment materialization — an empty attachment table stays empty', () => {
  it('does not adopt a link-child (People) row nested under an MI participant', () => {
    const binding = attachmentBinding()
    const flat: Record<string, unknown> = {
      // Every attachment slice really is empty in the stored variables.
      50548: [],
      50542: [],
      attachment: [],
      // People rows live nested under the participant row, under their own binding key.
      50539: [
        {
          id_idw: 'Test-000002',
          main_id: 'Meeting-000001',
          __subTables__: {
            50547: [PEOPLE_ROW],
            People: [PEOPLE_ROW],
            people: [PEOPLE_ROW],
          },
        },
      ],
    }
    applySharedAttachmentFinalizeAndMaterialize([binding], null, {
      flattened: flat,
      bindingTableById: new Map<number, number | null>([
        [50548, 50330],
        [50542, 50330],
        [50539, 50331],
        [50547, 50333],
      ]),
    })
    expect(binding.data).toEqual([])
  })

  it('does not adopt a top-level People slice either', () => {
    const binding = attachmentBinding()
    const flat: Record<string, unknown> = {
      50548: [],
      attachment: [],
      50547: [PEOPLE_ROW],
      people: [PEOPLE_ROW],
    }
    applySharedAttachmentFinalizeAndMaterialize([binding], null, {
      flattened: flat,
      bindingTableById: new Map<number, number | null>([
        [50548, 50330],
        [50547, 50333],
      ]),
    })
    expect(binding.data).toEqual([])
  })

  it('still materializes REAL attachment rows from a sibling binding of the same table', () => {
    const binding = attachmentBinding()
    const realAttachment = { id: 'f0000000-0000-4000-8000-000000000001', main_id: 'M1', file: 'spec.pdf' }
    const flat: Record<string, unknown> = {
      50548: [],
      // Same relation table (50330), different binding — this rescue path must keep working.
      50542: [realAttachment],
    }
    applySharedAttachmentFinalizeAndMaterialize([binding], null, {
      flattened: flat,
      bindingTableById: new Map<number, number | null>([
        [50548, 50330],
        [50542, 50330],
      ]),
    })
    expect(binding.data).toEqual([realAttachment])
  })

  it('keeps a real attachment row and drops a co-located link-child row', () => {
    const binding = attachmentBinding()
    const realAttachment = { id: 'f0000000-0000-4000-8000-000000000002', main_id: 'M1', file: 'a.png' }
    const flat: Record<string, unknown> = {
      50548: [],
      50542: [realAttachment],
      50547: [PEOPLE_ROW],
    }
    applySharedAttachmentFinalizeAndMaterialize([binding], null, {
      flattened: flat,
      bindingTableById: new Map<number, number | null>([
        [50548, 50330],
        [50542, 50330],
        [50547, 50333],
      ]),
    })
    expect(binding.data).toEqual([realAttachment])
  })
})
