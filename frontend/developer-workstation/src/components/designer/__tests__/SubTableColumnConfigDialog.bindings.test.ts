import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SubTableColumnConfigDialog from '../sub-table-list/SubTableColumnConfigDialog.vue'

const wrap = { template: '<div><slot/><slot name="footer"/></div>' }
function render(options: Array<Record<string, unknown>>) {
  return mount(SubTableColumnConfigDialog, { props: { showActionColumnConfig: true, editingActionColumnType: 'linkForm',
    linkColumnConfig: { boundSubTableBindingId: 2 }, lookupColumnConfig: {}, subTableBindingOptions: options },
    global: { mocks: { $t: (key: string) => key }, stubs: { ElDialog: wrap, ElForm: wrap, ElFormItem: wrap, ElSelect: wrap,
      ElOption: { props: ['label', 'value'], template: '<span :data-id="value">{{ label }}</span>' }, ElInput: true, ElButton: wrap, LookupBindingSelect: true } },
  })
}
describe('Link Form target binding labels', () => {
  it('distinguishes same-table options by configured filter FK and binding ID without changing values', () => {
    const wrapper = render([{ bindingId: 1, tableName: 'Lines', filterFkFieldName: 'parent_one' }, { bindingId: 2, tableName: 'Lines', filterFkFieldName: 'parent_two' }])
    expect(wrapper.get('[data-id="1"]').text()).toBe('Lines [parent_one · #1]')
    expect(wrapper.get('[data-id="2"]').text()).toBe('Lines [parent_two · #2]')
    expect(wrapper.emitted('save')).toBeUndefined()
  })
  it('keeps unique labels short and uses IDs to distinguish old options without FK metadata', () => {
    const wrapper = render([{ bindingId: 1, tableName: 'Lines' }, { bindingId: 2, tableName: 'Lines' }, { bindingId: 3, tableName: 'Other' }])
    expect(wrapper.get('[data-id="1"]').text()).toBe('Lines [#1]')
    expect(wrapper.get('[data-id="3"]').text()).toBe('Other')
  })
})
