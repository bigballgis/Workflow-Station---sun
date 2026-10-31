import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { h } from 'vue'
import Layout from '../SubTableDialogLayout'
import type { FormField } from '../formRendererHelpers'
import type { DialogLayoutItem } from '../subTableAddDialogHelpers/dialogFormLayout'

vi.mock('../FieldRenderer.vue', () => ({ default: { props: ['field'], template: '<div>{{ field.label }}</div>' } }))
const leaf = (key: string): FormField => ({ key, label: key, type: 'text' })
function render(fields: FormField[], extraFields: FormField[] = []) {
  return mount(Layout, {
    props: { fields, extraFields, columns: ['a', 'b', 'email', 'person'].map(field => ({ field, label: field, type: 'text' as const })) },
    slots: { default: ({ item }: { item: DialogLayoutItem }) => h('span', { 'data-field': item.key, 'data-assignment': item.type === 'column' ? item.assignmentSlot : undefined }, item.key), placement: ({ field }: { field: FormField }) => h('aside', field.key) },
  })
}
describe('row dialog layout tree', () => {
  it('retains grid columns and nested card boundaries', () => {
    const wrapper = render([{ key: 'row', label: '', type: 'row', children: [
      { key: 'col', label: '', type: 'col', span: 12, children: [leaf('a')] },
      { key: 'outer', label: 'Outer', type: 'card', span: 12, children: [{ key: 'inner', label: 'Inner', type: 'card', children: [leaf('b')] }] },
    ] }])
    expect(wrapper.findAll('.el-card')).toHaveLength(2)
    expect(wrapper.findAll('.el-col-12')).toHaveLength(2)
    expect(wrapper.text()).toContain('Inner')
  })
  it('retains tabs, collapse panels and display-only content', () => {
    const wrapper = render([
      { key: 'tabs', label: '', type: 'tabs', tabs: [{ name: 'one', label: 'One', fields: [leaf('a')] }] },
      { key: 'collapse', label: '', type: 'collapse', collapsePanels: [{ name: 'two', label: 'Two', fields: [leaf('b')] }] },
      { key: 'title', label: 'Instructions', type: 'title' },
    ])
    expect(wrapper.findAll('.el-tabs')).toHaveLength(1)
    expect(wrapper.findAll('.el-collapse')).toHaveLength(1)
    expect(wrapper.text()).toContain('Instructions')
  })
  it('keeps assignment children grouped, excludes hidden trees and does not duplicate placements', () => {
    const note: FormField = { key: 'note', label: '', type: 'recordNote' }
    const wrapper = render([
      { key: 'assign', label: '', type: 'miAssignment', children: [leaf('person'), leaf('email')] },
      { key: 'hidden', label: '', type: 'card', hidden: true, children: [leaf('a')] }, note,
    ], [note])
    expect(wrapper.find('[data-field="email"]').attributes('data-assignment')).toBe('last')
    expect(wrapper.find('[data-field="a"]').exists()).toBe(false)
    expect(wrapper.findAll('aside')).toHaveLength(1)
  })
})
