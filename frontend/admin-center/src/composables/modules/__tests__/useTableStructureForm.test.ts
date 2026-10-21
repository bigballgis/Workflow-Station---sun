import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useTableStructureForm } from '../useTableStructureForm'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  getById: vi.fn(),
  update: vi.fn(),
  listDeployedLatest: vi.fn(),
  notifyError: vi.fn(),
}))

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/utils/notify', () => ({
  notifySuccess: vi.fn(), notifyWarning: vi.fn(), notifyError: mocks.notifyError,
}))
vi.mock('@/api/relationTable', () => ({
  relationTableStructureApi: { list: mocks.list, getById: mocks.getById, update: mocks.update },
}))
vi.mock('@/api/functionUnit', () => ({
  functionUnitApi: { listDeployedLatest: mocks.listDeployedLatest },
}))

describe('Relation Table Function Unit selection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.list.mockResolvedValue([])
    mocks.listDeployedLatest.mockResolvedValue([])
  })

  async function load(functionUnits: { id: string; code: string; name: string }[]) {
    mocks.getById.mockResolvedValue({
      id: 2, tableName: 'meeting_space', functionUnits, fieldDefinitions: [],
    })
    const state = useTableStructureForm({ tableId: ref(2), isEdit: ref(true) })
    await state.loadTableData()
    expect(mocks.notifyError).not.toHaveBeenCalled()
    expect(mocks.update).not.toHaveBeenCalled()
    return state
  }

  it('collapses version links before mapping to the latest deployed option', async () => {
    mocks.listDeployedLatest.mockResolvedValue([{ id: 'latest', code: 'workflow-a', name: 'Workflow' }])
    const state = await load([
      { id: 'v1', code: 'workflow-a', name: 'Workflow' },
      { id: 'v2', code: 'workflow-a', name: 'Workflow' },
      { id: 'latest', code: 'workflow-a', name: 'Workflow' },
    ])
    expect(state.form.functionUnitIds).toEqual(['latest'])
    expect(state.allFunctionUnitOptions.value.map(fu => fu.id)).toEqual(['latest'])
  })

  it('keeps one assigned option per code when no deployed option is available', async () => {
    const state = await load([
      { id: 'v1', code: 'workflow-a', name: 'Workflow' },
      { id: 'v2', code: 'workflow-a', name: 'Workflow' },
    ])
    expect(state.form.functionUnitIds).toHaveLength(1)
    expect(state.allFunctionUnitOptions.value).toHaveLength(1)
    expect(state.allFunctionUnitOptions.value[0].id).toBe(state.form.functionUnitIds[0])
  })

  it('preserves different Function Units with the same display name', async () => {
    const units = [
      { id: 'a', code: 'workflow-a', name: 'Same name' },
      { id: 'b', code: 'workflow-b', name: 'Same name' },
    ]
    mocks.listDeployedLatest.mockResolvedValue(units)
    const state = await load(units)
    expect(state.form.functionUnitIds).toEqual(['a', 'b'])
    expect(state.allFunctionUnitOptions.value).toHaveLength(2)
  })

  it('preserves legacy links without a code by distinct id', async () => {
    const state = await load([
      { id: 'legacy-a', code: '', name: 'Legacy' },
      { id: 'legacy-a', code: '', name: 'Legacy' },
      { id: 'legacy-b', code: '', name: 'Legacy' },
    ])
    expect(state.form.functionUnitIds).toEqual(['legacy-a', 'legacy-b'])
    expect(state.allFunctionUnitOptions.value.map(fu => fu.id)).toEqual(['legacy-a', 'legacy-b'])
  })

  it('keeps Common selected for a table without Function Unit links', async () => {
    const state = await load([])
    expect(state.form.functionUnitIds).toEqual([state.COMMON_OPTION_VALUE])
    expect(state.allFunctionUnitOptions.value).toEqual([])
  })
})
