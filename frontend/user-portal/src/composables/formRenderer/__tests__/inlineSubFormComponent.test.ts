import { describe, it, expect, vi } from 'vitest'
import { useInlineSubFormComponent } from '../useInlineSubFormComponent'
import type { SubTableBinding } from '../useSubTableBindings'
import type { FormField } from '../../../components/formRendererHelpers'

/**
 * Contract of the Inline Form widget runtime:
 *  - Exactly one row is edited: the current MI sub-task's own row (matched via
 *    `currentMiRowId`) when the binding is MI-scoped, else row[0] for the plain single-row case
 *  - blank-but-editable when the sub-table has no rows yet; first edit creates row[0]
 *  - NEVER allocates a primary key or writes an FK (see the R3 note in the composable)
 *  - self-referencing bindings must not recurse
 */

const FIELD: FormField = {
  key: '__inlineSubForm_66',
  label: '',
  type: 'inlineSubForm',
  _bindingId: 66,
}

/** Inline Form pointing at the People link-child binding (FU 50005). */
const FIELD_PEOPLE: FormField = {
  key: '__inlineSubForm_50547',
  label: '',
  type: 'inlineSubForm',
  _bindingId: 50547,
}

function makeBinding(over: Partial<SubTableBinding> = {}): SubTableBinding {
  return {
    bindingId: 66,
    tableId: 7,
    bindingType: 'SUB',
    bindingMode: 'EDITABLE',
    tableName: 'Attachment',
    tableType: 'SUB',
    tableDescription: '',
    columns: [],
    data: [],
    formFields: [],
    foreignKeyField: 'main_id',
    ...over,
  } as SubTableBinding
}

function setup(binding: SubTableBinding | undefined, over: Record<string, unknown> = {}) {
  const handleSubTableUpdate = vi.fn()
  const api = useInlineSubFormComponent({
    readonly: () => false,
    resolveBinding: () => binding,
    isBindingModeEditable: (m) => String(m ?? '').toUpperCase() === 'EDITABLE',
    handleSubTableUpdate,
    ...over,
  } as never)
  return { api, handleSubTableUpdate }
}

describe('useInlineSubFormComponent — row resolution', () => {
  it('returns null when the sub-table has no rows (blank editable form)', () => {
    const { api } = setup(makeBinding({ data: [] }))
    expect(api.resolveInlineSubFormRow(FIELD)).toBeNull()
  })

  it('returns row[0] when rows exist', () => {
    const { api } = setup(makeBinding({ data: [{ id: 1, note: 'a' }, { id: 2, note: 'b' }] }))
    expect(api.resolveInlineSubFormRow(FIELD)).toEqual({ id: 1, note: 'a' })
  })

  it('returns a copy, not the live row object', () => {
    const row = { id: 1, note: 'a' }
    const { api } = setup(makeBinding({ data: [row] }))
    const got = api.resolveInlineSubFormRow(FIELD)!
    got.note = 'mutated'
    expect(row.note).toBe('a')
  })

  it('falls back to the persisted __subTables__ slice when binding.data is empty', () => {
    const { api } = setup(makeBinding({ data: [] }), {
      getSavedRowsForBinding: () => [{ id: 9, note: 'saved' }],
    })
    expect(api.resolveInlineSubFormRow(FIELD)).toEqual({ id: 9, note: 'saved' })
  })

  it('prefers live binding.data over the persisted slice', () => {
    const { api } = setup(makeBinding({ data: [{ id: 1, note: 'live' }] }), {
      getSavedRowsForBinding: () => [{ id: 9, note: 'saved' }],
    })
    expect(api.resolveInlineSubFormRow(FIELD)!.note).toBe('live')
  })

  it('returns null when the binding cannot be resolved', () => {
    const { api } = setup(undefined)
    expect(api.resolveInlineSubFormRow(FIELD)).toBeNull()
  })
})

describe('useInlineSubFormComponent — MI row targeting (regression: editing one participant must not touch another)', () => {
  const participants = [
    { id_idw: 'Test-000009', name: '444' },
    { id_idw: 'Test-000010', name: '555' },
    { id_idw: 'Test-000011', name: '33444' },
  ]

  it('reads the current MI sub-task\'s own row, not row[0], when currentMiRowId identifies a different row', () => {
    const { api } = setup(makeBinding({ data: participants, primaryKeyFields: ['id_idw'] }), {
      currentMiRowId: () => 'Test-000010',
    })
    expect(api.resolveInlineSubFormRow(FIELD)).toEqual({ id_idw: 'Test-000010', name: '555' })
  })

  it('falls back to row[0] when currentMiRowId is absent (plain non-MI single-row case unchanged)', () => {
    const { api } = setup(makeBinding({ data: participants, primaryKeyFields: ['id_idw'] }))
    expect(api.resolveInlineSubFormRow(FIELD)).toEqual({ id_idw: 'Test-000009', name: '444' })
  })

  it('writes an edit back into the matched MI row, not row[0], leaving sibling participants untouched', () => {
    const { api, handleSubTableUpdate } = setup(makeBinding({ data: participants, primaryKeyFields: ['id_idw'] }), {
      currentMiRowId: () => 'Test-000010',
    })
    api.handleInlineSubFormUpdate(FIELD, { name: 'edited' })
    const [, rows] = handleSubTableUpdate.mock.calls[0]
    expect(rows).toEqual([
      { id_idw: 'Test-000009', name: '444' },
      { id_idw: 'Test-000010', name: 'edited' },
      { id_idw: 'Test-000011', name: '33444' },
    ])
  })

  it('reproduces the exact regression: editing "Name" on participant Test-000010 must not surface or overwrite Test-000009/Test-000011 data', () => {
    const { api, handleSubTableUpdate } = setup(makeBinding({ data: participants, primaryKeyFields: ['id_idw'] }), {
      currentMiRowId: () => 'Test-000010',
    })
    // Before the fix, resolveInlineSubFormRow always returned row[0] (Test-000009's data) —
    // the form would show/edit the wrong participant entirely.
    expect(api.resolveInlineSubFormRow(FIELD)!.id_idw).toBe('Test-000010')
    api.handleInlineSubFormUpdate(FIELD, { name: 'renamed' })
    const [, rows] = handleSubTableUpdate.mock.calls[0]
    expect(rows.find((r: any) => r.id_idw === 'Test-000009').name).toBe('444')
    expect(rows.find((r: any) => r.id_idw === 'Test-000010').name).toBe('renamed')
    expect(rows.find((r: any) => r.id_idw === 'Test-000011').name).toBe('33444')
  })
})

/**
 * An Inline Form bound to a participant-scoped CHILD table (People-style: structural FK
 * `sub_task_id` → the MI participant row) sees the cross-participant pool. Index 0 is then very
 * likely a sibling's row, so there is no safe default: falling back to it showed another sub-task's
 * data AND merged the edit into that same row, overwriting it.
 */
describe('useInlineSubFormComponent — link-child binding has no index-0 fallback (cross-participant overwrite)', () => {
  const peopleBinding = (data: unknown[]) =>
    makeBinding({
      bindingId: 50547,
      tableId: 50333,
      tableName: 'people',
      foreignKeyField: 'id',
      // 生产 binding 都带设计器主键；缺失会抛 MI_CONFIG_MISSING（不猜列名）
      primaryKeyFields: ['id'],
      data,
    } as Partial<SubTableBinding>)

  const ALICE = { id: 101, sub_task_id: '1', age: '30' }

  it('renders blank rather than a sibling participant\'s row when this participant owns none', () => {
    const { api } = setup(peopleBinding([ALICE]), { currentMiRowId: () => '2' })
    expect(api.resolveInlineSubFormRow(FIELD_PEOPLE)).toBeNull()
  })

  it('appends this participant\'s own row instead of overwriting the sibling\'s', () => {
    const { api, handleSubTableUpdate } = setup(peopleBinding([ALICE]), {
      currentMiRowId: () => '2',
    })
    api.handleInlineSubFormUpdate(FIELD_PEOPLE, { age: 'BOB_EDIT' })
    const [, rows] = handleSubTableUpdate.mock.calls[0]
    // Alice's row survives untouched…
    expect(rows).toContainEqual(ALICE)
    // …and Bob's edit lands on a new row of its own.
    expect(rows).toHaveLength(2)
    expect(rows[1]).toEqual({ age: 'BOB_EDIT' })
  })

  it('still edits this participant\'s own row in place when it IS present', () => {
    const bobRow = { id: 202, sub_task_id: '2', age: '41' }
    const { api, handleSubTableUpdate } = setup(peopleBinding([ALICE, bobRow]), {
      currentMiRowId: () => '2',
    })
    api.handleInlineSubFormUpdate(FIELD_PEOPLE, { age: '42' })
    const [, rows] = handleSubTableUpdate.mock.calls[0]
    expect(rows).toHaveLength(2)
    expect(rows[0]).toEqual(ALICE)
    expect(rows[1]).toEqual({ id: 202, sub_task_id: '2', age: '42' })
  })

  it('keeps the index-0 fallback for a NON-participant-scoped table (shared attachment, main_id)', () => {
    const attachment = makeBinding({
      bindingId: 50548,
      tableName: 'attachment',
      foreignKeyField: 'main_id',
      data: [{ id: 1, main_id: 'M1', file: 'a.pdf' }],
    } as Partial<SubTableBinding>)
    const { api } = setup(attachment, { currentMiRowId: () => '2' })
    expect(api.resolveInlineSubFormRow({ ...FIELD_PEOPLE, _bindingId: 50548 })).toEqual({
      id: 1,
      main_id: 'M1',
      file: 'a.pdf',
    })
  })
})

describe('useInlineSubFormComponent — write back', () => {
  it('creates row[0] on the first edit when the sub-table is empty', () => {
    const { api, handleSubTableUpdate } = setup(makeBinding({ data: [] }))
    api.handleInlineSubFormUpdate(FIELD, { note: 'first' })
    expect(handleSubTableUpdate).toHaveBeenCalledTimes(1)
    const [bindingId, rows] = handleSubTableUpdate.mock.calls[0]
    expect(bindingId).toBe(66)
    expect(rows).toEqual([{ note: 'first' }])
  })

  it('merges into row[0] in place on later edits, never appending', () => {
    const { api, handleSubTableUpdate } = setup(makeBinding({ data: [{ id: 1, note: 'a' }] }))
    api.handleInlineSubFormUpdate(FIELD, { note: 'b' })
    const [, rows] = handleSubTableUpdate.mock.calls[0]
    expect(rows).toHaveLength(1)
    expect(rows[0]).toEqual({ id: 1, note: 'b' })
  })

  it('leaves rows beyond the first untouched', () => {
    const { api, handleSubTableUpdate } = setup(
      makeBinding({ data: [{ id: 1, note: 'a' }, { id: 2, note: 'keep' }] }),
    )
    api.handleInlineSubFormUpdate(FIELD, { note: 'edited' })
    const [, rows] = handleSubTableUpdate.mock.calls[0]
    expect(rows).toHaveLength(2)
    expect(rows[1]).toEqual({ id: 2, note: 'keep' })
  })

  it('does not mutate the original binding.data array', () => {
    const data = [{ id: 1, note: 'a' }]
    const { api } = setup(makeBinding({ data }))
    api.handleInlineSubFormUpdate(FIELD, { note: 'b' })
    expect(data).toEqual([{ id: 1, note: 'a' }])
  })

  it('no-ops when the binding cannot be resolved', () => {
    const { api, handleSubTableUpdate } = setup(undefined)
    api.handleInlineSubFormUpdate(FIELD, { note: 'x' })
    expect(handleSubTableUpdate).not.toHaveBeenCalled()
  })

  /**
   * R3 guard: the main PK is allocated lazily (at submit, or at sub-table row save), and the
   * Add dialog defers it on purpose so a cancelled dialog does not burn a sequence number.
   * Seeding an FK here would force PK allocation on every keystroke — strictly worse than the
   * dialog, since merely opening the form would burn a number.
   */
  it('writes no foreign key and allocates no primary key when creating row[0]', () => {
    const { api, handleSubTableUpdate } = setup(
      makeBinding({ data: [], foreignKeyField: 'main_id' }),
    )
    api.handleInlineSubFormUpdate(FIELD, { note: 'first' })
    const [, rows] = handleSubTableUpdate.mock.calls[0]
    expect(rows[0]).not.toHaveProperty('main_id')
    expect(rows[0]).not.toHaveProperty('parent_id')
    expect(rows[0]).not.toHaveProperty('id')
    expect(Object.keys(rows[0])).toEqual(['note'])
  })
})

describe('useInlineSubFormComponent — readonly', () => {
  it('is readonly when the whole form is readonly', () => {
    const { api } = setup(makeBinding({ bindingMode: 'EDITABLE' }), { readonly: () => true })
    expect(api.inlineSubFormReadonly(FIELD)).toBe(true)
  })

  it('is readonly when the binding itself is not editable', () => {
    const { api } = setup(makeBinding({ bindingMode: 'READONLY' }))
    expect(api.inlineSubFormReadonly(FIELD)).toBe(true)
  })

  it('is editable when the form is editable and the binding is EDITABLE', () => {
    const { api } = setup(makeBinding({ bindingMode: 'EDITABLE' }))
    expect(api.inlineSubFormReadonly(FIELD)).toBe(false)
  })

  it('is readonly when the binding is missing', () => {
    const { api } = setup(undefined)
    expect(api.inlineSubFormReadonly(FIELD)).toBe(true)
  })

  it('is readonly when bound to an ACTION table, even with bindingMode EDITABLE', () => {
    const { api } = setup(makeBinding({ bindingType: 'ACTION', bindingMode: 'EDITABLE' }))
    expect(api.inlineSubFormReadonly(FIELD)).toBe(true)
  })
})

describe('useInlineSubFormComponent — fields and self-nesting guard', () => {
  it('returns the bound sub-form fields', () => {
    const formFields: FormField[] = [{ key: 'note', label: 'Note', type: 'text' }]
    const { api } = setup(makeBinding({ formFields }))
    expect(api.resolveInlineSubFormFields(FIELD).map(f => f.key)).toEqual(['note'])
  })

  it('drops a nested self-reference instead of recursing forever', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const formFields: FormField[] = [
      { key: 'note', label: 'Note', type: 'text' },
      // binding 66's own sub-form embedding binding 66 — an infinite expansion
      { key: '__inlineSubForm_66', label: '', type: 'inlineSubForm', _bindingId: 66 },
    ]
    const { api } = setup(makeBinding({ formFields }))
    const out = api.resolveInlineSubFormFields(FIELD)
    expect(out.map(f => f.key)).toEqual(['note'])
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })

  it('drops a self-reference nested inside a layout container', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const formFields: FormField[] = [
      {
        key: 'card1',
        label: '',
        type: 'card',
        children: [
          { key: 'note', label: 'Note', type: 'text' },
          { key: '__inlineSubForm_66', label: '', type: 'inlineSubForm', _bindingId: 66 },
        ],
      },
    ]
    const { api } = setup(makeBinding({ formFields }))
    const out = api.resolveInlineSubFormFields(FIELD)
    expect(out[0].children!.map(f => f.key)).toEqual(['note'])
    warn.mockRestore()
  })

  it('keeps an inlineSubForm pointing at a DIFFERENT binding', () => {
    const formFields: FormField[] = [
      { key: '__inlineSubForm_99', label: '', type: 'inlineSubForm', _bindingId: 99 },
    ]
    const { api } = setup(makeBinding({ formFields }))
    expect(api.resolveInlineSubFormFields(FIELD).map(f => f.key)).toEqual(['__inlineSubForm_99'])
  })

  it('drops an indirect cycle (binding 66 -> binding 99, whose own form embeds one pointing back at 66) when the caller threads the visited set forward', () => {
    // This is the scenario a fresh top-level call cannot see on its own: resolving binding 99's
    // OWN fields (as the renderer does when it re-invokes resolveInlineSubFormFields for the
    // nested inlineSubForm field) must know binding 66 is already an ancestor on this render path.
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const binding66FormFields: FormField[] = [
      { key: '__inlineSubForm_99', label: '', type: 'inlineSubForm', _bindingId: 99 },
    ]
    const binding99FormFields: FormField[] = [
      { key: 'note', label: 'Note', type: 'text' },
      // Cycles back to 66 — must not be re-expanded when resolving binding 99's own fields.
      { key: '__inlineSubForm_66', label: '', type: 'inlineSubForm', _bindingId: 66 },
    ]
    const binding66 = makeBinding({ bindingId: 66, formFields: binding66FormFields })
    const binding99 = makeBinding({ bindingId: 99, formFields: binding99FormFields })
    const { api } = setup(undefined, {
      resolveBinding: (id?: number) => (Number(id) === 66 ? binding66 : Number(id) === 99 ? binding99 : undefined),
    })

    // Step 1: fresh top-level resolution of binding 66 — binding 99's inlineSubForm field survives
    // (99 is not an ancestor yet), exactly like the "DIFFERENT binding" test above.
    const level1 = api.resolveInlineSubFormFields(FIELD)
    expect(level1.map(f => f.key)).toEqual(['__inlineSubForm_99'])

    // Step 2: the renderer re-invokes resolution for the nested inlineSubForm field (binding 99),
    // now threading forward the ancestry that already includes 66 — matching how
    // PortalFormFields.vue's nextVisitedInlineSubFormBindingIds/FormRendererFields.vue's
    // initial Set([66]) accumulate and pass this down.
    const nestedField = level1[0]
    const level2 = api.resolveInlineSubFormFields(nestedField, new Set([66]))
    expect(level2.map(f => f.key)).toEqual(['note'])
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })

  it('returns an empty list when the binding is missing', () => {
    const { api } = setup(undefined)
    expect(api.resolveInlineSubFormFields(FIELD)).toEqual([])
  })

  it('titles the block with the bound table name', () => {
    const { api } = setup(makeBinding({ tableName: 'Attachment' }))
    expect(api.resolveInlineSubFormTitle(FIELD)).toBe('Attachment')
  })
})

describe('useInlineSubFormComponent — field-level permissions (composite bindingId:field key)', () => {
  const formFields: FormField[] = [
    { key: 'name', label: 'Name', type: 'text' },
    { key: 'bu_code', label: 'Business Unit', type: 'select' },
    { key: 'role_code', label: 'Role', type: 'select' },
  ]

  it('marks READONLY composite-keyed fields readonly, leaves others untouched', () => {
    const { api } = setup(makeBinding({ formFields }), {
      fieldPermissions: () => ({
        '66:bu_code': 'READONLY',
        '66:role_code': 'READONLY',
        '66:name': 'EDITABLE',
      }),
    })
    const out = api.resolveInlineSubFormFields(FIELD)
    expect(out.find(f => f.key === 'bu_code')?.readonly).toBe(true)
    expect(out.find(f => f.key === 'role_code')?.readonly).toBe(true)
    expect(out.find(f => f.key === 'name')?.readonly).toBeFalsy()
  })

  it('does not apply a different binding\'s composite key to this binding\'s field', () => {
    const { api } = setup(makeBinding({ formFields }), {
      fieldPermissions: () => ({ '99:bu_code': 'READONLY' }),
    })
    const out = api.resolveInlineSubFormFields(FIELD)
    expect(out.find(f => f.key === 'bu_code')?.readonly).toBeFalsy()
  })

  it('leaves every field unchanged when fieldPermissions has no entry for this binding (backward compatible)', () => {
    const { api } = setup(makeBinding({ formFields }), {
      fieldPermissions: () => ({ name: 'READONLY' }),
    })
    const out = api.resolveInlineSubFormFields(FIELD)
    expect(out.find(f => f.key === 'bu_code')?.readonly).toBeFalsy()
  })

  it('leaves every field unchanged when fieldPermissions is absent entirely', () => {
    const { api } = setup(makeBinding({ formFields }))
    const out = api.resolveInlineSubFormFields(FIELD)
    expect(out.every(f => !f.readonly)).toBe(true)
  })

  it('applies composite-key readonly to fields nested inside a layout container', () => {
    const nested: FormField[] = [
      {
        key: 'card1',
        label: '',
        type: 'card',
        children: [
          { key: 'bu_code', label: 'Business Unit', type: 'select' },
        ],
      },
    ]
    const { api } = setup(makeBinding({ formFields: nested }), {
      fieldPermissions: () => ({ '66:bu_code': 'READONLY' }),
    })
    const out = api.resolveInlineSubFormFields(FIELD)
    expect(out[0].children![0].readonly).toBe(true)
  })
})
