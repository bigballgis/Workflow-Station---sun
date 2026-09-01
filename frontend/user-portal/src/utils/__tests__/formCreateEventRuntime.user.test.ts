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

  it('copies identity and workspace names and omits tokens and permissions', () => {
    getUserMock.mockReturnValue({
      userId: 'u-1',
      username: 'ada',
      displayName: 'Ada Lovelace',
      email: 'ada@example.com',
      roles: ['approver'],
      language: 'en',
      activeBusinessUnitId: 'bu-1',
      activeBusinessUnitName: 'Retail',
      activeRoleId: 'role-1',
      activeRoleName: 'Manager',
      permissions: ['secret.permission'],
      portalAccessMode: 'FULL',
    })
    const user = readFormEventCurrentUser()
    expect(user).toEqual({
      userId: 'u-1',
      username: 'ada',
      displayName: 'Ada Lovelace',
      email: 'ada@example.com',
      roles: ['approver'],
      language: 'en',
      activeBusinessUnitId: 'bu-1',
      activeBusinessUnitName: 'Retail',
      activeRoleId: 'role-1',
      activeRoleName: 'Manager',
    })
    expect(user).not.toHaveProperty('permissions')
    expect(user).not.toHaveProperty('portalAccessMode')
  })

  it('injects a fresh user object into $FNX and PREFIX handlers', () => {
    getUserMock.mockReturnValue({
      userId: 'u-1',
      username: 'ada',
      displayName: 'Ada Lovelace',
      email: 'ada@example.com',
      roles: [],
      language: 'en',
      activeBusinessUnitName: 'Retail',
      activeRoleName: 'Manager',
    })
    const formData: Record<string, unknown> = { requester: '', dept: '' }
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
    )

    runFormOnChangeHandler(
      '$FNX:\nvar bu = user && user.activeBusinessUnitName\napi.setValue("requester", user && user.displayName)\napi.setValue("dept", bu)',
      'select',
      '1',
      api,
    )
    expect(formData.requester).toBe('Ada Lovelace')
    expect(formData.dept).toBe('Retail')

    formData.requester = ''
    runFormOnChangeHandler(
      "[[FORM-CREATE-PREFIX-function (field, value, options){\n  api.setValue('requester', user && user.displayName)\n}-FORM-CREATE-SUFFIX]]",
      'select',
      '1',
      api,
    )
    expect(formData.requester).toBe('Ada Lovelace')
  })

  it('re-reads getUser on each event', () => {
    const formData: Record<string, unknown> = { requester: '' }
    const api = createPortalFormApi(
      () => formData,
      (patch) => Object.assign(formData, patch),
    )
    getUserMock.mockReturnValueOnce({
      userId: 'u-1',
      username: 'ada',
      displayName: 'Ada',
      email: 'a@example.com',
      roles: [],
      language: 'en',
    })
    runFormOnChangeHandler(
      '$FNX:\napi.setValue("requester", user && user.displayName)',
      'select',
      '1',
      api,
    )
    expect(formData.requester).toBe('Ada')

    getUserMock.mockReturnValueOnce({
      userId: 'u-2',
      username: 'bob',
      displayName: 'Bob',
      email: 'b@example.com',
      roles: [],
      language: 'en',
    })
    runFormOnChangeHandler(
      '$FNX:\napi.setValue("requester", user && user.displayName)',
      'select',
      '1',
      api,
    )
    expect(formData.requester).toBe('Bob')
  })
})
