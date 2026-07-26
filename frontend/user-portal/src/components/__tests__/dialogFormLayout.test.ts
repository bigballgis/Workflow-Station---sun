import { describe, it, expect } from 'vitest'
import { buildDialogLayoutGroups } from '../subTableAddDialogHelpers/dialogFormLayout'
import type { DialogColumn } from '../subTableAddDialogHelpers'
import type { FormField } from '../formRendererHelpers'

const cols = (...fields: string[]): DialogColumn[] =>
  fields.map((field) => ({ field, label: field, type: 'text' }))

describe('buildDialogLayoutGroups', () => {
  it('returns a single flat group when formFields has no cards', () => {
    const visible = cols('a', 'b')
    expect(buildDialogLayoutGroups(undefined, visible)).toEqual([
      { key: 'flat', title: null, columns: visible },
    ])
    expect(buildDialogLayoutGroups([{ key: 'a', label: 'A', type: 'text', span: 24 }], visible)).toEqual([
      { key: 'flat', title: null, columns: visible },
    ])
  })

  it('groups columns under designer cards (DW Form Preview parity)', () => {
    const visible = cols('test', 'assignee', 'bu_code', 'main_id', 'id_idw', 'name')
    const formFields: FormField[] = [
      {
        key: 'card-1',
        label: 'Title',
        type: 'card',
        span: 24,
        children: [{ key: 'test', label: 'test', type: 'text', span: 24 }],
      },
      {
        key: 'card-2',
        label: 'Title',
        type: 'card',
        span: 24,
        children: [
          { key: 'assignee', label: 'Assignee', type: 'user', span: 24 },
          { key: 'bu_code', label: 'BU', type: 'text', span: 24 },
        ],
      },
      {
        key: 'card-3',
        label: 'Title',
        type: 'card',
        span: 24,
        children: [
          { key: 'main_id', label: 'main id', type: 'text', span: 24 },
          { key: 'id_idw', label: 'Id', type: 'text', span: 24 },
          { key: 'name', label: 'Name', type: 'text', span: 24 },
        ],
      },
    ]
    const groups = buildDialogLayoutGroups(formFields, visible)
    expect(groups).toHaveLength(3)
    expect(groups.map((g) => g.title)).toEqual(['Title', 'Title', 'Title'])
    expect(groups[0].columns.map((c) => c.field)).toEqual(['test'])
    expect(groups[1].columns.map((c) => c.field)).toEqual(['assignee', 'bu_code'])
    expect(groups[2].columns.map((c) => c.field)).toEqual(['main_id', 'id_idw', 'name'])
  })

  it('appends unmapped visible columns after card groups', () => {
    const visible = cols('in_card', 'orphan')
    const formFields: FormField[] = [
      {
        key: 'c1',
        label: 'Card',
        type: 'card',
        span: 24,
        children: [{ key: 'in_card', label: 'In', type: 'text', span: 24 }],
      },
    ]
    const groups = buildDialogLayoutGroups(formFields, visible)
    expect(groups).toHaveLength(2)
    expect(groups[1]).toMatchObject({ key: 'rest', title: null })
    expect(groups[1].columns.map((c) => c.field)).toEqual(['orphan'])
  })
})
