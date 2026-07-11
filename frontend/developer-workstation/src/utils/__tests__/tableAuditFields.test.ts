import { describe, it, expect } from 'vitest'
import { filterOutTableAuditFields, isTableAuditField, TABLE_AUDIT_FIELD_NAMES } from '../tableAuditFields'

describe('tableAuditFields', () => {
  it('recognizes standard audit field names case-insensitively', () => {
    for (const name of TABLE_AUDIT_FIELD_NAMES) {
      expect(isTableAuditField(name)).toBe(true)
      expect(isTableAuditField(name.toUpperCase())).toBe(true)
    }
  })

  it('rejects non-audit field names', () => {
    expect(isTableAuditField('id')).toBe(false)
    expect(isTableAuditField('name')).toBe(false)
    expect(isTableAuditField('__request_id')).toBe(false)
    expect(isTableAuditField('')).toBe(false)
    expect(isTableAuditField(undefined)).toBe(false)
  })

  it('filterOutTableAuditFields keeps business fields only', () => {
    const filtered = filterOutTableAuditFields([
      { fieldName: 'name' },
      { fieldName: 'created_at' },
      { fieldName: 'updated_by' },
    ])
    expect(filtered).toEqual([{ fieldName: 'name' }])
  })
})
