import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { localDayRangeToIso } from '../dateRange'
import { useAuditStore } from '@/stores/audit'
import { useUserPortalAuditStore } from '@/stores/user-portal-audit'

/** Node reads TZ on every Date operation, so each case runs in the viewer's zone it names. */
function inTimeZone<T>(zone: string, run: () => T): T {
  const previous = process.env.TZ
  process.env.TZ = zone
  try {
    return run()
  } finally {
    if (previous === undefined) delete process.env.TZ
    else process.env.TZ = previous
  }
}

describe('localDayRangeToIso', () => {
  it('bounds the picked days in a +08:00 zone', () => {
    expect(inTimeZone('Asia/Shanghai', () => localDayRangeToIso('2026-11-01', '2026-11-06'))).toEqual({
      startTime: '2026-10-31T16:00:00.000Z',
      endTime: '2026-11-06T15:59:59.999Z',
    })
  })

  it('bounds the picked days in the viewer\'s zone, not in +08:00', () => {
    expect(inTimeZone('America/New_York', () => localDayRangeToIso('2026-11-06', '2026-11-06'))).toEqual({
      startTime: '2026-11-06T05:00:00.000Z',
      endTime: '2026-11-07T04:59:59.999Z',
    })
    expect(inTimeZone('UTC', () => localDayRangeToIso('2026-11-06', '2026-11-06'))).toEqual({
      startTime: '2026-11-06T00:00:00.000Z',
      endTime: '2026-11-06T23:59:59.999Z',
    })
  })

  it('uses each day\'s own offset when the range crosses a daylight-saving change', () => {
    // London moves from +00:00 to +01:00 on 2026-03-29
    expect(inTimeZone('Europe/London', () => localDayRangeToIso('2026-03-28', '2026-03-29'))).toEqual({
      startTime: '2026-03-28T00:00:00.000Z',
      endTime: '2026-03-29T22:59:59.999Z',
    })
  })

  it('refuses a value that is not a picked date instead of sending a made-up range', () => {
    expect(() => localDayRangeToIso('not-a-date', '2026-11-06')).toThrow(RangeError)
  })
})

describe('audit list filters send the picked days in the viewer\'s zone', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('Admin Center audit log', () => {
    const store = useAuditStore()
    store.dateRange = ['2026-11-01', '2026-11-06']
    const request = inTimeZone('America/New_York', () => store.buildQueryRequest())
    expect(request.startTime).toBe('2026-11-01T04:00:00.000Z') // still -04:00 on 1 November
    expect(request.endTime).toBe('2026-11-07T04:59:59.999Z') // -05:00 from 1 November 02:00
  })

  it('User Portal audit log', () => {
    const store = useUserPortalAuditStore()
    store.dateRange = ['2026-11-06', '2026-11-06']
    const request = inTimeZone('Asia/Shanghai', () => store.buildQueryRequest())
    expect(request.startTime).toBe('2026-11-05T16:00:00.000Z')
    expect(request.endTime).toBe('2026-11-06T15:59:59.999Z')
  })

  it('sends no time bounds when no range is picked', () => {
    const store = useAuditStore()
    store.dateRange = null
    const request = store.buildQueryRequest()
    expect(request.startTime).toBeUndefined()
    expect(request.endTime).toBeUndefined()
  })
})
