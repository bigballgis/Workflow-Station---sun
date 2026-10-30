import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import TaskClaimBanner from '@/components/tasks/TaskClaimBanner.vue'
import type { AssignmentClaimAccess } from '@/types/assignmentClaimAccess'
import type { AssignmentWriteAccess } from '@/types/assignmentWriteAccess'
import { actionAvailability } from '@/api/assignmentRuntimeActions'

const router = vi.hoisted(() => ({ go: vi.fn() }))
vi.mock('@/api/assignmentRuntimeActions', () => ({ actionAvailability: vi.fn(), handoffTask: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => router }))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, named?: Record<string, unknown>) =>
      named ? `${key}:${Object.values(named).join(',')}` : key,
  }),
}))

type BannerTask = {
  taskId?: string
  claimPoolTask?: boolean
  claimable?: boolean
  claimedByCurrentUser?: boolean
  canForceUnclaim?: boolean
  canReassign?: boolean
  assignee?: string
  assigneeName?: string
  assignmentClaimAccess?: AssignmentClaimAccess
  assignmentWriteAccess?: AssignmentWriteAccess
  assignmentLocalDraftRetained?: boolean
}

let wrapper: VueWrapper | null = null

function mountBanner(task: BannerTask) {
  wrapper = mount(TaskClaimBanner, {
    props: { task, submitting: false },
    global: { plugins: [ElementPlus] },
  })
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  vi.restoreAllMocks()
  router.go.mockReset()
})

describe('TaskClaimBanner', () => {
  it('offers an authorized handoff on a fixed-source task even when it needs no claim banner', async () => {
    vi.mocked(actionAvailability).mockResolvedValueOnce({ contractVersion: 1, resourceId: 'fixed', allowed: true,
      targets: [{ id: 'recipient', label: 'Recipient' }] })
    const w=mountBanner({ taskId: 'fixed', claimPoolTask: false, assignmentWriteAccess: {
      canWrite: true, evaluation: { contractVersion: 1, tracked: true, exclusionAllowsWrite: true, reason: 'CURRENT', stamp: null },
    } })
    await flushPromises()
    expect(w.find('[data-test="task-claim-banner"]').exists()).toBe(false)
    expect(w.find('[data-test="assignment-handoff"]').exists()).toBe(true)
  })
  it('reloads the server form only after explicitly confirming the retained local form will be discarded', async () => {
    // Element Plus types combine input/action results; confirm itself resolves an action string.
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>)
    const w = mountBanner({ assignmentLocalDraftRetained: true, assignmentWriteAccess: { canWrite: true, evaluation: null } })
    expect(w.text()).toContain('task.assignmentDraftRetained')
    await w.get('[data-test="assignment-reload-server-form"]').trigger('click')
    expect(ElMessageBox.confirm).toHaveBeenCalledOnce()
    expect(router.go).toHaveBeenCalledWith(0)
  })

  it('keeps retained input when the discard confirmation is cancelled', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
    const w = mountBanner({ assignmentLocalDraftRetained: true, assignmentWriteAccess: { canWrite: true, evaluation: null } })
    await w.get('[data-test="assignment-reload-server-form"]').trigger('click')
    expect(router.go).not.toHaveBeenCalled()
    expect(w.text()).toContain('task.assignmentDraftRetained')
  })
  it.each(['ASSIGNMENT_EXCLUDED', 'ASSIGNMENT_STATE_CHANGED', 'ASSIGNMENT_SYNC_PENDING',
    'ASSIGNMENT_DEPENDENCY_UNAVAILABLE', 'CLAIM_REQUIRED', 'CURRENT'])('shows a write restriction on non-pool tasks: %s', (reason) => {
    const w = mountBanner({ claimPoolTask: false, assignee: 'actor', assignmentWriteAccess: {
      canWrite: false, evaluation: { contractVersion: 1, tracked: true, exclusionAllowsWrite: false, reason, stamp: null },
    } })
    expect(w.find('[data-test="task-claim-banner"]').exists()).toBe(true)
    expect(w.text()).toContain('task.assignmentWrite')
    expect(w.find('[data-test="task-claim-btn"]').exists()).toBe(false)
  })

  it('does not infer a held task is read-only from claimable=false', () => {
    const w = mountBanner({ claimPoolTask: false, assignee: 'actor', claimable: false,
      assignmentWriteAccess: { canWrite: true, evaluation: null } })
    expect(w.find('[data-test="task-claim-banner"]').exists()).toBe(false)
  })
  it.each([
    ['RECONCILING', 'claimRecalculating'], ['SYNC_PENDING', 'claimSyncPending'],
    ['BLOCKED_NO_ELIGIBLE_USER', 'claimNoEligibleUser'], ['ACTOR_INACTIVE', 'claimActorInactive'],
    ['NOT_ACTIVE', 'claimTaskChanged'], ['CONTEXT_UNAVAILABLE', 'claimQualificationUnavailable'],
    ['DEPENDENCY_UNAVAILABLE', 'claimQualificationUnavailable'], ['CURRENT', 'claimCurrentlyIneligible'],
  ] as const)('shows %s without inventing a holder or offering Claim', (state, message) => {
    const w = mountBanner({ claimPoolTask: true, claimable: false, assignmentClaimAccess: access(state) })
    expect(w.find('[data-test="task-claim-banner-title"]').text()).toBe(`task.${message}`)
    expect(w.text()).toContain(`task.${message}Hint`)
    expect(w.find('[data-test="task-claim-btn"]').exists()).toBe(false)
    expect(w.text()).not.toContain('task.claimHeldByOther')
  })

  it('keeps a held task release action when claim information is unavailable', () => {
    const w = mountBanner({ claimPoolTask: true, assignee: 'me', claimedByCurrentUser: true,
      assignmentClaimAccess: access('DEPENDENCY_UNAVAILABLE') })
    expect(w.find('[data-test="task-unclaim-btn"]').exists()).toBe(true)
    expect(w.text()).toContain('task.assignmentWriteUnavailable')
  })

  it('keeps legacy display and authorized management actions', () => {
    const w = mountBanner({ claimPoolTask: true, assignee: 'alice', canForceUnclaim: true, canReassign: true,
      assignmentClaimAccess: { ...access('LEGACY'), tracked: false } })
    expect(w.text()).toContain('task.claimHeldByOther:alice')
    expect(w.find('[data-test="task-force-unclaim-btn"]').exists()).toBe(true)
    expect(w.find('[data-test="task-reassign-btn"]').exists()).toBe(true)
  })
  it('stays out of the way for tasks that are not BU Role pool rows', () => {
    const w = mountBanner({ claimPoolTask: false, assignee: 'alice' })
    expect(w.find('[data-test="task-claim-banner"]').exists()).toBe(false)
  })

  it('offers Claim while the request is still free', () => {
    const w = mountBanner({ claimPoolTask: true, claimable: true })

    expect(w.find('[data-test="task-claim-banner-title"]').text()).toBe('task.claimAvailable')
    expect(w.find('[data-test="task-claim-btn"]').exists()).toBe(true)
    expect(w.find('[data-test="task-unclaim-btn"]').exists()).toBe(false)
  })

  it('offers Unclaim to the member holding the request', () => {
    const w = mountBanner({ claimPoolTask: true, claimedByCurrentUser: true, assignee: 'me' })

    expect(w.find('[data-test="task-claim-banner-title"]').text()).toBe('task.claimHeldByYou')
    expect(w.find('[data-test="task-unclaim-btn"]').exists()).toBe(true)
    expect(w.find('[data-test="task-claim-btn"]').exists()).toBe(false)
  })

  it('names the holder and offers no action to the rest of the role', () => {
    const w = mountBanner({ claimPoolTask: true, assignee: 'alice', assigneeName: 'Alice Chen' })

    expect(w.find('[data-test="task-claim-banner-title"]').text()).toBe(
      'task.claimHeldByOther:Alice Chen',
    )
    expect(w.find('[data-test="task-claim-btn"]').exists()).toBe(false)
    expect(w.find('[data-test="task-unclaim-btn"]').exists()).toBe(false)
  })

  it('offers Force Unclaim when the current user may release someone else hold', () => {
    const w = mountBanner({
      claimPoolTask: true,
      assignee: 'alice',
      assigneeName: 'Alice Chen',
      canForceUnclaim: true,
    })

    expect(w.find('[data-test="task-force-unclaim-btn"]').exists()).toBe(true)
    expect(w.find('[data-test="task-claim-banner"]').text()).toContain('task.forceUnclaimHint')
  })

  it('emits claim and unclaim so the page owns the API call', async () => {
    const free = mountBanner({ claimPoolTask: true, claimable: true })
    await free.find('[data-test="task-claim-btn"]').trigger('click')
    expect(free.emitted('claim')).toHaveLength(1)
    free.unmount()

    const mine = mountBanner({ claimPoolTask: true, claimedByCurrentUser: true })
    await mine.find('[data-test="task-unclaim-btn"]').trigger('click')
    expect(mine.emitted('unclaim')).toHaveLength(1)
  })

  it('offers Reassign next to Claim when the current user may assign the pool row', () => {
    const w = mountBanner({
      claimPoolTask: true,
      claimable: true,
      canReassign: true,
    })

    expect(w.find('[data-test="task-claim-btn"]').exists()).toBe(true)
    expect(w.find('[data-test="task-reassign-btn"]').exists()).toBe(true)
  })

  it('emits reassign from the Reassign button', async () => {
    const w = mountBanner({
      claimPoolTask: true,
      assignee: 'alice',
      canForceUnclaim: true,
      canReassign: true,
    })
    await w.find('[data-test="task-reassign-btn"]').trigger('click')
    expect(w.emitted('reassign')).toHaveLength(1)
  })
})

function access(state: AssignmentClaimAccess['state']): AssignmentClaimAccess {
  return { taskId: 'task', tracked: true, state,
    claim: { exclusionAllowsClaim: false, poolSource: 'RESOLVER', holderSource: null }, stamp: null }
}
