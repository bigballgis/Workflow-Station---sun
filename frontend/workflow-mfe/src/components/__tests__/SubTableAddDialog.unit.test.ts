import { describe, it, expect } from 'vitest'
import { buildInitialRow, buildRules } from '../subTableAddDialogHelpers'
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
})
