import { ref } from 'vue'
import { describe, it, expect, vi } from 'vitest'
import type { ApplicationDetailCtx } from '../context'
const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/api/process', () => ({ processApi: { getProcessHistory: api.get } }))
import { createApplicationDetailHistory } from '../useApplicationDetailHistory'

function setup() {
  const state = { processId: 'synthetic-instance', t: (key: string) => key,
    snapshotTime: '', snapshotTaskName: '', snapshotTaskId: '', snapshotActivityId: ref(''),
    processInfo: ref({ status: 'RUNNING', startTime: '', startUserId: 'synthetic' }), historyRecords: ref([]),
    batchAssignmentHistory: ref([]), batchHistoryLoadError: ref(false) }
  return { state, history: createApplicationDetailHistory(state as unknown as ApplicationDetailCtx) }
}

describe('persistent assignment observations', () => {
  it('keeps audit records out of the BPMN timeline and restores them on reload', async () => {
    const engine = { id: 'engine', activityId: 'review', activityName: 'Review', operationType: 'APPROVE' }
    const audit = { id: 'audit', operationType: 'BATCH_ASSIGNMENT_VERIFICATION', operationTime: '2026-10-17T12:00:00',
      batchAssignmentVerification: { status: 'FAILED', issues: [{ taskId: 'child', reasonCode: 'ROLE_TASK_CANDIDATES_MISMATCH' }] } }
    api.get.mockResolvedValue({ data: [engine, audit] })
    const { state, history } = setup()
    await history.loadProcessHistory()
    expect(state.batchAssignmentHistory.value).toEqual([audit])
    expect(state.historyRecords.value).toHaveLength(1)
    expect(state.historyRecords.value[0]).toHaveProperty('nodeId', 'review')
    await history.loadProcessHistory()
    expect(state.batchAssignmentHistory.value).toEqual([audit])
  })

  it('shows a load error instead of retaining stale results or pretending no records exist', async () => {
    api.get.mockRejectedValue(new Error('Synthetic history unavailable'))
    const { state, history } = setup()
    await history.loadProcessHistory()
    expect(state.batchHistoryLoadError.value).toBe(true)
    expect(state.batchAssignmentHistory.value).toEqual([])
  })

  it('leaves ordinary history without new audit rows', async () => {
    api.get.mockResolvedValue({ data: [{ activityId: 'old-node', operationType: 'APPROVE' }] })
    const { state, history } = setup()
    await history.loadProcessHistory()
    expect(state.batchAssignmentHistory.value).toEqual([])
    expect(state.batchHistoryLoadError.value).toBe(false)
    expect(state.historyRecords.value).toHaveLength(1)
  })
  it.each(['RUNNING', 'COMPLETED'])('does not show later audit observations in a %s time-only snapshot', async status => {
    const audit = { id: 'later', operationType: 'BATCH_ASSIGNMENT_VERIFICATION', operationTime: '2026-10-17T12:00:00',
      batchAssignmentVerification: { status: 'FAILED', issues: [] } }
    api.get.mockResolvedValue({ data: [audit] })
    const { state } = setup()
    state.processInfo.value.status = status
    // The context stores immutable route options, so construct it with this historical cutoff.
    const historical = createApplicationDetailHistory({ ...state, snapshotTime: '2026-10-17T11:00:00' } as unknown as ApplicationDetailCtx)
    await historical.loadProcessHistory()
    expect(state.batchAssignmentHistory.value).toEqual([])
  })
})
