import { describe, it, expect } from 'vitest'
import {
  applyAuditFieldDefaults,
  applyEditAuditDefaults,
  buildInitialRow,
  buildRules,
} from '../subTableAddDialogHelpers'
import type { DialogColumn } from '../subTableAddDialogHelpers'

// Validates: Requirements 3.2, 4.1–4.10

describe('buildInitialRow', () => {
  it('type "number" → undefined', () => {
    const col: DialogColumn = { field: 'qty', label: 'Qty', type: 'number' }
    expect(buildInitialRow([col])).toEqual({ qty: undefined })
  })

  it('type "switch" → false', () => {
    const col: DialogColumn = { field: 'active', label: 'Active', type: 'switch' }
    expect(buildInitialRow([col])).toEqual({ active: false })
  })

  it('type "checkbox" → []', () => {
    const col: DialogColumn = { field: 'tags', label: 'Tags', type: 'checkbox' }
    const result = buildInitialRow([col])
    expect(result.tags).toEqual([])
  })

  it('type "date" → null', () => {
    const col: DialogColumn = { field: 'dob', label: 'DOB', type: 'date' }
    expect(buildInitialRow([col])).toEqual({ dob: null })
  })

  it('type "datetime" → null', () => {
    const col: DialogColumn = { field: 'ts', label: 'Timestamp', type: 'datetime' }
    expect(buildInitialRow([col])).toEqual({ ts: null })
  })

  it('type "text" → ""', () => {
    const col: DialogColumn = { field: 'name', label: 'Name', type: 'text' }
    expect(buildInitialRow([col])).toEqual({ name: '' })
  })

  it('type undefined → ""', () => {
    const col: DialogColumn = { field: 'name', label: 'Name' }
    expect(buildInitialRow([col])).toEqual({ name: '' })
  })

  it('type "textarea" → ""', () => {
    const col: DialogColumn = { field: 'desc', label: 'Desc', type: 'textarea' }
    expect(buildInitialRow([col])).toEqual({ desc: '' })
  })

  it('type "select" → ""', () => {
    const col: DialogColumn = { field: 'status', label: 'Status', type: 'select' }
    expect(buildInitialRow([col])).toEqual({ status: '' })
  })

  it('type "radio" → ""', () => {
    const col: DialogColumn = { field: 'gender', label: 'Gender', type: 'radio' }
    expect(buildInitialRow([col])).toEqual({ gender: '' })
  })

  it('type "upload" → ""', () => {
    const col: DialogColumn = { field: 'file', label: 'File', type: 'upload' }
    expect(buildInitialRow([col])).toEqual({ file: '' })
  })

  it('type "user" → ""', () => {
    const col: DialogColumn = { field: 'owner', label: 'Owner', type: 'user' }
    expect(buildInitialRow([col])).toEqual({ owner: '' })
  })

  it('type "department" → ""', () => {
    const col: DialogColumn = { field: 'dept', label: 'Dept', type: 'department' }
    expect(buildInitialRow([col])).toEqual({ dept: '' })
  })

  it('multiple columns → all fields present with correct defaults', () => {
    const columns: DialogColumn[] = [
      { field: 'name', label: 'Name', type: 'text' },
      { field: 'qty', label: 'Qty', type: 'number' },
      { field: 'active', label: 'Active', type: 'switch' },
      { field: 'tags', label: 'Tags', type: 'checkbox' },
      { field: 'dob', label: 'DOB', type: 'date' },
      { field: 'ts', label: 'TS', type: 'datetime' },
    ]
    const result = buildInitialRow(columns)
    expect(result).toEqual({
      name: '',
      qty: undefined,
      active: false,
      tags: [],
      dob: null,
      ts: null,
    })
  })
})

// Audit values must be generated at SAVE time, never when the dialog opens.
describe('audit field timing (save-time fill, no open-time prefill)', () => {
  const auditColumns: DialogColumn[] = [
    { field: 'created_at', label: 'Created At', type: 'datetime' },
    { field: 'created_by', label: 'Created By', type: 'text' },
    { field: 'updated_at', label: 'Updated At', type: 'datetime' },
    { field: 'updated_by', label: 'Updated By', type: 'text' },
    { field: 'name', label: 'Name', type: 'text' },
  ]

  it('buildInitialRow leaves audit fields at type defaults (no timestamps on dialog open)', () => {
    const row = buildInitialRow(auditColumns)
    expect(row.created_at).toBeNull()
    expect(row.created_by).toBe('')
    expect(row.updated_at).toBeNull()
    expect(row.updated_by).toBe('')
  })

  it('applyAuditFieldDefaults fills created_* and updated_* on add-save', () => {
    const row = buildInitialRow(auditColumns)
    applyAuditFieldDefaults(row, auditColumns)
    expect(row.created_at).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(row.updated_at).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(row.name).toBe('')
  })

  it('applyAuditFieldDefaults uses list-view columns when dialog omits audit fields (#canvas-dialog)', () => {
    const dialogColumns: DialogColumn[] = [
      { field: 'id', label: 'id', type: 'text' },
      { field: 'main_id', label: 'main_id', type: 'text' },
      { field: 'testinfo', label: 'testinfo', type: 'text' },
    ]
    const listColumns: DialogColumn[] = [
      ...dialogColumns,
      { field: 'created_at', label: 'Created At', type: 'datetime' },
      { field: 'updated_at', label: 'Updated At', type: 'datetime' },
    ]
    const row = buildInitialRow(dialogColumns)
    applyAuditFieldDefaults(row, listColumns)
    expect(row.created_at).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(row.updated_at).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(row.testinfo).toBe('')
  })

  it('applyEditAuditDefaults refreshes only updated_* on edit-save', () => {
    const row: Record<string, unknown> = {
      created_at: '2020-01-01 00:00:00',
      created_by: 'someone',
      updated_at: '2020-01-01 00:00:00',
      updated_by: 'someone',
    }
    applyEditAuditDefaults(row, auditColumns)
    expect(row.created_at).toBe('2020-01-01 00:00:00')
    expect(row.created_by).toBe('someone')
    expect(row.updated_at).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(row.updated_at).not.toBe('2020-01-01 00:00:00')
  })

  it('buildRules never requires audit fields (empty until save would block submit)', () => {
    const required: DialogColumn[] = auditColumns.map(c => ({ ...c, required: true }))
    const rules = buildRules(required)
    expect(rules.created_at).toBeUndefined()
    expect(rules.created_by).toBeUndefined()
    expect(rules.updated_at).toBeUndefined()
    expect(rules.updated_by).toBeUndefined()
    expect(rules.name).toBeDefined()
  })
})

describe('buildRules', () => {
  it('required: true → generates rule with required: true and correct message', () => {
    const col: DialogColumn = { field: 'name', label: 'Name', type: 'text', required: true }
    const rules = buildRules([col])
    expect(rules.name).toBeDefined()
    expect(rules.name[0].required).toBe(true)
    expect(rules.name[0].message).toBe('Name is required')
  })

  it('required: false → no rule generated', () => {
    const col: DialogColumn = { field: 'name', label: 'Name', type: 'text', required: false }
    const rules = buildRules([col])
    expect(rules.name).toBeUndefined()
  })

  it('required: undefined → no rule generated', () => {
    const col: DialogColumn = { field: 'name', label: 'Name', type: 'text' }
    const rules = buildRules([col])
    expect(rules.name).toBeUndefined()
  })

  it('type "select" → trigger "change"', () => {
    const col: DialogColumn = { field: 'status', label: 'Status', type: 'select', required: true }
    const rules = buildRules([col])
    expect(rules.status[0].trigger).toBe('change')
  })

  it('type "date" → trigger "change"', () => {
    const col: DialogColumn = { field: 'dob', label: 'DOB', type: 'date', required: true }
    const rules = buildRules([col])
    expect(rules.dob[0].trigger).toBe('change')
  })

  it('type "datetime" → trigger "change"', () => {
    const col: DialogColumn = { field: 'ts', label: 'TS', type: 'datetime', required: true }
    const rules = buildRules([col])
    expect(rules.ts[0].trigger).toBe('change')
  })

  it('type "checkbox" → trigger "change"', () => {
    const col: DialogColumn = { field: 'tags', label: 'Tags', type: 'checkbox', required: true }
    const rules = buildRules([col])
    expect(rules.tags[0].trigger).toBe('change')
  })

  it('type "text" → trigger "blur"', () => {
    const col: DialogColumn = { field: 'name', label: 'Name', type: 'text', required: true }
    const rules = buildRules([col])
    expect(rules.name[0].trigger).toBe('blur')
  })

  it('type "number" → trigger "blur"', () => {
    const col: DialogColumn = { field: 'qty', label: 'Qty', type: 'number', required: true }
    const rules = buildRules([col])
    expect(rules.qty[0].trigger).toBe('blur')
  })

  it('readonly required column — no rule generated (auto-PK / system-filled FK)', () => {
    const col: DialogColumn = { field: 'id_idw', label: 'id', type: 'text', required: true, readonly: true }
    const rules = buildRules([col])
    expect(rules.id_idw).toBeUndefined()
  })

  it('multiple columns — only required ones get rules', () => {
    const columns: DialogColumn[] = [
      { field: 'name', label: 'Name', type: 'text', required: true },
      { field: 'desc', label: 'Desc', type: 'textarea' },
      { field: 'status', label: 'Status', type: 'select', required: true },
      { field: 'qty', label: 'Qty', type: 'number', required: false },
    ]
    const rules = buildRules(columns)
    expect(Object.keys(rules)).toEqual(['name', 'status'])
    expect(rules.name[0].required).toBe(true)
    expect(rules.status[0].required).toBe(true)
  })

  it('prefers Form Design column.rules (len) over required-only synthesis', () => {
    const col: DialogColumn = {
      field: 'card_number',
      label: 'Card Number',
      type: 'text',
      required: false,
      rules: [{ len: 1, trigger: 'blur' }],
    }
    const rules = buildRules([col])
    expect(rules.card_number).toEqual([{ len: 1, trigger: 'blur' }])
  })
})
