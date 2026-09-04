import { describe, it, expect } from 'vitest'
import {
  flattenNestedSubTableRowsIntoPayload,
  scrubMiCorruptLinkChildRowsForParent,
  buildMiCollectionSliceKeySet,
} from '../shared'

/**
 * #1443 — MI assignee "sub form2": Sub Task grid empty + People not carrying sub form1 age/sex.
 *
 * Root cause: the current in-progress participant's MI collection row carries BOTH its participant
 * primary key ({@code id_idw}) AND an allocated UUID {@code id} plus business payload. The link-child
 * scrub ({@code scrubMiCorruptLinkChildRowsForParent}, #1435) then mistook the collection row for a
 * corrupt People link-child and stripped {@code id_idw}. With the participant key gone, the collection
 * filter ({@code rowMatchesSubTablePrimaryKey} on {@code id_idw}) matched nothing → empty Sub Task grid,
 * and the People inline form had no parent row to scope its child → age/sex lost.
 *
 * Fix: scrub skips MI collection slices ({@link buildMiCollectionSliceKeySet}), preserving {@code id_idw}
 * there while still scrubbing genuine People link-child slices.
 */

const CURRENT = 'Test-000069'
const UUID = '1fe66960-5fff-4080-98b0-f9baee07490e'

function collectionRow() {
  return {
    id: UUID,
    age: 'jj0',
    sex: true,
    name: '55',
    id_idw: CURRENT,
    main_id: 'main-1',
    assignee: 'user-e2e-lina',
    sub_task_id: CURRENT,
    participant_id: CURRENT,
    task_status: 'IN_PROGRESS',
    task_current_node: 'sub form2',
    __subTables__: {
      // nested People (link-child) slice — thin, no id_idw
      '30': [{ id: UUID, age: 'jj0', sex: true, name: '55', sub_task_id: CURRENT }],
    },
  }
}
function peopleRow() {
  return {
    id: UUID,
    age: 'jj0',
    sex: true,
    name: '55',
    id_idw: CURRENT,
    sub_task_id: CURRENT,
    task_status: 'IN_PROGRESS',
  }
}
function buildSubTables() {
  return {
    // collection (Sub Task) slices: tableId 20 → bindings 64/66/69
    '64': [collectionRow(), { id_idw: 'Test-000070', name: '6666' }],
    '66': [collectionRow()],
    '69': [collectionRow()],
    // People link-child slices: tableId 21 → bindings 30/63
    '30': [peopleRow()],
    '63': [peopleRow()],
  } as Record<string, any[]>
}

const RT_MAP = new Map<number, number | null>([
  [64, 20], [66, 20], [69, 20], [30, 21], [63, 21],
])
const BINDINGS = [
  {
    bindingId: 69, tableName: 'Sub Task', designerTableName: 'subtable', tableId: 20,
    columns: [{ field: 'id_idw' }, { field: 'assignee' }, { field: 'task_status' }],
  },
  {
    bindingId: 30, tableName: 'People', designerTableName: 'people', tableId: 21,
    columns: [{ field: 'id' }, { field: 'sex' }, { field: 'age' }],
  },
]

const find = (slice: any[]) => slice.find(r => r?.id === UUID)

describe('#1443 — MI collection id_idw survives the link-child scrub', () => {
  it('buildMiCollectionSliceKeySet covers collection bindings/names, not People', () => {
    const skip = buildMiCollectionSliceKeySet(BINDINGS, RT_MAP, 'subtable')
    expect(skip.has('64')).toBe(true)
    expect(skip.has('66')).toBe(true)
    expect(skip.has('69')).toBe(true)
    expect(skip.has('subtable')).toBe(true)
    expect(skip.has('30')).toBe(false)
    expect(skip.has('63')).toBe(false)
  })

  it('regression: scrub without skip set strips id_idw from the collection row', () => {
    const st = buildSubTables()
    flattenNestedSubTableRowsIntoPayload(st)
    scrubMiCorruptLinkChildRowsForParent(st, CURRENT)
    expect(find(st['64'])?.id_idw).toBeUndefined()
  })

  it('fix: scrub with collection skip set keeps id_idw on Sub Task, still scrubs People', () => {
    const st = buildSubTables()
    flattenNestedSubTableRowsIntoPayload(st)
    const skip = buildMiCollectionSliceKeySet(BINDINGS, RT_MAP, 'subtable')
    scrubMiCorruptLinkChildRowsForParent(st, CURRENT, { skipSliceKeys: skip })

    // Sub Task collection rows keep the participant primary key → filter finds the current row.
    expect(find(st['64'])?.id_idw).toBe(CURRENT)
    expect(find(st['66'])?.id_idw).toBe(CURRENT)
    expect(find(st['69'])?.id_idw).toBe(CURRENT)

    // People (link-child) row still gets id_idw stripped (UUID id is its real PK; #1435).
    expect(find(st['30'])?.id_idw).toBeUndefined()
    // Business fields survive so sub form1 age/sex carry forward into sub form2.
    expect(find(st['30'])?.age).toBe('jj0')
    expect(find(st['30'])?.sex).toBe(true)
  })

  it('empty skip set behaves like the legacy 2-arg call (back-compat)', () => {
    const st = buildSubTables()
    flattenNestedSubTableRowsIntoPayload(st)
    scrubMiCorruptLinkChildRowsForParent(st, CURRENT, { skipSliceKeys: null })
    expect(find(st['30'])?.id_idw).toBeUndefined()
  })
})
