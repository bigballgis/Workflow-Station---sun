import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/types/errors'
import type { FunctionUnit } from '@/api/functionUnit'
import { useFunctionUnit } from '../useFunctionUnit'
import { useFunctionUnitLists } from '../useFunctionUnitLists'

const mocks = vi.hoisted(() => ({
  notifyError: vi.fn(),
  notifySuccess: vi.fn(),
  notifyWarning: vi.fn(),
  notifyConfirm: vi.fn(),
  api: {
    deploy: vi.fn(),
    restore: vi.fn(),
    validateUnit: vi.fn(),
    getAllVersions: vi.fn(),
    getDeploymentHistory: vi.fn(),
    getDeletePreview: vi.fn(),
    previewImport: vi.fn(),
    import: vi.fn(),
    query: vi.fn(),
    queryArchived: vi.fn(),
    queryDeployments: vi.fn(),
  },
  store: {
    setEnabled: vi.fn(),
    deleteFunctionUnit: vi.fn(),
    batchSetEnabled: vi.fn(),
    batchDelete: vi.fn(),
  },
}))

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/utils/logger', () => ({ logger: { error: vi.fn(), warn: vi.fn(), info: vi.fn(), debug: vi.fn() } }))
vi.mock('@/utils/notify', () => ({
  notifyError: mocks.notifyError,
  notifySuccess: mocks.notifySuccess,
  notifyWarning: mocks.notifyWarning,
  notifyConfirm: mocks.notifyConfirm,
}))
vi.mock('@/api/functionUnit', () => ({ functionUnitApi: mocks.api }))
vi.mock('@/stores/functionUnit', () => ({ useFunctionUnitStore: () => mocks.store }))
vi.mock('@/composables/list/useAdminListGrid', () => ({
  useAdminListGrid: () => ({
    beginQuery: () => 1,
    isCurrentQuery: () => true,
    buildQuery: () => ({ page: 0, size: 20 }),
    applyPage: vi.fn(),
    resetPage: vi.fn(),
  }),
}))

/** Runs a composable inside a component, as the page does. */
function inPage<T>(use: () => T): T {
  let state!: T
  mount(defineComponent({ setup() { state = use(); return () => null } }))
  return state
}

const unit = { id: 'fu-1', code: 'CASES', name: 'Cases', enabled: true } as FunctionUnit

/** Each FU page action, made to fail with `error`, and the toast it falls back to when nobody else reported it. */
const actions: Array<[string, (error: unknown) => Promise<void>, string]> = [
  ['deploy', async (error) => {
    mocks.api.deploy.mockRejectedValue(error)
    await inPage(useFunctionUnit).handleDeploy(unit)
  }, 'functionUnit.deployFailed'],
  ['restore', async (error) => {
    mocks.api.restore.mockRejectedValue(error)
    await inPage(useFunctionUnit).handleRestore(unit)
  }, 'functionUnit.restoreFailed'],
  ['validate', async (error) => {
    mocks.api.validateUnit.mockRejectedValue(error)
    await inPage(useFunctionUnit).handleValidate(unit)
  }, 'functionUnit.validateFailed'],
  ['rollback', async (error) => {
    mocks.api.getDeploymentHistory.mockRejectedValue(error)
    await inPage(useFunctionUnit).handleRollback(unit)
  }, 'functionUnit.rollbackFailed'],
  ['versions', async (error) => {
    mocks.api.getAllVersions.mockRejectedValue(error)
    await inPage(useFunctionUnit).showVersions(unit)
  }, 'functionUnit.loadFailed'],
  ['enable / disable', async (error) => {
    mocks.store.setEnabled.mockRejectedValue(error)
    await inPage(useFunctionUnit).handleEnabledChange({ ...unit }, true)
  }, 'common.failed'],
  ['delete preview', async (error) => {
    mocks.api.getDeletePreview.mockRejectedValue(error)
    await inPage(useFunctionUnit).handleDeleteClick(unit)
  }, 'functionUnit.getDeletePreviewFailed'],
  ['archive', async (error) => {
    mocks.store.deleteFunctionUnit.mockRejectedValue(error)
    const page = inPage(useFunctionUnit)
    page.deleteTargetUnit.value = unit
    await page.handleDeleteConfirm()
  }, 'functionUnit.deleteFailed'],
  ['batch enable', async (error) => {
    mocks.store.batchSetEnabled.mockRejectedValue(error)
    await inPage(useFunctionUnit).handleBatchEnable()
  }, 'common.failed'],
  ['batch disable', async (error) => {
    mocks.store.batchSetEnabled.mockRejectedValue(error)
    await inPage(useFunctionUnit).handleBatchDisable()
  }, 'common.failed'],
  ['batch archive', async (error) => {
    mocks.store.batchDelete.mockRejectedValue(error)
    await inPage(useFunctionUnit).handleBatchDelete()
  }, 'common.failed'],
  ['import preview', async (error) => {
    mocks.api.previewImport.mockRejectedValue(error)
    await inPage(useFunctionUnit).handleImportFileChange({ raw: new File(['zip'], 'cases.zip') })
  }, 'functionUnit.importFailed'],
]

describe('Function Unit page: one toast per failure', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.notifyConfirm.mockResolvedValue(undefined)
    mocks.api.query.mockResolvedValue({ content: [], columns: [], page: 0, size: 20, totalElements: 0 })
  })

  it.each(actions)('%s: no second toast when the request layer already showed the error', async (_name, failWith) => {
    await failWith(new ApiError('INVALID_PARAMS', 400, 'Function unit status does not allow deployment: DRAFT'))
    expect(mocks.notifyError).not.toHaveBeenCalled()
  })

  it.each(actions)('%s: its own toast when the failure did not come from a request', async (_name, failWith, fallback) => {
    await failWith(new TypeError('boom'))
    expect(mocks.notifyError).toHaveBeenCalledTimes(1)
    expect(mocks.notifyError).toHaveBeenCalledWith(fallback)
  })

  // 'cancel' is the Cancel button; 'close' is the X or the Esc key
  it.each(['cancel', 'close'])('batch actions stay quiet when the confirmation is dismissed (%s)', async (action) => {
    mocks.notifyConfirm.mockRejectedValue(action)
    const page = inPage(useFunctionUnit)
    await page.handleBatchDisable()
    await page.handleBatchDelete()
    expect(mocks.notifyError).not.toHaveBeenCalled()
    expect(mocks.store.batchSetEnabled).not.toHaveBeenCalled()
    expect(mocks.store.batchDelete).not.toHaveBeenCalled()
  })
})

describe('Function Unit lists: one toast per failed load', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('adds nothing to the request layer\'s toast', async () => {
    mocks.api.query.mockRejectedValue(new ApiError('SERVER_ERROR', 500, 'Internal server error'))
    mocks.api.queryArchived.mockRejectedValue(new ApiError('NETWORK_ERROR', 0))
    mocks.api.queryDeployments.mockRejectedValue(new ApiError('NO_PERMISSION', 403, 'Permission denied'))
    const lists = inPage(useFunctionUnitLists)
    await lists.fetchFunctionUnits()
    await lists.fetchArchivedFunctionUnits()
    await lists.fetchDeployments()
    expect(mocks.notifyError).not.toHaveBeenCalled()
  })

  it('reports a failure that did not come from a request', async () => {
    mocks.api.query.mockRejectedValue(new Error('function-units/query response is missing its column declaration'))
    await inPage(useFunctionUnitLists).fetchFunctionUnits()
    expect(mocks.notifyError).toHaveBeenCalledWith('function-units/query response is missing its column declaration')
  })
})
