import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../request', () => ({ default: request }))
import { processApi } from '../process'

describe('process start published context transport', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('opts only New Request into the fresh start-content endpoint', () => {
    processApi.getFunctionUnitStartContent('synthetic-catalog')
    expect(request.get).toHaveBeenCalledWith('/processes/function-units/synthetic-catalog/content', { params: { forStart: true } })
    processApi.getFunctionUnitContent('synthetic-catalog', 'synthetic-task', 'synthetic-instance')
    expect(request.get).toHaveBeenLastCalledWith('/processes/function-units/synthetic-catalog/content', {
      params: { taskId: 'synthetic-task', processInstanceId: 'synthetic-instance' },
    })
  })

  it('keeps context outside business formData when submitting', () => {
    const formData = { case_key: 'SYNTHETIC' }
    const batchStartContext = { schemaVersion: 1, catalogId: 'synthetic-catalog', definitionDigest: 'a'.repeat(64) }
    processApi.startProcess('synthetic-process', { formData, batchStartContext })
    expect(request.post).toHaveBeenCalledWith('/processes/synthetic-process/start', { formData, batchStartContext })
    expect(formData).toEqual({ case_key: 'SYNTHETIC' })
  })

  it('keeps legacy API request shape unchanged without context', () => {
    processApi.startProcess('legacy-process', { formData: { case_key: 'SYNTHETIC' } })
    expect(request.post).toHaveBeenCalledWith('/processes/legacy-process/start', { formData: { case_key: 'SYNTHETIC' } })
  })
})
