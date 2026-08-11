import { describe, it, expect } from 'vitest'
import {
  buildDialogLayoutGroups,
  groupAssignmentFieldsUnderMarker,
  type DialogLayoutItem,
} from '../subTableAddDialogHelpers/dialogFormLayout'
import type { DialogColumn } from '../subTableAddDialogHelpers'
import type { FormField } from '../formRendererHelpers'

const cols = (...fields: string[]): DialogColumn[] =>
  fields.map((field) => ({ field, label: field, type: 'text' }))

describe('buildDialogLayoutGroups', () => {
  it('falls back to every visible column when the design references none', () => {
    const visible = cols('a', 'b')
    expect(buildDialogLayoutGroups(undefined, visible)).toEqual([
      {
        key: 'flat',
        title: null,
        items: visible.map(column => ({ type: 'column', key: column.field, column })),
      },
    ])
  })

  /**
   * Designed columns are the only truth (subtable-columns-dw-parity). A sub-table's
   * physical columns can outnumber the fields its author placed — FU 50005's
   * Participants has an `assignee` column the sub-form design never references —
   * and appending the remainder rendered fields DW Form Preview does not show.
   */
  it('does not append undesigned columns when the design references any', () => {
    const visible = cols('a', 'b')
    expect(buildDialogLayoutGroups([{ key: 'a', label: 'A', type: 'text', span: 24 }], visible)).toEqual([
      {
        key: 'flat',
        title: null,
        items: [{ type: 'column', key: 'a', column: visible[0] }],
      },
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
    expect(groups[0].items.map(item => item.key)).toEqual(['test'])
    expect(groups[1].items.map(item => item.key)).toEqual(['assignee', 'bu_code'])
    expect(groups[2].items.map(item => item.key)).toEqual(['main_id', 'id_idw', 'name'])
  })

  it('keeps undesigned columns out of card layouts too', () => {
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
    expect(groups).toHaveLength(1)
    expect(groups[0]).toMatchObject({ key: 'c1', title: 'Card' })
    expect(groups.flatMap(g => g.items).map(item => item.key)).toEqual(['in_card'])
  })

  it('keeps designer-order fields inside the card when all are present in formFields', () => {
    // Counterpart to extract dropping Hide fields: once dates stay in card.children,
    // revealing them must not create a rest group outside Financial adjustment.
    const visible = cols(
      'merchant_credit',
      'merchant_credit_date',
      'temporary_refund',
      'temporary_refund_date',
      'rebilled_date',
    )
    const formFields: FormField[] = [
      {
        key: 'c1',
        label: 'Financial adjustment',
        type: 'card',
        span: 24,
        children: visible.map(c => field(c.field)),
      },
    ]
    const groups = buildDialogLayoutGroups(formFields, visible)
    expect(groups).toHaveLength(1)
    expect(groups[0].title).toBe('Financial adjustment')
    expect(groups[0].items.map(item => item.key)).toEqual(visible.map(c => c.field))
  })

  it.each([
    ['flat', (marker: FormField) => [field('before'), marker, field('after')]],
    ['card', (marker: FormField) => [{
      key: 'card', label: 'Card', type: 'card', children: [field('before'), marker, field('after')],
    }]],
    ['row', (marker: FormField) => [{
      key: 'row', label: '', type: 'row', children: [{
        key: 'col', label: '', type: 'col', children: [field('before'), marker, field('after')],
      }],
    }]],
    ['tabs', (marker: FormField) => [{
      key: 'tabs', label: '', type: 'tabs', tabs: [{
        name: 'one', label: 'One', fields: [field('before'), marker, field('after')],
      }],
    }]],
    ['collapse', (marker: FormField) => [{
      key: 'collapse', label: '', type: 'collapse', collapsePanels: [{
        name: 'one', label: 'One', fields: [field('before'), marker, field('after')],
      }],
    }]],
  ])('preserves marker order in %s layout', (_name, makeFields) => {
    const marker: FormField = { key: 'assignment-marker', label: '', type: 'miAssignment' }
    const groups = buildDialogLayoutGroups(makeFields(marker), cols('before', 'after'))
    expect(groups.flatMap(group => group.items).map(item => item.key))
      .toEqual(['before', 'assignment-marker', 'after'])
  })

  /**
   * The designer's standard "Hide" toggle on the Assignment Mode component was
   * being ignored — the block always rendered whenever the sub-table had a valid
   * assignment contract. The whole block (marker + its owned fields) must
   * disappear together, and its owned fields must not leak into `rest` as
   * ordinary unwrapped columns.
   */
  it('drops a hidden miAssignment marker and its owned fields entirely', () => {
    const marker: FormField = {
      key: 'assignment-marker',
      label: '',
      type: 'miAssignment',
      hidden: true,
      children: [
        { key: 'assignee', label: 'Assignee', type: 'user' },
        { key: 'bu_code', label: 'BU', type: 'text' },
      ],
    }
    const formFields: FormField[] = [field('before'), marker, field('after')]
    const visible = cols('before', 'after', 'assignee', 'bu_code')
    const groups = buildDialogLayoutGroups(formFields, visible)
    const keys = groups.flatMap(group => group.items).map(item => item.key)
    expect(keys).not.toContain('assignment-marker')
    expect(keys).not.toContain('assignee')
    expect(keys).not.toContain('bu_code')
    expect(keys).toEqual(['before', 'after'])
  })

  it('keeps a visible miAssignment marker and its owned fields (regression baseline)', () => {
    const marker: FormField = {
      key: 'assignment-marker',
      label: '',
      type: 'miAssignment',
      hidden: false,
      children: [{ key: 'assignee', label: 'Assignee', type: 'user' }],
    }
    const formFields: FormField[] = [field('before'), marker, field('after')]
    const visible = cols('before', 'after', 'assignee')
    const groups = buildDialogLayoutGroups(formFields, visible)
    const keys = groups.flatMap(group => group.items).map(item => item.key)
    expect(keys).toEqual(['before', 'assignment-marker', 'assignee', 'after'])
  })

  /**
   * Same Hidden-toggle contract as above, but through the card-wrapped `walk()`
   * branch (taken whenever the sub-form has any elCard) instead of the flat
   * `collectLayoutItems` branch — the two used to diverge because only the flat
   * branch checked `f.hidden`.
   */
  it('drops a hidden miAssignment marker inside a card-wrapped layout', () => {
    const marker: FormField = {
      key: 'assignment-marker',
      label: '',
      type: 'miAssignment',
      hidden: true,
      children: [
        { key: 'bu_code', label: 'BU', type: 'text' },
        { key: 'role_code', label: 'Role', type: 'text' },
      ],
    }
    const formFields: FormField[] = [{
      key: 'card-1',
      label: 'Card',
      type: 'card',
      children: [field('before'), marker, field('after')],
    }]
    const visible = cols('before', 'after', 'bu_code', 'role_code')
    const groups = buildDialogLayoutGroups(formFields, visible)
    const keys = groups.flatMap(group => group.items).map(item => item.key)
    expect(keys).not.toContain('assignment-marker')
    expect(keys).not.toContain('bu_code')
    expect(keys).not.toContain('role_code')
    expect(keys).toEqual(['before', 'after'])
  })

  it('keeps a visible miAssignment marker inside a card-wrapped layout (regression baseline)', () => {
    const marker: FormField = {
      key: 'assignment-marker',
      label: '',
      type: 'miAssignment',
      hidden: false,
      children: [{ key: 'bu_code', label: 'BU', type: 'text' }],
    }
    const formFields: FormField[] = [{
      key: 'card-1',
      label: 'Card',
      type: 'card',
      children: [field('before'), marker, field('after')],
    }]
    const visible = cols('before', 'after', 'bu_code')
    const groups = buildDialogLayoutGroups(formFields, visible)
    const keys = groups.flatMap(group => group.items).map(item => item.key)
    expect(keys).toEqual(['before', 'assignment-marker', 'bu_code', 'after'])
  })

  /**
   * The two card cases above both nest the marker INSIDE the card, which routes
   * through collectLayoutItems. A marker sitting at top level beside a card takes
   * the other branch (`walk`), and that branch used to mishandle both states:
   * hidden markers re-surfaced their owned fields as standalone groups, and
   * visible ones emitted the block with no picker inside it.
   */
  it('drops a hidden top-level marker that sits beside a card', () => {
    const marker: FormField = {
      key: 'assignment-marker',
      label: '',
      type: 'miAssignment',
      hidden: true,
      children: [{ key: 'assignee', label: 'Assignee', type: 'text' }],
    }
    const formFields: FormField[] = [
      { key: 'card-1', label: 'Card', type: 'card', children: [field('before')] },
      marker,
    ]
    const groups = buildDialogLayoutGroups(formFields, cols('before', 'assignee'))
    const keys = groups.flatMap(group => group.items).map(item => item.key)
    expect(keys).not.toContain('assignment-marker')
    expect(keys).not.toContain('assignee')
    expect(keys).toEqual(['before'])
  })

  it('keeps a visible top-level marker beside a card owning its picker', () => {
    const marker: FormField = {
      key: 'assignment-marker',
      label: '',
      type: 'miAssignment',
      hidden: false,
      children: [{ key: 'assignee', label: 'Assignee', type: 'text' }],
    }
    const formFields: FormField[] = [
      { key: 'card-1', label: 'Card', type: 'card', children: [field('before')] },
      marker,
    ]
    const groups = buildDialogLayoutGroups(formFields, cols('before', 'assignee'))
    const keys = groups.flatMap(group => group.items).map(item => item.key)
    // The block must never be an empty frame — it owns the picker.
    expect(keys).toEqual(['before', 'assignment-marker', 'assignee'])
  })
})

function field(key: string): FormField {
  return { key, label: key, type: 'text' }
}

describe('groupAssignmentFieldsUnderMarker', () => {
  const items = (...keys: string[]): DialogLayoutItem[] =>
    keys.map(key => key === 'MARKER'
      ? { type: 'miAssignment', key: 'marker' }
      : { type: 'column', key, column: { field: key, label: key, type: 'text' } })

  it('pulls assignment fields up to directly follow the marker', () => {
    const result = groupAssignmentFieldsUnderMarker(
      items('assignee', 'name', 'MARKER', 'note'),
      ['assignee'],
    )
    expect(result.map(i => i.key)).toEqual(['name', 'marker', 'assignee', 'note'])
  })

  it('tags the final owned field so CSS can close the block', () => {
    const result = groupAssignmentFieldsUnderMarker(
      items('MARKER', 'bu', 'role', 'other'),
      ['bu', 'role'],
    )
    expect(result.map(i => (i as { assignmentSlot?: string }).assignmentSlot))
      .toEqual([undefined, 'owned', 'last', undefined])
  })

  it('leaves the list untouched with no marker or no owned fields', () => {
    const noMarker = items('assignee', 'name')
    expect(groupAssignmentFieldsUnderMarker(noMarker, ['assignee'])).toBe(noMarker)
    const noOwned = items('MARKER', 'name')
    expect(groupAssignmentFieldsUnderMarker(noOwned, [])).toBe(noOwned)
  })

  it('never adds or drops items', () => {
    const input = items('a', 'assignee', 'MARKER', 'b', 'role')
    const result = groupAssignmentFieldsUnderMarker(input, ['assignee', 'role'])
    expect(result).toHaveLength(input.length)
    expect([...result.map(i => i.key)].sort()).toEqual([...input.map(i => i.key)].sort())
  })
})

