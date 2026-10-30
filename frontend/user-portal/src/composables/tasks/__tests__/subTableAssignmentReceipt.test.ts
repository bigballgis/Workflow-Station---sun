import { defineComponent, h, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { useTaskForm } from '../useTaskForm'
import { useSubTableAssignment } from '@/composables/subTableField/useSubTableAssignment'
import { assignSubTableRow } from '@/api/task'
import { getTaskFormData, submitTaskForm } from '@/api/processForm'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() } }))
vi.mock('@/api/task', () => ({ assignSubTableRow: vi.fn() }))
vi.mock('@/api/processForm', () => ({ submitTaskForm: vi.fn(), getTaskFormData: vi.fn() }))

function fixture(version: number | null = 1, key: string | boolean = 'Case-A') {
  const task = ref('task-1')
  const rows = ref([{ entry_code: key, owner: 'old', amount: 'unsaved', rowKey: { entry_code: key },
    ...(version === null ? {} : { _wsRowVersion: version }) }])
  const baseline = [{ entry_code: key, owner: 'old', amount: 'saved',
    ...(version === null ? {} : { _wsRowVersion: version }) }]
  let form: ReturnType<typeof useTaskForm>
  let action: ReturnType<typeof useSubTableAssignment>
  const Child = defineComponent({ setup() {
    action = useSubTableAssignment({ title: 'Renamed', columns: [], get taskId() { return task.value }, assigneeField: 'owner',
      primaryKeyFields: ['entry_code'] }, rows, vi.fn(), key => key,
    { resolveSubTableRowPk: row => row?.entry_code as string })
    return () => h('span')
  } })
  const wrapper = mount(defineComponent({ setup() {
    form = useTaskForm({ subTableBindings: ref([{ bindingId: 2, tableId: 20, tableName: 'renamed_entries',
      primaryKeyFields: ['entry_code'], data: rows.value }]), assignmentTracked: ref(true),
    isMiSubTaskMode: ref(false), isCompletedTask: ref(false), effectiveTaskId: task,
    taskFormDTO: ref({ fieldValues: { __subTables__: { 'dw:renamed_entries': baseline } } }) })
    form.formData.value = { __subTables__: { 'dw:renamed_entries': rows.value,
      'rt:directory': [{ person_key: 'P1', display: 'lookup retained' }] } }
    return () => h(Child)
  } }))
  return { rows, task, wrapper, get form() { return form! }, get action() { return action! } }
}
const receipt = (previousRowVersion: number | null = 1) => ({ success: true, rowId: 0,
  assigneeId: 'new', assigneeName: 'New Owner', previousRowVersion, rowVersion: (previousRowVersion ?? 0) + 1,
  rowKey: { entry_code: 'Case-A' }, storeKey: 'dw:renamed_entries', assigneeField: 'owner' })
function firstRow(rows: unknown): Record<string, unknown> {
  if (!Array.isArray(rows) || !rows[0] || typeof rows[0] !== 'object' || Array.isArray(rows[0])) {
    throw new Error('Expected a stored sub-table row')
  }
  return rows[0] as Record<string, unknown>
}

describe('assignment receipt and next task save', () => {
  it('advances only the assigned row baseline without replacing unsaved fields or lookup caches', async () => {
    const f = fixture()
    vi.mocked(assignSubTableRow).mockResolvedValueOnce(receipt() as any)
    expect(await f.action.performSubTableRowAssignment(0, 'new')).toBe(true)
    const next = f.form.buildCurrentTaskFormSubmitPayload().formData.__subTables__['dw:renamed_entries'][0]
    expect(next).toMatchObject({ owner: 'new', amount: 'unsaved', _wsRowVersion: 2 })
    expect(f.form.loadedSubTableBaseline.value['dw:renamed_entries']).toEqual([
      { entry_code: 'Case-A', owner: 'new', amount: 'saved', _wsRowVersion: 2 },
    ])
    expect(f.form.formData.value.__subTables__['rt:directory'][0].display).toBe('lookup retained')
    f.wrapper.unmount()
  })
  it('keeps a stale draft version when the accepted assignment followed another writer', async () => {
    const f = fixture()
    vi.mocked(assignSubTableRow).mockResolvedValueOnce(receipt(2) as any)
    expect(await f.action.performSubTableRowAssignment(0, 'new')).toBe(false)
    expect(f.rows.value[0]).toMatchObject({ owner: 'old', amount: 'unsaved', _wsRowVersion: 1 })
    expect(firstRow(f.form.loadedSubTableBaseline.value['dw:renamed_entries'])._wsRowVersion).toBe(1)
    f.wrapper.unmount()
  })
  it('sends the receipt version on the next actual autosave request', async () => {
    const f = fixture()
    vi.mocked(assignSubTableRow).mockResolvedValueOnce(receipt() as any)
    expect(await f.action.performSubTableRowAssignment(0, 'new')).toBe(true)
    vi.mocked(submitTaskForm).mockClear().mockResolvedValueOnce(undefined as any)
    vi.mocked(getTaskFormData).mockResolvedValueOnce({ fieldValues: { __subTables__: { 'dw:renamed_entries': [
      { entry_code: 'Case-A', owner: 'new', amount: 'unsaved', _wsRowVersion: 3 },
    ] } } } as any)
    f.form.scheduleSubTableAutosave()
    await vi.waitFor(() => expect(submitTaskForm).toHaveBeenCalledTimes(1))
    const submitted = vi.mocked(submitTaskForm).mock.calls[0][1].formData.__subTables__
    if (!submitted || typeof submitted !== 'object' || Array.isArray(submitted)) throw new Error('Expected sub-table payload')
    expect(firstRow((submitted as Record<string, unknown>)['dw:renamed_entries'])).toMatchObject({
      owner: 'new', amount: 'unsaved', _wsRowVersion: 2,
    })
    await f.form.syncSubTableRowVersionsBeforeSubmit()
    f.wrapper.unmount()
  })
  it('does not upgrade a newly selected task when an old assignment response arrives', async () => {
    const f = fixture()
    let finish!: (value: any) => void
    vi.mocked(assignSubTableRow).mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const assigning = f.action.performSubTableRowAssignment(0, 'new')
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
    f.task.value = 'task-2'
    finish(receipt())
    expect(await assigning).toBe(false)
    expect(f.rows.value[0]._wsRowVersion).toBe(1)
    expect(f.form.subTableSaveError.value).toBeNull()
    f.wrapper.unmount()
  })
  it('requires version metadata for a protected response', async () => {
    const f = fixture()
    vi.mocked(assignSubTableRow).mockResolvedValueOnce({ success: true, assigneeId: 'new', assigneeName: 'New' } as any)
    expect(await f.action.performSubTableRowAssignment(0, 'new')).toBe(false)
    expect(f.rows.value[0]._wsRowVersion).toBe(1)
    f.wrapper.unmount()
  })
  it.each([false, 'false'] as const)('does not confuse Boolean and text primary keys (%s)', async key => {
    const f = fixture(1, key)
    const otherType = typeof key === 'boolean' ? 'false' : false
    vi.mocked(assignSubTableRow).mockResolvedValueOnce({ ...receipt(), rowKey: { entry_code: otherType } } as any)
    expect(await f.action.performSubTableRowAssignment(0, 'new')).toBe(false)
    expect(f.rows.value[0]._wsRowVersion).toBe(1)
    f.wrapper.unmount()
  })
  it('upgrades an unversioned legacy row only when the server also observed no version', async () => {
    const f = fixture(null)
    vi.mocked(assignSubTableRow).mockResolvedValueOnce(receipt(null) as any)
    expect(await f.action.performSubTableRowAssignment(0, 'new')).toBe(true)
    expect(f.form.buildCurrentTaskFormSubmitPayload().formData.__subTables__['dw:renamed_entries'][0]._wsRowVersion).toBe(1)
    f.wrapper.unmount()
  })
  it.each([
    { rowVersion: 3 }, { previousRowVersion: undefined }, { rowKey: { entry_code: 'case-a' } },
    { storeKey: 'dw:other_store' }, { assigneeField: 'other_field' }, { assigneeId: 'other_user' },
  ])('rejects a mismatched receipt without upgrading any row: %j', async patch => {
    const f = fixture()
    vi.mocked(assignSubTableRow).mockResolvedValueOnce({ ...receipt(), ...patch } as any)
    expect(await f.action.performSubTableRowAssignment(0, 'new')).toBe(false)
    expect(f.rows.value[0]._wsRowVersion).toBe(1)
    expect(firstRow(f.form.loadedSubTableBaseline.value['dw:renamed_entries'])._wsRowVersion).toBe(1)
    expect(f.rows.value[0].amount).toBe('unsaved')
    f.wrapper.unmount()
  })
  it('does not version-upgrade a row changed locally while the assignment request was pending', async () => {
    const f = fixture()
    let finish!: (value: any) => void
    vi.mocked(assignSubTableRow).mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const assigning = f.action.performSubTableRowAssignment(0, 'new')
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
    f.rows.value[0]._wsRowVersion = 2
    finish(receipt())
    expect(await assigning).toBe(false)
    expect(firstRow(f.form.loadedSubTableBaseline.value['dw:renamed_entries'])._wsRowVersion).toBe(1)
    expect(f.form.subTableSaveError.value).toBeInstanceOf(Error)
    f.wrapper.unmount()
  })
  it('updates nested target copies but neither sibling rows nor their unsaved values', async () => {
    const f = fixture()
    const peer = { entry_code: 'Case-B', owner: 'peer', amount: 'unsaved-peer', _wsRowVersion: 7 }
    f.form.formData.value.__subTables__['dw:renamed_entries'].push(peer)
    const nested = { entry_code: 'Case-A', owner: 'old', amount: 'nested-unsaved', _wsRowVersion: 1 }
    f.form.formData.value.__subTables__['dw:host'] = [{ host_key: 'H1', __subTables__: { 'dw:renamed_entries': [nested] } }]
    vi.mocked(assignSubTableRow).mockResolvedValueOnce(receipt() as any)
    expect(await f.action.performSubTableRowAssignment(0, 'new')).toBe(true)
    expect(nested).toMatchObject({ owner: 'new', _wsRowVersion: 2, amount: 'nested-unsaved' })
    expect(peer).toEqual({ entry_code: 'Case-B', owner: 'peer', amount: 'unsaved-peer', _wsRowVersion: 7 })
    f.wrapper.unmount()
  })
  it('waits for the in-flight autosave and uses its acknowledged baseline before assigning', async () => {
    const f = fixture()
    let finishSave!: () => void
    vi.mocked(submitTaskForm).mockImplementationOnce(() => new Promise(resolve => { finishSave = () => resolve(undefined as any) }))
    vi.mocked(getTaskFormData).mockResolvedValueOnce({ fieldValues: { __subTables__: { 'dw:renamed_entries': [
      { entry_code: 'Case-A', owner: 'old', amount: 'unsaved', _wsRowVersion: 2 },
    ] } } } as any)
    vi.mocked(assignSubTableRow).mockClear().mockResolvedValueOnce(receipt(2) as any)
    f.form.scheduleSubTableAutosave()
    await vi.waitFor(() => expect(finishSave).toBeTypeOf('function'))
    const assigning = f.action.performSubTableRowAssignment(0, 'new')
    expect(assignSubTableRow).not.toHaveBeenCalled()
    finishSave()
    expect(await assigning).toBe(true)
    expect(f.form.buildCurrentTaskFormSubmitPayload().formData.__subTables__['dw:renamed_entries'][0]).toMatchObject({
      owner: 'new', amount: 'unsaved', _wsRowVersion: 3,
    })
    f.wrapper.unmount()
  })
})
