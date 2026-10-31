import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { TaskInfo } from '@/api/task'
import type { TaskDetailCtx } from '../context'
import { retainAssignmentDraft, resetTaskDetailForHydration } from '../taskDetailReloadState'
import { isAssignmentWriteBlocked } from '@/utils/assignmentWriteAccess'
import { createTaskDetailLoader } from '../useTaskDetailLoader'

const api = vi.hoisted(() => ({ getTaskDetail: vi.fn(), getProcessDetail: vi.fn() }))
vi.mock('@/api/task', async importOriginal => ({ ...await importOriginal<object>(), getTaskDetail: api.getTaskDetail }))
vi.mock('@/api/process', async importOriginal => {
  const actual = await importOriginal<typeof import('@/api/process')>()
  return { ...actual, processApi: { ...actual.processApi, getProcessDetail: api.getProcessDetail } }
})
vi.mock('element-plus', async importOriginal => ({ ...await importOriginal<object>(), ElMessage: { error: vi.fn() } }))

function fixture(): TaskDetailCtx {
  return {
    taskId: 'task', t: (key: string) => key, route: { query: {} },
    taskInfo: ref({ taskId: 'task', variables: { comment: 'server baseline' }, assignmentWriteAccess: { canWrite: true, evaluation: null } }),
    taskForm: { formReadOnly: ref(false), clearAutosaveTimer: vi.fn(),
      formData: ref({ comment: 'Unsent comment', __subTables__: { 'dw:custom_lines': [{ key: 'row-a', note: 'Unsent row' }] } }),
      getCurrentFormFieldKeys: vi.fn() },
    bpmn: { currentNodeId: ref('work'), bpmnXml: ref(''), parseBpmnXml: vi.fn() },
    loading: ref(false), formRenderReady: ref(true), fuFormSubTableFields: ref(['old-field']), detailUiPhase: ref(3),
    diagramInViewport: ref(true), nodeFormMap: ref(new Map([['work', {}]])), previousForms: ref([{ formId: 'previous' }]),
    subTableBindings: ref([{ bindingId: 42, data: [{ key: 'row-a', note: 'Unsent row' }] }]), taskError: ref(null),
    isCompletedTask: ref(false), isCompletedTaskData: vi.fn().mockReturnValue(false), hasCompletedSnapshotRoute: () => false,
    disconnectDiagramViewportObserver: vi.fn(), deferredNodeFormMapContent: { old: true }, scheduleDetailUiPhases: vi.fn(),
    loadTaskHistory: vi.fn().mockResolvedValue([]), refreshNodeFormMapFromFormData: vi.fn(),
    prefetchProcessAndTaskFormData: vi.fn().mockResolvedValue({}), loadProcessAndTaskFormData: vi.fn().mockResolvedValue(undefined),
  } as unknown as TaskDetailCtx
}

function incoming(canWrite = false): Partial<TaskInfo> {
  return { taskId: 'task', assignee: 'new-holder', variables: { comment: 'new server value' },
    assignmentWriteAccess: { canWrite, evaluation: { contractVersion: 1, tracked: true,
      exclusionAllowsWrite: canWrite, reason: canWrite ? 'CURRENT' : 'ASSIGNMENT_EXCLUDED', stamp: null } } }
}

beforeEach(() => { api.getTaskDetail.mockReset(); api.getProcessDetail.mockReset() })

describe('qualification loss and local task form', () => {
  it('retains scalar input, row data and the same mounted binding model while updating ownership metadata', () => {
    const ctx = fixture(), model = ctx.taskForm.formData.value, bindings = ctx.subTableBindings.value
    expect(retainAssignmentDraft(ctx, incoming())).toBe(true)
    expect(ctx.taskForm.formReadOnly.value).toBe(true)
    expect(ctx.taskForm.formData.value).toBe(model)
    expect(ctx.taskForm.formData.value.comment).toBe('Unsent comment')
    expect(ctx.subTableBindings.value).toBe(bindings)
    expect(ctx.taskInfo.value.assignee).toBe('new-holder')
    expect(ctx.taskInfo.value.variables?.comment).toBe('server baseline')
    expect(ctx.formRenderReady.value).toBe(true)
    expect(ctx.disconnectDiagramViewportObserver).not.toHaveBeenCalled()
    expect(ctx.taskForm.clearAutosaveTimer).toHaveBeenCalledOnce()
  })

  it('does not silently replay retained edits when eligibility later returns', () => {
    const ctx = fixture()
    expect(retainAssignmentDraft(ctx, incoming())).toBe(true)
    expect(retainAssignmentDraft(ctx, incoming(true))).toBe(true)
    expect(ctx.taskInfo.value.assignmentWriteAccess?.canWrite).toBe(true)
    expect(isAssignmentWriteBlocked(ctx.taskInfo.value)).toBe(true)
    expect(ctx.taskForm.formReadOnly.value).toBe(true)
    expect(ctx.taskForm.formData.value.comment).toBe('Unsent comment')
  })

  it('uses the pre-check editable state after editing has been paused during the request', () => {
    const ctx = fixture(); ctx.taskForm.formReadOnly.value = true
    expect(retainAssignmentDraft(ctx, incoming(), true)).toBe(true)
  })

  it('never retains another task draft or an initial/unloaded form', () => {
    const ctx = fixture()
    expect(retainAssignmentDraft(ctx, { ...incoming(), taskId: 'another-task' })).toBe(false)
    ctx.formRenderReady.value = false
    expect(retainAssignmentDraft(ctx, incoming())).toBe(false)
    expect(ctx.taskInfo.value.assignmentLocalDraftRetained).toBeUndefined()
  })

  it('keeps normal claim/MI reload reset behavior for forms that were already read-only', () => {
    const ctx = fixture(); ctx.taskForm.formReadOnly.value = true
    expect(retainAssignmentDraft(ctx, incoming())).toBe(false)
    resetTaskDetailForHydration(ctx)
    expect(ctx.formRenderReady.value).toBe(false)
    expect(ctx.previousForms.value).toEqual([])
    expect(ctx.subTableBindings.value).toEqual([])
    expect(ctx.nodeFormMap.value.size).toBe(0)
    expect(ctx.taskForm.formReadOnly.value).toBe(false)
  })

  it('the real loader checks first and never hydrates over the retained local model', async () => {
    const ctx = fixture(), model = ctx.taskForm.formData.value
    let respond!: (value: unknown) => void
    api.getTaskDetail.mockReturnValue(new Promise(resolve => { respond = resolve }))
    const loader = createTaskDetailLoader(ctx, { fallbackProcessInstanceId: computed(() => '') })
    const pending = loader.loadTaskDetail()
    expect(ctx.taskInfo.value.assignmentWriteCheckPending).toBe(true)
    expect(ctx.taskForm.formReadOnly.value).toBe(true)
    expect(ctx.loading.value).toBe(false)
    expect(ctx.formRenderReady.value).toBe(true)
    respond({ data: incoming() }); await pending
    expect(ctx.taskInfo.value.assignmentLocalDraftRetained).toBe(true)
    expect(ctx.taskInfo.value.assignmentWriteCheckPending).toBe(false)
    expect(ctx.taskForm.formData.value).toBe(model)
    expect(ctx.subTableBindings.value).toHaveLength(1)
    expect(ctx.disconnectDiagramViewportObserver).not.toHaveBeenCalled()
    expect(api.getTaskDetail).toHaveBeenCalledOnce()
    expect(api.getTaskDetail).toHaveBeenCalledWith('task')
    expect(ctx.scheduleDetailUiPhases).not.toHaveBeenCalled()
  })

  it.each([403, 404, 503])('a failed refresh (%s) keeps the mounted local draft and never hydrates process fallback values', async status => {
    const ctx = fixture(), model = ctx.taskForm.formData.value, bindings = ctx.subTableBindings.value
    api.getTaskDetail.mockRejectedValue({ response: { status }, message: 'Synthetic failed task refresh' })
    api.getProcessDetail.mockResolvedValue({ data: { id: 'process', variables: { comment: 'Fallback server value' } } })
    await createTaskDetailLoader(ctx, { fallbackProcessInstanceId: computed(() => 'process') }).loadTaskDetail()
    expect.soft(ctx.taskForm.formData.value).toBe(model)
    expect.soft(ctx.taskForm.formData.value.comment).toBe('Unsent comment')
    expect.soft(ctx.subTableBindings.value).toBe(bindings)
    expect.soft(ctx.formRenderReady.value).toBe(true)
    expect.soft(ctx.taskInfo.value.assignmentLocalDraftRetained).toBe(true)
    expect.soft(ctx.taskForm.formReadOnly.value).toBe(true)
    expect.soft(isAssignmentWriteBlocked(ctx.taskInfo.value)).toBe(true)
    expect.soft(ctx.taskError.value).toBeNull()
    expect.soft(ctx.disconnectDiagramViewportObserver).not.toHaveBeenCalled()
    expect.soft(ctx.scheduleDetailUiPhases).not.toHaveBeenCalled()
    expect.soft(api.getProcessDetail).not.toHaveBeenCalled()
  })

  it('does not manufacture a retained draft on an initial failed load', async () => {
    const ctx = fixture(); ctx.formRenderReady.value = false; ctx.taskInfo.value = {}
    api.getTaskDetail.mockRejectedValue({ response: { status: 404 } })
    await createTaskDetailLoader(ctx, { fallbackProcessInstanceId: computed(() => '') }).loadTaskDetail()
    expect(ctx.taskInfo.value.assignmentLocalDraftRetained).not.toBe(true)
    expect(ctx.formRenderReady.value).toBe(false)
    expect(ctx.taskError.value).toBe('task.notFound')
  })

  it('keeps the original failed-reload path for positively identified legacy tasks', async () => {
    const ctx = fixture()
    ctx.taskInfo.value.assignmentClaimAccess = { taskId: 'task', tracked: false, state: 'LEGACY',
      claim: { exclusionAllowsClaim: true, poolSource: null, holderSource: null }, stamp: null }
    api.getTaskDetail.mockRejectedValue({ response: { status: 404 } })
    await createTaskDetailLoader(ctx, { fallbackProcessInstanceId: computed(() => '') }).loadTaskDetail()
    expect(ctx.taskInfo.value.assignmentLocalDraftRetained).not.toBe(true)
    expect(ctx.formRenderReady.value).toBe(false)
    expect(ctx.taskError.value).toBe('task.notFound')
  })
})
