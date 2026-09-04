import { describe, expect, it } from 'vitest'
import {
  attachAssignmentConfigsToBindings,
  fieldsHiddenByMode,
  isAssignModeSwitchable,
  isAssignmentConfigured,
  lockedAssignMode,
  resolveAssignModeFromRow,
  type AssignmentConfig,
} from '../miAssignmentConfig'

const genericBoth: AssignmentConfig = {
  allowUser: true,
  allowRole: true,
  assigneeField: 'owner_user_id',
  buField: 'department_code',
  roleField: 'approver_role',
}

describe('miAssignmentConfig', () => {
  it('uses only configured generic field names', () => {
    expect(resolveAssignModeFromRow({ approver_role: 'REVIEWER' }, genericBoth)).toBe('role')
    expect(resolveAssignModeFromRow({ owner_user_id: 'u-1' }, genericBoth)).toBe('person')
    expect([...fieldsHiddenByMode('person', genericBoth)]).toEqual([
      'approver_role',
      'department_code',
    ])
    expect([...fieldsHiddenByMode('role', genericBoth)]).toEqual(['owner_user_id'])
  })

  it('requires every enabled mode to declare its field', () => {
    expect(isAssignmentConfigured(genericBoth)).toBe(true)
    expect(isAssignModeSwitchable(genericBoth)).toBe(true)
    expect(isAssignmentConfigured({
      allowUser: true,
      allowRole: true,
      assigneeField: 'owner_user_id',
    })).toBe(false)
  })

  describe('isAssignModeSwitchable / lockedAssignMode', () => {
    const userOnly: AssignmentConfig = { allowUser: true, allowRole: false, assigneeField: 'owner_user_id' }
    const roleOnly: AssignmentConfig = { allowUser: false, allowRole: true, roleField: 'approver_role', buField: 'department_code' }

    it('is switchable only when both modes are configured', () => {
      expect(isAssignModeSwitchable(genericBoth)).toBe(true)
      expect(isAssignModeSwitchable(userOnly)).toBe(false)
      expect(isAssignModeSwitchable(roleOnly)).toBe(false)
      expect(isAssignModeSwitchable(undefined)).toBe(false)
    })

    it('locks to the single configured mode when not switchable', () => {
      expect(lockedAssignMode(userOnly)).toBe('person')
      expect(lockedAssignMode(roleOnly)).toBe('role')
    })

    it('returns undefined when switchable or unconfigured', () => {
      expect(lockedAssignMode(genericBoth)).toBeUndefined()
      expect(lockedAssignMode(undefined)).toBeUndefined()
      expect(lockedAssignMode({ allowUser: false, allowRole: false })).toBeUndefined()
    })
  })

  it('attaches config by physical table name without a fixed-field fallback', () => {
    const bindings = [{
      tableName: 'Participants',
      designerTableName: 'mi_participants',
    }]
    attachAssignmentConfigsToBindings(bindings, {
      mi_participants: genericBoth,
    })
    expect(bindings[0].assignmentConfig).toEqual(genericBoth)

    const unrelated = [{ tableName: 'Participants' }]
    attachAssignmentConfigsToBindings(unrelated, {})
    expect(unrelated[0]).not.toHaveProperty('assignmentConfig')
  })
})
