import { describe, expect, it } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import type { AuditLog } from '@/api/audit'
import { useAuditDetailLoad } from './useAuditDetailLoad'

function canceledError(): Error {
  return Object.assign(new Error('canceled'), { code: 'ERR_CANCELED', name: 'CanceledError' })
}

function log(id: string): AuditLog {
  return {
    id,
    userId: 'u1',
    username: 'alice',
    action: 'UPDATE',
    resourceType: 'USER',
    resourceId: id,
    resourceName: '',
    description: '',
    oldValue: `{"id":"${id}"}`,
    newValue: `{"id":"${id}-new"}`,
    ipAddress: '127.0.0.1',
    userAgent: '',
    requestMethod: 'PUT',
    requestPath: '/users',
    requestParams: {},
    responseStatus: 200,
    result: 'SUCCESS',
    duration: 1,
    createdAt: '2026-09-01T00:00:00Z',
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function mountDetail(fetchDetail: (id: string, signal: AbortSignal) => Promise<AuditLog>) {
  return mount(defineComponent({
    setup() {
      return useAuditDetailLoad(fetchDetail)
    },
    template: '<div />',
  }))
}

describe('useAuditDetailLoad', () => {
  it('ignores a slower first GET after a second row is opened', async () => {
    const first = deferred<AuditLog>()
    const second = deferred<AuditLog>()
    const fetchDetail = (id: string) => (id === 'a' ? first.promise : second.promise)
    const wrapper = mountDetail(fetchDetail)

    const shownA = wrapper.vm.showDetailById('a')
    const shownB = wrapper.vm.showDetailById('b')
    first.resolve(log('a'))
    second.resolve(log('b'))
    await Promise.all([shownA, shownB])

    expect(wrapper.vm.currentLog?.id).toBe('b')
    expect(wrapper.vm.currentLog?.oldValue).toBe('{"id":"b"}')
    expect(wrapper.vm.detailDialogVisible).toBe(true)
    wrapper.unmount()
  })

  it('does not close the dialog when the in-flight GET is aborted', async () => {
    const first = deferred<AuditLog>()
    const second = deferred<AuditLog>()
    const fetchDetail = (id: string, signal: AbortSignal) => {
      if (id !== 'a') {
        return second.promise
      }
      return new Promise<AuditLog>((resolve, reject) => {
        signal.addEventListener('abort', () => reject(canceledError()))
        first.promise.then(resolve, reject)
      })
    }
    const wrapper = mountDetail(fetchDetail)

    const shownA = wrapper.vm.showDetailById('a')
    const shownB = wrapper.vm.showDetailById('b')
    await shownA
    expect(wrapper.vm.detailDialogVisible).toBe(true)
    expect(wrapper.vm.currentLog).toBeNull()

    second.resolve(log('b'))
    await shownB
    expect(wrapper.vm.detailDialogVisible).toBe(true)
    expect(wrapper.vm.currentLog?.id).toBe('b')
    wrapper.unmount()
  })

  it('closes the dialog on a non-abort failure', async () => {
    const wrapper = mountDetail(() => Promise.reject(new Error('not found')))
    await wrapper.vm.showDetailById('missing')
    expect(wrapper.vm.detailDialogVisible).toBe(false)
    expect(wrapper.vm.currentLog).toBeNull()
    wrapper.unmount()
  })
})
