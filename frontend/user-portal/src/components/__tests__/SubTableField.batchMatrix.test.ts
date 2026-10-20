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

describe('frozen batch capabilities on outer toolbar', () => {
  for (const { mask, flags } of states) {
    for (const { mask: crudMask, crud } of [{ mask: -1, crud: {} }, ...crudStates]) {
      it(`projects mask ${mask} against frozen CRUD ${crudMask}`, async () => {
        const wrapper = render(flags, crud, { allowAdd: false, allowEdit: false, allowDelete: false })
        const expected = [Boolean(mask & 1) && crud.allowAdd !== false, Boolean(mask & 2) && crud.allowEdit !== false,
          Boolean(mask & 4) && crud.allowDelete !== false, Boolean(mask & 8) && crud.allowEdit !== false]
        const buttons = wrapper.findAll('.actions button')
        expect(buttons.some(button => button.text() === 'subTable.batchAdd')).toBe(expected[0])
        expect(buttons.some(button => button.text() === 'subTable.batchDelete')).toBe(expected[2])
        const entry = buttons.find(button => button.text() === 'subTable.batchEdit')
        expect(Boolean(entry)).toBe(expected[1] || expected[3])
        if (entry) {
          expect(entry.attributes('disabled')).toBeDefined()
          const vm = wrapper.vm as unknown as { onSelectionChange(rows: typeof rows): void }
          vm.onSelectionChange(rows)
          await wrapper.vm.$nextTick()
          await entry.trigger('click')
          const dialog = wrapper.findComponent({ name: 'SubTableBatchEditDialog' })
          expect(dialog.props('visible')).toBe(true)
          expect(dialog.props('selectedRowIds')).toHaveLength(1)
          expect(dialog.props('allowEdit')).toBe(expected[1])
          expect(dialog.props('showAssign')).toBe(expected[3])
          expect(dialog.props('mode')).toBe('edit')
        }
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
        expect(wrapper.props('onBatchChange')).not.toHaveBeenCalled()
        wrapper.unmount()
      })
    }
    for (const denied of [{ editable: false }, { bindingType: 'ACTION' }, { bindingId: 43 }]) {
      it(`denies mask ${mask} for ${JSON.stringify(denied)}`, () => {
        const wrapper = render(flags, {}, denied)
        expect(wrapper.findAll('.actions button').some(button => ['subTable.batchEdit','subTable.batchAdd','subTable.batchDelete'].includes(button.text()))).toBe(false)
        wrapper.unmount()
      })
    }
  }
  it('does not offer batch actions for legacy forms', () => {
    const wrapper = render(states[15]!.flags, {}, { batchCapability: undefined })
    expect(wrapper.findAll('.actions button').map(button => button.text())).toEqual(['subTable.exportWithData', 'subTable.import', 'subTable.add'])
  })
})
