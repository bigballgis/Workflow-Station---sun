import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getUserMock } = vi.hoisted(() => ({
  getUserMock: vi.fn(() => null as Record<string, unknown> | null),
}))

vi.mock('@/api/auth', () => ({
  getUser: () => getUserMock(),
}))

import { createPortalFormApi, runFormOnChangeHandler } from '../formCreateEventRuntime'
import { readFormEventCurrentUser } from '../formCreateEventUser'

describe('formCreateEventUser', () => {
  beforeEach(() => {
    getUserMock.mockReset()
    getUserMock.mockReturnValue(null)
  })

  it('returns null when nobody is signed in', () => {
    expect(readFormEventCurrentUser()).toBeNull()
  })

  it('copies identity fields and leaves Portal workspace names empty for DW sessions', () => {
    getUserMock.mockReturnValue({
      userId: 'u-1',
      username: 'ada',
      displayName: 'Ada Lovelace',
      email: 'ada@example.com',
      roles: ['developer'],
      language: 'en',
      permissions: ['secret.permission'],
    })
    const user = readFormEventCurrentUser()
    expect(user).toEqual({
      userId: 'u-1',
      username: 'ada',
      displayName: 'Ada Lovelace',
      email: 'ada@example.com',
      roles: ['developer'],
      language: 'en',
    })
    expect(user).not.toHaveProperty('permissions')
    expect(user?.activeBusinessUnitName).toBeUndefined()
  })

  it('injects a fresh user object into $FNX handlers', () => {
    getUserMock.mockReturnValue({
      userId: 'u-1',
      username: 'ada',
      displayName: 'Ada Lovelace',
      email: 'ada@example.com',
      roles: [],
      language: 'en',
    })
    const formData: Record<string, unknown> = { requester: '' }
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
    )
    runFormOnChangeHandler(
      '$FNX:\napi.setValue("requester", user && user.displayName)',
      'select',
      '1',
      api,
    )
    expect(formData.requester).toBe('Ada Lovelace')
  })
})
