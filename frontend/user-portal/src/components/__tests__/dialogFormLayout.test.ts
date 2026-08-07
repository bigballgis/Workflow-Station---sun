import { describe, it, expect } from 'vitest'
import {
  buildDialogLayoutGroups,
  ensureAssignmentBlockPlaced,
  groupAssignmentFieldsUnderMarker,
  type DialogLayoutItem,
} from '../subTableAddDialogHelpers/dialogFormLayout'
import type { DialogColumn } from '../subTableAddDialogHelpers'
import type { FormField } from '../formRendererHelpers'

const cols = (...fields: string[]): DialogColumn[] =>
  fields.map((field) => ({ field, label: field, type: 'text' }))

describe('buildDialogLayoutGroups', () => {
  it('returns a single flat group when formFields has no cards', () => {
    const visible = cols('a', 'b')
    expect(buildDialogLayoutGroups(undefined, visible)).toEqual([
      {
        key: 'flat',
        title: null,
        items: visible.map(column => ({ type: 'column', key: column.field, column })),
      },
    ])
    expect(buildDialogLayoutGroups([{ key: 'a', label: 'A', type: 'text', span: 24 }], visible)).toEqual([
      {
        key: 'flat',
        title: null,
        items: visible.map(column => ({ type: 'column', key: column.field, column })),
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
    expect(groups[1].items.map(item => item.key)).toEqual(['orphan'])
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

/**
 * Sub-forms saved before the Assignment Mode component existed carry no marker.
 * Requiring one left their assignee / BU / role controls outside the block and
 * rendered an empty frame (the FU 50005 "Assign Task" shape reproduced below).
 */
describe('ensureAssignmentBlockPlaced', () => {
  const group = (...keys: string[]) => ({
    key: 'flat',
    title: null,
    items: keys.map(key => key === 'MARKER'
      ? { type: 'miAssignment', key: 'marker' }
      : { type: 'column', key, column: { field: key, label: key, type: 'text' } }) as DialogLayoutItem[],
  })

  it('inserts the block where the first owned field already sits', () => {
    // FU 50005 order: main_id, id_idw, name, assignee — no marker anywhere.
    const groups = [group('main_id', 'id_idw', 'name', 'assignee')]
    const [result] = ensureAssignmentBlockPlaced(groups, ['assignee'])

    expect(result!.items.map(i => i.type))
      .toEqual(['column', 'column', 'column', 'miAssignment', 'column'])
    // The picker is owned by the block and closes it.
    const owned = result!.items.filter(i => 'assignmentSlot' in i && i.assignmentSlot)
    expect(owned).toHaveLength(1)
    expect((owned[0] as { assignmentSlot?: string }).assignmentSlot).toBe('last')
  })

  it('keeps BU before Role and frames both in role mode', () => {
    const groups = [group('name', 'bu_code', 'role_code')]
    const [result] = ensureAssignmentBlockPlaced(groups, ['bu_code', 'role_code'])

    expect(result!.items.map(i => i.key))
      .toEqual(['name', '__mi_assignment_block__', 'bu_code', 'role_code'])
    expect(result!.items.filter(i => 'assignmentSlot' in i && i.assignmentSlot)).toHaveLength(2)
  })

  it('defers to a marker the designer already placed', () => {
    const groups = [group('name', 'MARKER', 'assignee')]
    expect(ensureAssignmentBlockPlaced(groups, ['assignee'])).toBe(groups)
  })

  it('never strands a headless block when nothing is owned', () => {
    const groups = [group('name', 'assignee')]
    expect(ensureAssignmentBlockPlaced(groups, [])).toBe(groups)
    // Owned field configured but absent from the dialog's columns.
    expect(ensureAssignmentBlockPlaced(groups, ['missing_field'])).toBe(groups)
  })

  it('adds only the block itself — no column is added or dropped', () => {
    const groups = [group('a', 'assignee', 'b')]
    const [result] = ensureAssignmentBlockPlaced(groups, ['assignee'])
    const columns = result!.items.filter(i => i.type === 'column').map(i => i.key)
    expect(columns).toEqual(['a', 'assignee', 'b'])
  })

  /**
   * Guardrail for the Hidden-toggle fix: buildDialogLayoutGroups drops a hidden
   * marker's owned fields along with it (see the miAssignment describe block
   * above), so a legacy pre-adoption form's groups would look identical to one
   * whose marker is simply hidden — both have no miAssignment item and no owned
   * columns. ensureAssignmentBlockPlaced alone cannot tell these apart; the caller
   * (SubTableAddDialog.vue's `hasAssignmentMarker`) MUST check the raw formFields
   * tree — not post-layout `groups` — before invoking this function, or a hidden
   * marker's block would be wrongly resurrected as a visible synthetic one.
   */
  it('would wrongly resurrect a visible block if the caller failed to check the raw formFields for a hidden marker', () => {
    // Simulates groups AFTER a hidden miAssignment marker's fields were already
    // dropped by buildDialogLayoutGroups — indistinguishable here from "no marker
    // ever existed". This function's own guard (no miAssignment item present) is
    // not enough on its own; SubTableAddDialog.vue's hasAssignmentMarker gate is
    // what actually prevents this from firing in production.
    const groups = [group('name', 'assignee')]
    const [result] = ensureAssignmentBlockPlaced(groups, ['assignee'])
    expect(result!.items.some(i => i.type === 'miAssignment')).toBe(true)
  })
})
