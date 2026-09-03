import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PortalFormFields from '../PortalFormFields.vue'

/**
 * A sub-table nested inside a form rendered by PortalFormFields (the Link Form dialog and the
 * Inline Form widget) must receive the FK/PK context its Add dialog needs to allocate a primary
 * key and seed the structural FK back to the host row.
 *
 * Regression: rows added to the People grid from inside the Link Form saved with an empty `id`
 * AND an empty `sub_task_id`, while the same grid opened from the sub-table Add/Edit dialog
 * filled both. PortalFormFields forwarded neither `bindingLinkMode` nor `bindingForeignKeyField`
 * to the nested SubTableField — the two inputs that drive that seeding.
 *
 * Note `bindingLinkMode` ('structuralFk' | 'miParticipantRow') is a DIFFERENT binding field from
 * `bindingMode` ('EDITABLE' | 'READONLY'); wiring the latter into the former silently produced
 * the same empty-FK symptom, so this test pins the value, not just its presence.
 */

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (key: string) => key }) }
})

const BINDING = {
  bindingId: 77,
  tableName: 'people',
  tableId: 50333,
  columns: [{ field: 'age', label: 'Age', type: 'text' }],
  data: [],
  primaryKeyFields: ['idqcxma'],
  fieldDefinitions: [{ fieldName: 'idqcxma', isPrimaryKey: true }],
  bindingMode: 'EDITABLE',
  bindingLinkMode: 'structuralFk',
  foreignKeyField: 'sub_task_idqcxma',
}

const FIELDS = [{ key: 'people', type: 'subTable', _bindingId: 77, span: 24 }]

/** Capture what the nested sub-table was handed, without booting the real component. */
const SubTableFieldStub = {
  name: 'SubTableField',
  props: [
    'bindingLinkMode', 'bindingForeignKeyField', 'bindingId', 'tableId',
    'primaryKeyFields', 'fieldDefinitions', 'parentRow', 'parentTableId',
    'functionUnitId', 'taskId', 'fieldPermissions',
  ],
  template: '<div class="sub-table-field-stub" />',
}

function mountFields(overrides: Record<string, unknown> = {}) {
  return mount(PortalFormFields, {
    props: {
      fields: FIELDS,
      model: { idqcxma: 'Test-000004' },
      editable: true,
      readonly: false,
      subTableBindings: [BINDING],
      hostTableId: 50331,
      hostFunctionUnitId: 'fu-demo',
      hostTaskId: 'task-1',
      ...overrides,
    } as never,
    global: { stubs: { teleport: true, SubTableField: SubTableFieldStub } },
  })
}

function nestedProps(wrapper: ReturnType<typeof mountFields>) {
  return wrapper.findComponent(SubTableFieldStub).props() as Record<string, unknown>
}

describe('PortalFormFields — FK/PK context for a nested sub-table', () => {
  it('forwards the link mode and FK column that drive PK allocation and FK seeding', () => {
    const wrapper = mountFields()
    const props = nestedProps(wrapper)

    // The value must be the LINK mode, never the EDITABLE/READONLY bindingMode.
    expect(props.bindingLinkMode).toBe('structuralFk')
    expect(props.bindingLinkMode).not.toBe('EDITABLE')
    expect(props.bindingForeignKeyField).toBe('sub_task_idqcxma')

    wrapper.unmount()
  })

  it('forwards the identity and host context the Add dialog seeds a row from', () => {
    const wrapper = mountFields()
    const props = nestedProps(wrapper)

    expect(props.bindingId).toBe(77)
    expect(props.tableId).toBe(50333)
    expect(props.primaryKeyFields).toEqual(['idqcxma'])
    expect(props.fieldDefinitions).toHaveLength(1)
    // The row being edited is the parent a new nested row links back to.
    expect(props.parentRow).toMatchObject({ idqcxma: 'Test-000004' })
    expect(props.parentTableId).toBe(50331)
    expect(props.functionUnitId).toBe('fu-demo')
    expect(props.taskId).toBe('task-1')

    wrapper.unmount()
  })

  it('passes an miParticipantRow binding through unchanged', () => {
    const wrapper = mountFields({
      subTableBindings: [{ ...BINDING, bindingLinkMode: 'miParticipantRow' }],
    })

    expect(nestedProps(wrapper).bindingLinkMode).toBe('miParticipantRow')

    wrapper.unmount()
  })
})
