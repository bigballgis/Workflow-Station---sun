import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createTaskDetailSubTableSync } from '../useTaskDetailSubTableSync'

const FILE_TID = 50348
const MAIN_TID = 50100

function binding(id: number, filterField: string, rows: Array<Record<string, unknown>>) {
  return {
    bindingId: id,
    tableId: FILE_TID,
    tableName: 'file_table',
    designerTableName: 'file_table',
    bindingType: 'SUB',
    filterFkRefTableId: MAIN_TID,
    filterFkFieldName: filterField,
    data: rows,
  }
}

function syncOf(bindings: ReturnType<typeof binding>[]) {
  const list = ref(bindings)
  const formData = ref<Record<string, unknown>>({})
  const sync = createTaskDetailSubTableSync({
    subTableBindings: list,
    previousForms: ref([]),
    nodeFormMap: ref(new Map()),
    isMiSubTaskMode: ref(false),
    miFullSubTablesSnapshotRef: ref(null),
    taskForm: { formData, scheduleSubTableAutosave: () => {} },
    getSavedSubTableRows: () => [],
  } as never)
  return { list, sync }
}

describe('syncMainSubTableRows filter fan-out', () => {
  it('does not copy one filter widget onto the other bindings of a multi-filter table', () => {
    const shared = [
      { id: 'A', case_id: 'C1' },
      { id: 'B', case_id: 'C2' },
    ]
    const slice = [{ id: 'A', case_id: 'C1' }]
    const { list, sync } = syncOf([
      binding(1, 'case_id', shared),
      binding(2, 'case_id2', [...shared]),
    ])
    sync.syncMainSubTableRows(1, slice)
    expect(list.value[0].data).toEqual(slice)
    expect(list.value[1].data).toEqual(shared)
  })

  it('still shares rows when two widgets declare the same filter', () => {
    const slice = [{ id: 'A', case_id: 'C1' }]
    const { list, sync } = syncOf([
      binding(1, 'case_id', []),
      binding(2, 'case_id', []),
    ])
    sync.syncMainSubTableRows(1, slice)
    expect(list.value[0].data).toEqual(slice)
    expect(list.value[1].data).toEqual(slice)
    expect(list.value[0].data).not.toBe(list.value[1].data)
  })
})
