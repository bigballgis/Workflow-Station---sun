/**
 * A `subTable` grid nested inside an `inlineSubForm` widget must receive the same FK/PK runtime
 * context the top-level `subTable` widget gets from FormRendererFields.
 *
 * Regression (To Do task detail, FU 50005 "Sub task"): the People grid rendered inside the
 * Inline Form on `subtable` refused every Add with
 *   "Please create a Main table record before adding People data."
 * The nested render path passed only a 13-prop subset: `subTableBindingsForContext` (the sole
 * carrier of the synthetic PRIMARY binding entry), the full `parentTablesById` ancestor pool, and
 * the binding's `bindingLinkMode` / `bindingForeignKeyField` were all dropped two hops earlier
 * (FormRendererFields → SubTableInlineForm → PortalFormFields), so `guardBeforeChildRowAdd` could
 * neither resolve the main-table ancestor row nor auto-allocate its PK, and hard-failed.
 *
 * "Main table" in that message is itself the fingerprint: `primaryTableDisplayName` was undefined
 * too, so the message fell back to `subTable.mainTableDefault`.
 */
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PortalFormFields from '../PortalFormFields.vue'
import type { FormField } from '../formRendererHelpers'
import {
  buildRowAddContext,
  finalizeSubTableRowOnSave,
} from '@/utils/subTableRowRuntime'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (key: string) => key }) }
})

const MAIN_TABLE_ID = 50060
const SUBTASK_TABLE_ID = 50064
const PEOPLE_TABLE_ID = 50069

/** Main table: auto-PK `id`, not yet allocated while the task form is open. */
const mainFields = [
  { fieldName: 'name' },
  {
    fieldName: 'id',
    isPrimaryKey: true,
    pkGeneration: { strategy: 'prefixedSequence' as const, prefix: 'Test-' },
  },
]

/** `subtable` (MI participant collection): own auto-PK, structural FK up to the main record. */
const subtaskFields = [
  { fieldName: 'sub_task_id', isPrimaryKey: true, pkGeneration: { strategy: 'uuid' as const } },
  {
    fieldName: 'main_id',
    isForeignKey: true,
    refTableId: MAIN_TABLE_ID,
    refPrimaryKeyFields: ['id'],
  },
]

/** People: participant-scoped child, structural FK to the participant row. */
const peopleFields = [
  { fieldName: 'age' },
  { fieldName: 'sex' },
  { fieldName: 'id', isPrimaryKey: true, pkGeneration: { strategy: 'uuid' as const } },
  {
    fieldName: 'sub_task_id',
    isForeignKey: true,
    refTableId: SUBTASK_TABLE_ID,
    refPrimaryKeyFields: ['sub_task_id'],
  },
]

function allocator(values: Record<number, string[]>) {
  const cursor: Record<number, number> = {}
  return vi.fn(async ({ tableId }: { tableId: number }) => {
    const pool = values[tableId] ?? []
    const i = cursor[tableId] ?? 0
    cursor[tableId] = i + 1
    return [pool[i] ?? `${tableId}-${i}`]
  })
}

/** Full context, as the top-level subTable widget receives it from FormRendererFields. */
const fullContext = {
  subTableBindingsForContext: [
    { tableId: MAIN_TABLE_ID, bindingType: 'PRIMARY', tableName: 'Main table' },
    { tableId: SUBTASK_TABLE_ID, bindingType: 'SUB', tableName: 'Sub task' },
    { tableId: PEOPLE_TABLE_ID, bindingType: 'SUB', tableName: 'People' },
  ],
  parentTablesById: {
    [MAIN_TABLE_ID]: { fieldDefinitions: mainFields },
    [SUBTASK_TABLE_ID]: { fieldDefinitions: subtaskFields },
  },
}

describe('subTable nested inside an inlineSubForm — FK/PK context', () => {
  it('adds a People row when the participant row carries the full ancestor context', async () => {
    // The inline form edits an unsaved participant row: no PK allocated yet (PK is a save-time job).
    const participantRow = { name: 'rrcc' }
    const allocate = allocator({
      [SUBTASK_TABLE_ID]: ['ST-1'],
      [PEOPLE_TABLE_ID]: ['P-1'],
      [MAIN_TABLE_ID]: ['Test-000002'],
    })

    const result = await finalizeSubTableRowOnSave({
      row: { age: '3' },
      fieldDefinitions: peopleFields,
      rowAddContext: buildRowAddContext(
        {},
        fullContext.subTableBindingsForContext,
        participantRow,
        SUBTASK_TABLE_ID,
      ),
      tableId: PEOPLE_TABLE_ID,
      allocatePrimaryKeys: allocate,
      parentTablesById: fullContext.parentTablesById,
      primaryTableId: MAIN_TABLE_ID,
      primaryTableDisplayName: 'Main table',
      tableDisplayName: 'People',
      autoEnsurePrimaryRecord: true,
      parentTableId: SUBTASK_TABLE_ID,
      t: (k: string) => k,
    })

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.row.id).toBe('P-1')
    // FK points at the participant PK the host row will actually be saved under…
    expect(result.row.sub_task_id).toBe('ST-1')
    // …which only holds because the host adopts this patch instead of allocating its own.
    expect(result.parentRowPatch).toMatchObject({ name: 'rrcc', sub_task_id: 'ST-1' })
  })

  it('reproduces the failure when the nested render path drops the ancestor pool', async () => {
    const participantRow = { name: 'rrcc' }
    const result = await finalizeSubTableRowOnSave({
      row: { age: '3' },
      fieldDefinitions: peopleFields,
      // No PRIMARY entry (linkedSubTableBindings has none) and no ancestor field definitions.
      rowAddContext: buildRowAddContext({}, [], participantRow, SUBTASK_TABLE_ID),
      tableId: PEOPLE_TABLE_ID,
      allocatePrimaryKeys: allocator({}),
      parentTablesById: undefined,
      autoEnsurePrimaryRecord: true,
      parentTableId: SUBTASK_TABLE_ID,
      t: (k: string) => k,
    })

    expect(result.ok).toBe(false)
    if (result.ok) return
    expect(result.message).toBe('subTable.fkGuardMainNotReady')
  })
})

/**
 * The runtime above only behaves once the render path actually hands it the context. These lock
 * the wiring: PortalFormFields must forward the FK/PK context to a nested `subTable`, both
 * directly and across a purely visual layout container it recurses through.
 */
describe('PortalFormFields — FK/PK context reaches a nested subTable', () => {
  const capturedProps: Array<Record<string, unknown>> = []

  const stubs = {
    SubTableField: {
      props: [
        'primaryFormData',
        'primaryTableId',
        'primaryTableDisplayName',
        'subTableBindingsForContext',
        'parentTablesById',
        'parentTableId',
        'bindingLinkMode',
        'bindingForeignKeyField',
      ],
      template: '<div class="stub-sub-table-field" />',
      created(this: Record<string, unknown>) {
        capturedProps.push({ ...(this.$props as Record<string, unknown>) })
      },
    },
    FieldRenderer: true,
    ElCol: { template: '<div><slot /></div>' },
    ElRow: { template: '<div><slot /></div>' },
    ElCard: { template: '<div><slot /></div>' },
    ElFormItem: { template: '<div><slot /></div>' },
  }

  const peopleBinding = {
    bindingId: 77,
    tableName: 'People',
    tableId: PEOPLE_TABLE_ID,
    columns: [],
    data: [],
    fieldDefinitions: peopleFields,
    bindingLinkMode: 'structuralFk',
    foreignKeyField: 'sub_task_id',
  }

  const hostContext = {
    hostTableId: SUBTASK_TABLE_ID,
    hostFieldDefinitions: subtaskFields,
    hostPrimaryFormData: { name: 'rrcc' },
    hostPrimaryTableId: MAIN_TABLE_ID,
    hostPrimaryTableDisplayName: 'Test Main',
    hostSubTableBindingsForContext: fullContext.subTableBindingsForContext,
    hostParentTablesById: { [MAIN_TABLE_ID]: { fieldDefinitions: mainFields } },
  }

  function mountWith(fields: FormField[]) {
    capturedProps.length = 0
    mount(PortalFormFields, {
      props: {
        fields,
        model: { name: 'rrcc' },
        editable: true,
        subTableBindings: [peopleBinding],
        ...hostContext,
      },
      global: { stubs },
    })
    return capturedProps[0]
  }

  it('forwards the main-table context and the binding link mode to a direct nested subTable', () => {
    const seen = mountWith([
      { key: 'people', type: 'subTable', _bindingId: 77, span: 24 } as unknown as FormField,
    ])

    expect(seen).toBeDefined()
    expect(seen.primaryTableDisplayName).toBe('Test Main')
    expect(seen.primaryTableId).toBe(MAIN_TABLE_ID)
    // Sole carrier of the synthetic PRIMARY entry — without it the FK guard blocks every add.
    expect(seen.subTableBindingsForContext).toEqual(fullContext.subTableBindingsForContext)
    expect(seen.bindingLinkMode).toBe('structuralFk')
    expect(seen.bindingForeignKeyField).toBe('sub_task_id')
    // Inherited ancestors are merged with this form's own table, not replaced by it.
    expect(seen.parentTablesById).toMatchObject({
      [MAIN_TABLE_ID]: { fieldDefinitions: mainFields },
      [SUBTASK_TABLE_ID]: { fieldDefinitions: subtaskFields },
    })
  })

  it('keeps that context across a layout container it recurses through', () => {
    const seen = mountWith([
      {
        key: 'card',
        type: 'card',
        span: 24,
        children: [{ key: 'people', type: 'subTable', _bindingId: 77, span: 24 }],
      } as unknown as FormField,
    ])

    expect(seen).toBeDefined()
    expect(seen.primaryTableId).toBe(MAIN_TABLE_ID)
    expect(seen.subTableBindingsForContext).toEqual(fullContext.subTableBindingsForContext)
    expect(seen.parentTablesById).toMatchObject({
      [MAIN_TABLE_ID]: { fieldDefinitions: mainFields },
      [SUBTASK_TABLE_ID]: { fieldDefinitions: subtaskFields },
    })
  })
})
