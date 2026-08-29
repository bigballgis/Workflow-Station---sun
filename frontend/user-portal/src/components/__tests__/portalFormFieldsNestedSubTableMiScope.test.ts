import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PortalFormFields from '../PortalFormFields.vue'
import type { FormField } from '../formRendererHelpers'

/**
 * MI sub-task data isolation for a sub-table nested INSIDE an Inline Form
 * (FU 50005 "MI Subtask Demo": Sub task form → Inline Form on binding 50544
 * (`subtable` = the MI participant collection) → nested `subTable` widget on
 * binding 50547 (`people`, structural FK `sub_task_id` → participant `id_idw`)).
 *
 * The inline form edits exactly ONE participant row. The People grid inside it must
 * therefore show only the People rows of THAT participant. Before the fix,
 * `resolveSubTableRows` fell through to the binding's flat cross-participant
 * `data` array whenever the current participant's own row carried no nested
 * `__subTables__` slice — so a participant who had added no People rows was shown
 * the OTHER sub-task's rows (and editing them wrote into the wrong sub-task).
 */

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (key: string) => key }) }
})

const globalStubs = {
  SubTableField: {
    template: '<div class="stub-sub-table-field" :data-rows="JSON.stringify(modelValue)" />',
    props: ['modelValue', 'bindingType', 'editable'],
  },
  FieldRenderer: true,
  ElCol: { template: '<div><slot /></div>' },
  ElFormItem: { template: '<div><slot /></div>' },
}

const PEOPLE_FIELD: FormField = {
  key: '__subTable_50547',
  label: 'People',
  type: 'subTable',
  _bindingId: 50547,
  span: 24,
} as unknown as FormField

/** People rows as they arrive in the flat top-level slice: both participants pooled. */
const ALICE_PERSON = { id: 101, sub_task_id: '1', sex: true, age: '30' }
const BOB_PERSON = { id: 202, sub_task_id: '2', sex: false, age: '41' }

function peopleBinding(data: unknown[]) {
  return {
    bindingId: 50547,
    tableName: 'people',
    tableId: 50333,
    columns: [
      { field: 'sex', label: 'sex' },
      { field: 'age', label: 'age' },
    ],
    data,
    primaryKeyFields: ['id'],
    foreignKeyField: 'id',
    bindingLinkMode: 'structuralFk',
    fieldDefinitions: [
      { fieldName: 'id', isPrimaryKey: true },
      { fieldName: 'sub_task_id', isForeignKey: true },
    ],
  }
}

function mountPeopleGrid(participantRow: Record<string, unknown>, flatData: unknown[]) {
  return mount(PortalFormFields, {
    props: {
      fields: [PEOPLE_FIELD],
      // `model` is the inline form's rowModel — the ONE participant row being edited.
      model: participantRow,
      parentRow: participantRow,
      editable: true,
      subTableBindings: [peopleBinding(flatData)],
    },
    global: { stubs: globalStubs },
  })
}

function renderedRows(wrapper: ReturnType<typeof mountPeopleGrid>): any[] {
  const raw = wrapper.find('.stub-sub-table-field').attributes('data-rows')
  return JSON.parse(raw ?? '[]')
}

describe('PortalFormFields — sub-table nested in an Inline Form is scoped to the host participant row', () => {
  it('shows nothing when the current participant has no People rows, even though a sibling participant does', () => {
    // Bob's sub-task: he added no People. Alice's row is still in the flat slice.
    const bob = { id_idw: '2', name: 'Bob' }
    const wrapper = mountPeopleGrid(bob, [ALICE_PERSON])
    expect(renderedRows(wrapper)).toEqual([])
  })

  /**
   * The participant's OWN rows reach the grid through their nested `__subTables__`, which the load
   * pipeline populates for every participant (`syncMiLinkChildRowsIntoParentNested`, called from the
   * task loader, both MI resync paths, isolation and sub-table sync). The flat pool is therefore
   * never the legitimate source of a participant's own rows — only of everyone else's — so an MI host
   * with nothing nested renders empty rather than reaching into it.
   */
  it('renders empty for an MI host with nothing nested, even when the pool holds a row keyed to it', () => {
    const bob = { id_idw: '2', name: 'Bob' }
    const wrapper = mountPeopleGrid(bob, [ALICE_PERSON, BOB_PERSON])
    expect(renderedRows(wrapper)).toEqual([])
  })

  it('shows the participant\'s own rows from their nested slice while the pool holds both', () => {
    const bob = {
      id_idw: '2',
      name: 'Bob',
      __subTables__: { 50547: [BOB_PERSON] },
    }
    const wrapper = mountPeopleGrid(bob, [ALICE_PERSON, BOB_PERSON])
    expect(renderedRows(wrapper)).toEqual([BOB_PERSON])
  })

  it('still prefers the nested __subTables__ slice on the participant row when present', () => {
    const bob = {
      id_idw: '2',
      name: 'Bob',
      __subTables__: { 50547: [BOB_PERSON] },
    }
    const wrapper = mountPeopleGrid(bob, [ALICE_PERSON, BOB_PERSON])
    expect(renderedRows(wrapper)).toEqual([BOB_PERSON])
  })

  it('scopes the nested slice too, when a stale nested map pooled a sibling\'s row', () => {
    const bob = {
      id_idw: '2',
      name: 'Bob',
      __subTables__: { 50547: [ALICE_PERSON, BOB_PERSON] },
    }
    const wrapper = mountPeopleGrid(bob, [])
    expect(renderedRows(wrapper)).toEqual([BOB_PERSON])
  })

  /**
   * A row the user just added lands in the host row's own nested `__subTables__` (that is where
   * `onNestedSubTableRowsUpdate` writes it), NOT in the shared pool — so it must survive scoping
   * there even though its structural FK is only seeded later, at save.
   */
  it('keeps a freshly added row with no participant FK yet, in the host\'s own nested slice', () => {
    const fresh = { sex: true, age: '22' }
    const bob = {
      id_idw: '2',
      name: 'Bob',
      __subTables__: { 50547: [fresh] },
    }
    const wrapper = mountPeopleGrid(bob, [ALICE_PERSON])
    expect(renderedRows(wrapper)).toEqual([fresh])
  })

  /**
   * The flat `binding.data` pool is not merely filtered for an MI participant host — it is not
   * consulted at all. Filtering alone would still leak any pooled row that carries no participant
   * identity yet (a sibling's freshly-added, not-yet-seeded row), and would make "this participant
   * owns nothing" indistinguishable from "the pool happened to scope to nothing".
   */
  it('never falls back to the cross-participant pool for an MI host, even for rows with no FK', () => {
    const bob = { id_idw: '2', name: 'Bob' }
    // A sibling's row that has not been FK-seeded yet: indistinguishable from a fresh row by
    // identity alone, so only cutting the fallback off entirely keeps it out of Bob's grid.
    const siblingUnseeded = { sex: false, age: '99' }
    const wrapper = mountPeopleGrid(bob, [ALICE_PERSON, siblingUnseeded])
    expect(renderedRows(wrapper)).toEqual([])
  })

  it('leaves a NON-MI host row untouched: no participant key on the host means no scoping', () => {
    // A plain link-form / nested dialog host with no MI identity must keep today's behavior.
    const host = { some_field: 'x' }
    const wrapper = mountPeopleGrid(host, [ALICE_PERSON, BOB_PERSON])
    expect(renderedRows(wrapper)).toEqual([ALICE_PERSON, BOB_PERSON])
  })

  it('does not scope a shared process-level sub-table (FK to the main record, not a participant)', () => {
    const bob = { id_idw: '2', name: 'Bob' }
    const attachments = [
      { id: 1, main_id: 'M1', file: 'a.pdf' },
      { id: 2, main_id: 'M1', file: 'b.pdf' },
    ]
    const wrapper = mount(PortalFormFields, {
      props: {
        fields: [{ ...PEOPLE_FIELD, _bindingId: 50548 } as unknown as FormField],
        model: bob,
        parentRow: bob,
        editable: true,
        subTableBindings: [
          {
            bindingId: 50548,
            tableName: 'attachment',
            tableId: 50330,
            columns: [{ field: 'file', label: 'file' }],
            data: attachments,
            foreignKeyField: 'main_id',
          },
        ],
      },
      global: { stubs: globalStubs },
    })
    expect(renderedRows(wrapper as never)).toEqual(attachments)
  })
})
