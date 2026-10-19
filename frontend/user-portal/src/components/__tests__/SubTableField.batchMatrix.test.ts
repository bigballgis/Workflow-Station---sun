import { describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import SubTableField from '../SubTableField.vue'

vi.mock('vue-i18n', async importOriginal => ({
  ...await importOriginal<typeof import('vue-i18n')>(), useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('@/api/task', () => ({ assignSubTableRow: vi.fn(), getSubTableData: vi.fn(), getTaskDetail: vi.fn() }))
vi.mock('@/api/user', () => ({ userApi: { searchUsers: vi.fn() } }))

const rows = [{ item_token: 'SYNTHETIC-ONE', caption: 'unchanged', _wsRowVersion: 1 }]
const keys = ['allowBatchAdd', 'allowBatchEdit', 'allowBatchDelete', 'allowBatchAssign'] as const
const states = Array.from({ length: 16 }, (_, mask) => ({ mask,
  flags: Object.fromEntries(keys.map((key, bit) => [key, Boolean(mask & (1 << bit))])) }))
const crudStates = Array.from({ length: 8 }, (_, mask) => ({ mask,
  crud: { allowAdd: Boolean(mask & 1), allowEdit: Boolean(mask & 2), allowDelete: Boolean(mask & 4) } }))

function projection(flags: Record<string, boolean>, crud: Record<string, boolean>) {
  return { batchControlId: 'synthetic-grid', bindingId: '42', capabilities: flags, originalCrud: crud,
    assignmentConfig: { allowUser: true, allowRole: false, assigneeField: 'handler_code' },
    batchForm: { schemaVersion: 1, rule: [{ type: 'input', field: 'caption' }, { type: 'miAssignment', field: 'target' }] },
    rowDesign: { binding: { tableId: 7, bindingType: 'SUB', bindingMode: 'EDITABLE', bindingLinkMode: 'structuralFk',
      primaryKeyFields: ['item_token'], fieldDefinitions: [{ fieldName: 'item_token', isPrimaryKey: true }] },
    fields: [{ fieldName: 'caption' }], parentTablesById: {} } }
}

function render(flags: Record<string, boolean>, crud: Record<string, boolean>, extra: Record<string, unknown> = {}) {
  return shallowMount(SubTableField, { props: { title: 'Synthetic matrix', columns: [{ field: 'caption', label: 'Caption' }],
    modelValue: structuredClone(rows), editable: true, bindingId: 42, tableId: 7, functionUnitId: '3', onBatchChange: vi.fn(),
    bindingType: 'SUB', bindingLinkMode: 'structuralFk', primaryKeyFields: ['item_token'],
    batchCapability: projection(flags, crud), ...extra }, global: { directives: { loading: {} }, stubs: {
    ElButton: { template: '<button><slot/></button>' }, ElIcon: true, ElTable: true, ElTableColumn: true,
    ElDialog: true, ElEmpty: true, ElForm: true, ElFormItem: true, ElInput: true, ElSelect: true, ElOption: true,
    ElSlider: true, ElRate: true, SubTableAddDialog: true, SubTableBatchEditDialog: true,
    SubTableBatchDeleteDialog: true, ElAlert: true, ElDescriptions: true, ElDescriptionsItem: true,
    ElLink: true, ElRow: true, ElSwitch: true, ElTag: true,
  } } })
}

describe('A03 real SubTableField batch switch matrix', () => {
  for (const { mask, flags } of states) {
    it(`preserves missing original CRUD allow semantics for batch mask ${mask}`, async () => {
      const wrapper = render(flags, {})
      const entry = wrapper.findAll('.actions button').find(button => button.text() === 'subTable.batchEdit')
      expect(Boolean(entry)).toBe(mask !== 0)
      if (entry) {
        await entry.trigger('click')
        const workspace = wrapper.findComponent({ name: 'SubTableBatchEditDialog' })
        expect(['showAdd', 'allowEdit', 'showDelete', 'showAssign'].map(key => workspace.props(key))).toEqual(
          [1, 2, 4, 8].map(bit => Boolean(mask & bit)))
      }
      expect(wrapper.props('onBatchChange')).not.toHaveBeenCalled()
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
      expect(wrapper.props('modelValue')).toEqual(rows)
      wrapper.unmount()
    })
    for (const { mask: crudMask, crud } of crudStates) {
      it(`projects batch mask ${mask} against frozen CRUD mask ${crudMask}`, async () => {
        // Deliberately opposite live props: batch design authority must come from the frozen projection.
        const wrapper = render(flags, crud, { allowAdd: !crud.allowAdd, allowEdit: !crud.allowEdit, allowDelete: !crud.allowDelete })
        const expected = [Boolean(mask & 1) && crud.allowAdd, Boolean(mask & 2) && crud.allowEdit,
          Boolean(mask & 4) && crud.allowDelete, Boolean(mask & 8) && crud.allowEdit]
        const buttons = wrapper.findAll('.actions button')
        const entry = buttons.find(button => button.text() === 'subTable.batchEdit')
        expect(Boolean(entry)).toBe(expected.some(Boolean))
        if (entry) {
          expect(buttons[0]?.text()).toBe('subTable.batchEdit')
          await entry.trigger('click')
          const workspace = wrapper.findComponent({ name: 'SubTableBatchEditDialog' })
          expect(workspace.props('visible')).toBe(true)
          expect(['showAdd', 'allowEdit', 'showDelete', 'showAssign'].map(key => workspace.props(key))).toEqual(expected)
        }
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
        expect(wrapper.props('onBatchChange')).not.toHaveBeenCalled()
        expect(wrapper.props('modelValue')).toEqual(rows)
        wrapper.unmount()
      })
    }
    for (const denied of [{ editable: false }, { bindingType: 'ACTION' }, { bindingId: 43 }]) {
      it(`denies batch mask ${mask} for ${JSON.stringify(denied)}`, () => {
        const wrapper = render(flags, {}, denied)
        expect(wrapper.findAll('.actions button').some(button => button.text() === 'subTable.batchEdit')).toBe(false)
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
        wrapper.unmount()
      })
    }
  }

  it('keeps old configuration and all-disabled configuration free of batch workspace mounts', () => {
    for (const batchCapability of [undefined, projection(states[0]!.flags, {})]) {
      const wrapper = render(states[15]!.flags, {}, { batchCapability })
      expect(wrapper.findComponent({ name: 'SubTableBatchEditDialog' }).exists()).toBe(false)
      expect(wrapper.findComponent({ name: 'SubTableBatchDeleteDialog' }).exists()).toBe(false)
      expect(wrapper.findAll('.actions button').map(button => button.text())).toEqual([
        'subTable.exportWithData', 'subTable.import', 'subTable.add'])
      wrapper.unmount()
    }
  })
})
