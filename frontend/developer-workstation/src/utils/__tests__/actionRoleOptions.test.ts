import { describe, expect, it } from 'vitest'
import { mergeActionRoleOptions } from '../actionRoleOptions'

describe('mergeActionRoleOptions', () => {
  it('keeps a saved code that is missing from the catalog', () => {
    const options = mergeActionRoleOptions(
      [{ code: 'ROLE_A', name: 'Role A' }],
      ['ROLE_A', 'OLD_ROLE'],
    )
    expect(options).toEqual([
      { code: 'ROLE_A', name: 'Role A' },
      { code: 'OLD_ROLE', name: 'OLD_ROLE' },
    ])
  })
})
