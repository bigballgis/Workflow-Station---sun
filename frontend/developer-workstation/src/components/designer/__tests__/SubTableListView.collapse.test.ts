import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
import SubTableListView from '../SubTableListView.vue'

vi.mock('@/api/subTableView', () => ({ subTableViewApi: { getAvailableFields: vi.fn(async () => ({ data: [] })) } }))
const fields = [
  { fieldName: 'caption', displayName: 'Caption', dataType: 'VARCHAR', nullable: true, isPrimaryKey: false },
  { fieldName: 'amount', displayName: 'Amount', dataType: 'INTEGER', nullable: true, isPrimaryKey: false },
]
async function render() {
  const wrapper = mount(SubTableListView, {
    props: { functionUnitId: 1, formId: 2, binding: { bindingId: 3, bindingType: 'SUB', bindingMode: 'EDITABLE', tableName: 'Lines', tableId: 4, tableType: 'SUB', tableDescription: '' }, availableFields: fields, modelValue: [] },
    global: {
      plugins: [createI18n({ legacy: false, locale: 'en', missingWarn: false, fallbackWarn: false, messages: { en: {} } })],
      directives: { loading: () => {} },
      stubs: { ElButton: true, ElLink: true, FormCreate: true, ElIcon: { template: '<span><slot/></span>' }, ElInput: { props: ['modelValue', 'size'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)"/>' }, ElEmpty: true, SubTablePreviewDialog: true, SubTableColumnConfigDialog: true, LookupPreview: true, ElDialog: true },
    },
  })
  await flushPromises()
  return wrapper
}
describe('List View vertical palette sections', () => {
  it('collapses only Table Columns, keeping Extend Action available', async () => {
    const wrapper = await render()
    await wrapper.get('[data-testid="table-columns-toggle"]').trigger('click')
    expect(wrapper.get('.section-content').isVisible()).toBe(false)
    expect(wrapper.get('.extend-action-list').isVisible()).toBe(true)
    expect(wrapper.get('.panel-section').classes()).toContain('is-collapsed')
    expect(wrapper.get('[data-testid="table-columns-toggle"]').attributes('aria-expanded')).toBe('false')
    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })
  it('retains the search and fields when expanded again', async () => {
    const wrapper = await render()
    await wrapper.get('input').setValue('Caption')
    const toggle = wrapper.get('[data-testid="table-columns-toggle"]')
    await toggle.trigger('click'); await toggle.trigger('click')
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('Caption')
    expect(wrapper.findAll('.section-content .field-name').map(item => item.text())).toEqual(['Caption'])
    wrapper.unmount()
  })
  it('folds Extend Action independently and preserves the existing whole-panel close', async () => {
    const wrapper = await render()
    await wrapper.get('[data-testid="extend-action-toggle"]').trigger('click')
    expect(wrapper.get('.extend-action-list').isVisible()).toBe(false)
    expect(wrapper.get('.section-content').isVisible()).toBe(true)
    await wrapper.get('.columns-panel-close').trigger('click')
    expect(wrapper.find('.columns-panel').exists()).toBe(false)
    await wrapper.get('.columns-toggle').trigger('click')
    expect(wrapper.get('.section-content').isVisible()).toBe(true)
    expect(wrapper.get('.extend-action-list').isVisible()).toBe(false)
    wrapper.unmount()
  })
})
