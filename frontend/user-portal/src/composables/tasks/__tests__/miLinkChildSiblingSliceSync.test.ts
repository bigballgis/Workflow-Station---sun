import { describe, it, expect } from 'vitest'
// #1446: MI 子任务 Save 时，编辑后的 link-form（People）行要同步进同一 relation table 的
// stale 兄弟切片（如另一节点的 binding 63），否则刷新后 hydration 读到旧值。
import { syncMiLinkChildEditedRowsIntoSiblingSlices } from '../shared'

const PEOPLE_PK = '97dc2032-7a26-4798-ac6b-451135964dc1'

const peopleBinding = {
  bindingId: 30,
  tableName: 'People',
  primaryKeyFields: ['id'],
  columns: [{ field: 'id' }, { field: 'sub_task_id' }, { field: 'sex' }, { field: 'age' }],
}

const editedRow = { id: PEOPLE_PK, sub_task_id: 'Test-000074', sex: true, age: '5577' }
const staleRow = { id: PEOPLE_PK, sub_task_id: 'Test-000074', sex: true, age: '55' }

function buildSubTables(): Record<string, any> {
  return {
    '30': [{ ...editedRow }],
    // 另一节点的 People 绑定切片 —— 提交时必须被同步成新值（复现 task 068c85ab 丢值）
    '63': [{ ...staleRow }],
    // MI collection（Sub Task）切片：禁止被 link-form 同步触碰（保持 09be69f8 / #1442 语义）
    '64': [{ id_idw: 'Test-000074' }],
    '66': [{ id_idw: 'Test-000074' }],
    'Sub Task': [{ id_idw: 'Test-000074', sub_task_id: 'Test-000074' }],
    // attachment 切片：PK 不同，必须原样保留
    '104': [{ id: 'pdf-1', file: '/api/v1/upload/files/x.pdf', main_id: 'main-1' }],
  }
}

const collectionKeys = new Set(['64', '66', '69', 'Sub Task', 'sub task', 'subtable'])

describe('syncMiLinkChildEditedRowsIntoSiblingSlices (#1446)', () => {
  it('updates the same-PK row in a stale sibling numeric slice', () => {
    const subTables = buildSubTables()
    syncMiLinkChildEditedRowsIntoSiblingSlices(subTables, peopleBinding, subTables['30'], collectionKeys)
    expect(subTables['63'][0].age).toBe('5577')
    expect(subTables['63'][0].sex).toBe(true)
    expect(subTables['63']).toHaveLength(1)
  })

  it('never appends rows into slices without a matching PK row', () => {
    const subTables = buildSubTables()
    subTables['63'] = [{ id: 'some-other-uuid', age: '1' }]
    syncMiLinkChildEditedRowsIntoSiblingSlices(subTables, peopleBinding, subTables['30'], collectionKeys)
    expect(subTables['63']).toHaveLength(1)
    expect(subTables['63'][0].age).toBe('1')
  })

  it('does not touch MI collection slices even when id_idw collides', () => {
    const subTables = buildSubTables()
    const before64 = JSON.stringify(subTables['64'])
    const beforeName = JSON.stringify(subTables['Sub Task'])
    syncMiLinkChildEditedRowsIntoSiblingSlices(subTables, peopleBinding, subTables['30'], collectionKeys)
    expect(JSON.stringify(subTables['64'])).toBe(before64)
    expect(JSON.stringify(subTables['Sub Task'])).toBe(beforeName)
  })

  it('leaves attachment slices intact (no PK match)', () => {
    const subTables = buildSubTables()
    const before = JSON.stringify(subTables['104'])
    syncMiLinkChildEditedRowsIntoSiblingSlices(subTables, peopleBinding, subTables['30'], collectionKeys)
    expect(JSON.stringify(subTables['104'])).toBe(before)
  })

  it('skips MI dashboard (collection) source bindings entirely', () => {
    const subTables = buildSubTables()
    const collectionBinding = {
      bindingId: 69,
      tableName: 'Sub Task',
      primaryKeyFields: ['id_idw'],
      columns: [{ field: 'assignee' }, { field: 'task_status' }],
    }
    const before63 = JSON.stringify(subTables['63'])
    syncMiLinkChildEditedRowsIntoSiblingSlices(
      subTables,
      collectionBinding,
      [{ id_idw: 'Test-000074', task_status: 'DONE' }],
      collectionKeys,
    )
    expect(JSON.stringify(subTables['63'])).toBe(before63)
  })

  it('participant-keyed PK (id_idw) must never drive the sync, even with empty collection key set', () => {
    // 076-node hazard: a binding whose primaryKeyFields is the participant key would otherwise
    // smear link-form payload onto every collection row matching id_idw.
    const subTables = buildSubTables()
    const participantKeyedBinding = {
      bindingId: 30,
      tableName: 'People',
      primaryKeyFields: ['id_idw'],
      columns: [{ field: 'id' }, { field: 'sub_task_id' }, { field: 'sex' }, { field: 'age' }],
    }
    const edited = [{ id_idw: 'Test-000074', id: PEOPLE_PK, age: '9999', sex: true }]
    const before = JSON.stringify(subTables)
    syncMiLinkChildEditedRowsIntoSiblingSlices(subTables, participantKeyedBinding, edited, new Set())
    expect(JSON.stringify(subTables)).toBe(before)
  })

  it('skips file-only source bindings', () => {
    const subTables = buildSubTables()
    const fileBinding = { bindingId: 104, tableName: 'Attachment', columns: [{ field: 'file' }] }
    const before = JSON.stringify(subTables)
    syncMiLinkChildEditedRowsIntoSiblingSlices(subTables, fileBinding, subTables['104'], collectionKeys)
    expect(JSON.stringify(subTables)).toBe(before)
  })
})
