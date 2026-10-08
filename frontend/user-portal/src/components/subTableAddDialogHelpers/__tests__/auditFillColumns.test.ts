import { describe, it, expect, vi } from 'vitest'

vi.mock('@/api/auth', () => ({ getUser: () => ({ username: 'e2e_lina', displayName: 'Lina' }) }))

import {
  auditFillColumns,
  applyAuditFieldDefaults,
  applyEditAuditDefaults,
  type DialogColumn,
} from '../../subTableAddDialogHelpers'

const dialogColumns: DialogColumn[] = [
  { field: 'vendor_name', label: 'Vendor' },
  { field: 'category', label: 'Category' },
]
const tableFields = ['id', 'vendor_name', 'category', 'created_at', 'created_by', 'updated_at', 'updated_by']

describe('auditFillColumns — the table design decides which audit fields a row carries', () => {
  it('adds the audit fields the table defines even when no column shows them', () => {
    const fields = auditFillColumns(dialogColumns, tableFields).map(c => c.field)
    expect(fields).toEqual(['vendor_name', 'category', 'created_at', 'created_by', 'updated_at', 'updated_by'])
  })

  it('does not duplicate an audit field that is already a column', () => {
    const columns = [...dialogColumns, { field: 'created_at', label: 'Created At' }]
    const fields = auditFillColumns(columns, tableFields).map(c => c.field)
    expect(fields.filter(f => f === 'created_at')).toHaveLength(1)
  })

  it('adds nothing for a table without audit fields, or without a known design', () => {
    expect(auditFillColumns(dialogColumns, ['id', 'vendor_name'])).toBe(dialogColumns)
    expect(auditFillColumns(dialogColumns, undefined)).toBe(dialogColumns)
  })

  it('a new row saved from a dialog without audit columns gets all four values', () => {
    const row: Record<string, unknown> = { vendor_name: 'Globex' }
    applyAuditFieldDefaults(row, auditFillColumns(dialogColumns, tableFields))
    expect(row.created_by).toBe('Lina')
    expect(row.updated_by).toBe('Lina')
    expect(row.created_at).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(row.updated_at).toBe(row.created_at)
  })

  it('an edited row refreshes only updated_*', () => {
    const row: Record<string, unknown> = { created_at: '2026-01-01 00:00:00', created_by: 'someone' }
    applyEditAuditDefaults(row, auditFillColumns(dialogColumns, tableFields))
    expect(row.created_at).toBe('2026-01-01 00:00:00')
    expect(row.created_by).toBe('someone')
    expect(row.updated_by).toBe('Lina')
    expect(row.updated_at).toMatch(/^\d{4}-/)
  })
})
