import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const { claimTask, unclaimTask } = vi.hoisted(() => ({
  claimTask: vi.fn(),
  unclaimTask: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/api/task', () => ({
  claimTask: (...args: unknown[]) => claimTask(...args),
  unclaimTask: (...args: unknown[]) => unclaimTask(...args),
}))

import { useTaskClaimActions } from '../useTaskClaimActions'

describe('useTaskClaimActions', () => {
  beforeEach(() => {
    vi.mocked(ElMessage.success).mockReset()
    vi.mocked(ElMessage.error).mockReset()
    claimTask.mockReset()
    unclaimTask.mockReset()
  })

  it('reloads after a successful claim', async () => {
    claimTask.mockResolvedValue({})
    const reload = vi.fn().mockResolvedValue(undefined)
    const submitting = ref(false)
    const { claim } = useTaskClaimActions({ reload, submitting })

    await claim('task-1')

    expect(claimTask).toHaveBeenCalledWith('task-1')
    expect(ElMessage.success).toHaveBeenCalledWith('task.claimSuccess')
    expect(reload).toHaveBeenCalledTimes(1)
    expect(submitting.value).toBe(false)
  })

  it('toasts the body message and still reloads on 403', async () => {
    claimTask.mockRejectedValue({
      response: { status: 403, data: { message: 'Already claimed' } },
      message: 'Request failed with status code 403',
    })
    const reload = vi.fn().mockResolvedValue(undefined)
    const { claim } = useTaskClaimActions({ reload })

    await claim('task-1')

    expect(ElMessage.error).toHaveBeenCalledWith('Already claimed')
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(reload).toHaveBeenCalledTimes(1)
  })
})
