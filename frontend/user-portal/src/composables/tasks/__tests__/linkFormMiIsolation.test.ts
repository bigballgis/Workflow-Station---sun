import { describe, expect, it } from 'vitest'
import {
  findSubTableRowByMiExpansionId,
  findMiIsolatedParentRow,
  rowMatchesMiExpansionId,
  scrubMiCorruptLinkChildRowsForParent,
  pickMiLinkChildRowsForParent,
  miParentRowAlignsWithChildRow,
  miLinkChildRowBelongsToParticipant,
  mergeSubTableRowsByRowId,
  collapseMiLinkChildRowsToOnePerParticipant,
  scoreMiLinkChildRowQuality,
  backfillMiLinkChildPrimaryKeysFromVariables,
  repairMisassignedLinkChildStructuralFk,
  scopeMiLinkChildRowsForParentRow,
} from '../shared'

describe('linkFormMiIsolation helpers', () => {
  it('rowMatchesMiExpansionId matches id_idw', () => {
    expect(rowMatchesMiExpansionId({ id_idw: 88, name: '8' }, 88)).toBe(true)
    expect(rowMatchesMiExpansionId({ id_idw: 88 }, 44)).toBe(false)
  })

  it('findSubTableRowByMiExpansionId returns the participant row', () => {
    const rows = [
      { id: '44', id_idw: 245, assignee: { id: 'other' } },
      { name: '8', id_idw: 88, assignee: { id: 'user-e2e-sunqiang' } }
    ]
    const found = findSubTableRowByMiExpansionId(rows, 88)
    expect(found?.name).toBe('8')
    expect(found?.id).toBeUndefined()
  })

  it('findMiIsolatedParentRow falls back to sole row when rowId is id_idw but row only has SQL id', () => {
    const rows = [{ id: 6532, assignee_user_id: '44053631', task_current_node: 'sub form2' }]
    expect(findSubTableRowByMiExpansionId(rows, 44053631)).toBeNull()
    const parent = findMiIsolatedParentRow(rows, 44053631)
    expect(parent?.id).toBe(6532)
  })

  it('scrubMiCorruptLinkChildRowsForParent repairs id when row has form payload', () => {
    const subTables: Record<string, unknown> = {
      '90': [
        { id: 44, id_idw: 88, sex: 'x' },
        { id: 88, id_idw: 88, sex: 'ok' }
      ]
    }
    scrubMiCorruptLinkChildRowsForParent(subTables, 88)
    const rows = subTables['90'] as Array<Record<string, unknown>>
    expect(rows).toHaveLength(2)
    const repaired = rows.find(r => r.sex === 'x')
    expect(repaired?.id).toBe(88)
  })

  it('pickMiLinkChildRowsForParent matches child by parent id_idw not stale id FK', () => {
    const parent = { name: '8', id_idw: 88 }
    const candidates = [
      { id: 44, id_idw: 88, sex: true, age: '12' },
      { id: 555, id_idw: 245, sex: false, age: '9' }
    ]
    const picked = pickMiLinkChildRowsForParent(parent, candidates, null)
    expect(picked).toHaveLength(1)
    expect(picked[0].sex).toBe(true)
    expect(picked[0].age).toBe('12')
  })

  it('scrubMiCorruptLinkChildRowsForParent drops thin stale id rows without payload', () => {
    const subTables: Record<string, unknown> = {
      '90': [{ id: 44, id_idw: 88 }, { id: 88, id_idw: 88, age: 1 }]
    }
    scrubMiCorruptLinkChildRowsForParent(subTables, 88)
    const rows = subTables['90'] as Array<Record<string, unknown>>
    expect(rows).toHaveLength(1)
    expect(rows[0].age).toBe(1)
  })

  it('scrubMiCorruptLinkChildRowsForParent preserves allocated UUID id when id_idw mirrors parent', () => {
    const uuid = '7a0099b0-2d27-4bce-937a-7b2385c6b1cd'
    const subTables: Record<string, unknown> = {
      '30': [
        {
          id: uuid,
          id_idw: 'Test-000058',
          sub_task_id: 'Test-000058',
          age: 'ii66',
          sex: true,
        },
      ],
    }
    scrubMiCorruptLinkChildRowsForParent(subTables, 'Test-000058')
    const row = (subTables['30'] as Array<Record<string, unknown>>)[0]
    expect(row.id).toBe(uuid)
    expect(row.id_idw).toBeUndefined()
  })

  it('miParentRowAlignsWithChildRow uses sub_task_id (People own id_idw is its own PK, may collide)', () => {
    // People row belongs to participant Test-000057 (sub_task_id), even though its OWN PK id_idw=Test-000058
    // collides with another participant's id. Structural FK is authoritative.
    expect(
      miParentRowAlignsWithChildRow(
        { id_idw: 'Test-000057', id: 'Test-000057' },
        { id_idw: 'Test-000058', sub_task_id: 'Test-000057', age: 'ii', sex: true },
      ),
    ).toBe(true)
    // Must NOT attach to participant Test-000058 just because the People row's own id_idw equals it.
    expect(
      miParentRowAlignsWithChildRow(
        { id_idw: 'Test-000058', id: 'Test-000058' },
        { id_idw: 'Test-000058', sub_task_id: 'Test-000057', age: 'ii', sex: true },
      ),
    ).toBe(false)
  })

  it('miLinkChildRowBelongsToParticipant keeps own People (sub_task_id), rejects other participant', () => {
    const peopleRow = { id_idw: 'Test-000058', sub_task_id: 'Test-000057', age: 'ii', sex: true }
    // Current participant Test-000057 — its own People (sub_task_id match) must remain visible.
    expect(miLinkChildRowBelongsToParticipant(peopleRow, 'Test-000057')).toBe(true)
    // Participant Test-000058 must not inherit Test-000057's People even though id_idw collides.
    expect(miLinkChildRowBelongsToParticipant(peopleRow, 'Test-000058')).toBe(false)
  })

  it('findMiIsolatedParentRow rejects sole row for a different MI participant', () => {
    const rows = [{ id_idw: 'Test-000057', id: 'Test-000057', assignee: { id: 'user-dev' } }]
    expect(findMiIsolatedParentRow(rows, 'Test-000058')).toBeNull()
  })

  /**
   * Persist-side guard (mirrors patchFormDataSubTablesFromCurrentBindings): a participant Save must merge
   * its own (isolated) rows back with OTHER participants' rows from the full pre-isolation snapshot, so the
   * shared process-level People slice never loses another sub-task's rows.
   */
  it('save merge preserves other participants People rows (no cross-participant wipe)', () => {
    const myRowId = 'Test-000057'
    // Full snapshot slice as stored in process variables (both participants present).
    const fullSnapshotRows = [
      { id: 'p57-row', id_idw: 'Test-000060', sub_task_id: 'Test-000057', age: '20', sex: true },
      { id: 'p58-row', id_idw: 'Test-000061', sub_task_id: 'Test-000058', age: '31', sex: false },
    ]
    // Current participant's isolated + edited binding rows.
    const currentBindingRows = [
      { id: 'p57-row', id_idw: 'Test-000060', sub_task_id: 'Test-000057', age: '21', sex: true },
    ]
    const others = fullSnapshotRows.filter(
      r => !miLinkChildRowBelongsToParticipant(r, myRowId),
    )
    const persisted = mergeSubTableRowsByRowId(others, currentBindingRows, ['id'])

    // Participant 58's row survives untouched.
    const p58 = persisted.find((r: any) => r.id === 'p58-row')
    expect(p58).toBeTruthy()
    expect(p58.age).toBe('31')
    expect(p58.sub_task_id).toBe('Test-000058')
    // Participant 57's edit wins.
    const p57 = persisted.find((r: any) => r.id === 'p57-row')
    expect(p57.age).toBe('21')
    expect(persisted).toHaveLength(2)
  })

  it('collapseMiLinkChildRowsToOnePerParticipant prefers UUID id over participant-id copy', () => {
    const rows = [
      { id: 'Test-000058', sub_task_id: 'Test-000058', age: 'ii66', sex: true },
      { id: '586152b6-c284-456b-8cdd-e782436b63be', sub_task_id: 'Test-000058', age: 'ii66', sex: true },
    ]
    const collapsed = collapseMiLinkChildRowsToOnePerParticipant(rows)
    expect(collapsed).toHaveLength(1)
    expect(collapsed[0].id).toBe('586152b6-c284-456b-8cdd-e782436b63be')
    expect(scoreMiLinkChildRowQuality(rows[1] as Record<string, unknown>)).toBeGreaterThan(
      scoreMiLinkChildRowQuality(rows[0] as Record<string, unknown>),
    )
  })

  it('scopeMiLinkChildRowsForParentRow keeps only the current participant child rows', () => {
    const parent062 = { id_idw: 'Test-000062', id: 'Test-000062' }
    const slice = [
      { sub_task_id: 'Test-000061', age: '88', sex: true },
      { sub_task_id: 'Test-000062', age: '', sex: false },
    ]
    const scoped = scopeMiLinkChildRowsForParentRow(parent062, slice)
    expect(scoped).toHaveLength(1)
    expect(scoped[0].sub_task_id).toBe('Test-000062')
    expect(scoped[0].age).toBe('')
  })

  it('miLinkChildRowBelongsToParticipant prefers id_idw over stale sub_task_id without repair', () => {
    const row = {
      id: 'c6f346f5-0000-4000-8000-000000000030',
      id_idw: 'Test-000043',
      sub_task_id: 'Test-000044',
      task_current_node: 'sub form2',
    }
    expect(miLinkChildRowBelongsToParticipant(row, 'Test-000043')).toBe(true)
    expect(miLinkChildRowBelongsToParticipant(row, 'Test-000044')).toBe(false)
  })

  it('repairMisassignedLinkChildStructuralFk fixes stale sub_task_id so sub form1 People survives filter', () => {
    const row = {
      id: 'Test-000059',
      id_idw: 'Test-000059',
      sub_task_id: 'Test-000057',
      age: 'ii',
      sex: true,
      name: '33',
      task_current_node: 'sub form1',
    }
    const fixed = repairMisassignedLinkChildStructuralFk(row, 'Test-000059')
    expect(fixed.sub_task_id).toBe('Test-000059')
    expect(miLinkChildRowBelongsToParticipant(fixed, 'Test-000059')).toBe(true)
  })

  it('collapseMiLinkChildRowsToOnePerParticipant merges sub form1 fields into UUID row for same participant', () => {
    const subForm1Row = repairMisassignedLinkChildStructuralFk(
      {
        id: 'Test-000059',
        sub_task_id: 'Test-000057',
        age: 'ii',
        sex: true,
        name: '33',
        task_current_node: 'sub form1',
      },
      'Test-000059',
    )
    const subForm2Stub = {
      id: 'bc601a4c-6a89-4165-bc8a-132c184893d6',
      sub_task_id: 'Test-000059',
      age: 'rrr',
      task_current_node: 'sub form2',
    }
    const collapsed = collapseMiLinkChildRowsToOnePerParticipant([subForm1Row, subForm2Stub])
    expect(collapsed).toHaveLength(1)
    expect(collapsed[0].id).toBe('bc601a4c-6a89-4165-bc8a-132c184893d6')
    expect(collapsed[0].age).toBe('ii')
    expect(collapsed[0].name).toBe('33')
    expect(collapsed[0].sex).toBe(true)
  })

  it('pickMiLinkChildRowsForParent dedupes duplicate participant rows to allocated PK', () => {
    const parent = { id_idw: 'Test-000058', id: 'Test-000058' }
    const candidates = [
      { id: 'Test-000058', sub_task_id: 'Test-000058', age: 'ii66' },
      { id: '586152b6-c284-456b-8cdd-e782436b63be', sub_task_id: 'Test-000058', age: 'ii66' },
    ]
    const picked = pickMiLinkChildRowsForParent(parent, candidates, ['id'])
    expect(picked).toHaveLength(1)
    expect(String(picked[0].id)).toMatch(/^[0-9a-f-]{36}$/i)
  })

  it('backfillMiLinkChildPrimaryKeysFromVariables restores allocated id after repair cleared misassigned PK', () => {
    const saved = {
      30: [{ id: '586152b6-c284-456b-8cdd-e782436b63be', sub_task_id: 'Test-000058', age: 'ii66' }],
    }
    const bindings = [
      {
        bindingId: 30,
        tableName: 'People',
        foreignKeyField: 'id',
        columns: [{ field: 'id' }, { field: 'sub_task_id' }, { field: 'age' }],
        data: [{ sub_task_id: 'Test-000058', age: 'ii66' }],
      },
    ]
    backfillMiLinkChildPrimaryKeysFromVariables(bindings as any, saved, 'Test-000058')
    expect(bindings[0]!.data[0]!.id).toBe('586152b6-c284-456b-8cdd-e782436b63be')
  })

  it('backfill matches an FK-less donor via id_idw (id-churn root cause: donor identity must never be its own UUID)', () => {
    // People-style link child: PK is plain `id`, participant discriminator is id_idw; the saved
    // row carries NO structural parent FK. The old `?? sidNorm` fallback compared the donor's own
    // UUID against the parent id_idw (never equal), so hydration lost the persisted UUID and every
    // Save re-allocated a fresh PK.
    const saved = {
      30: [{ id: '586152b6-c284-456b-8cdd-e782436b63be', id_idw: 'Test-000058', age: 'ii66' }],
    }
    const bindings = [
      {
        bindingId: 30,
        tableName: 'People',
        foreignKeyField: 'id_idw',
        columns: [{ field: 'id' }, { field: 'id_idw' }, { field: 'age' }],
        data: [{ id_idw: 'Test-000058', age: 'ii66' }],
      },
    ]
    backfillMiLinkChildPrimaryKeysFromVariables(bindings as any, saved, 'Test-000058')
    expect(bindings[0]!.data[0]!.id).toBe('586152b6-c284-456b-8cdd-e782436b63be')
  })

  it('backfill never borrows an id from a foreign participant row (id_idw points elsewhere, #1444 guard)', () => {
    const saved = {
      30: [{ id: '586152b6-c284-456b-8cdd-e782436b63be', id_idw: 'Test-000099', age: 'zz' }],
    }
    const bindings = [
      {
        bindingId: 30,
        tableName: 'People',
        foreignKeyField: 'id_idw',
        columns: [{ field: 'id' }, { field: 'id_idw' }, { field: 'age' }],
        data: [{ id_idw: 'Test-000058', age: 'ii66' }],
      },
    ]
    backfillMiLinkChildPrimaryKeysFromVariables(bindings as any, saved, 'Test-000058')
    expect(bindings[0]!.data[0]!.id).toBeUndefined()
  })
})
