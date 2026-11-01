import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import Dialog from '../SubTableAddDialog.vue'

vi.mock('vue-i18n', async original => ({ ...await original<typeof import('vue-i18n')>(), useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/api/user', () => ({ userApi: { searchUsers: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/api/admin', () => ({ getBusinessUnitTree: vi.fn().mockResolvedValue([]), getRolesByBusinessUnit: vi.fn().mockResolvedValue([]) }))
const form = defineComponent({ setup(_, { slots, expose }) {
  expose({ clearValidate() {}, resetFields() {}, validate: async () => true })
  return () => h('form', slots.default?.())
} })
const nested = defineComponent({ name: 'NestedSubTableField', emits: ['update:primaryFormData'],
  setup(_, { emit }) { return () => h('button', { onClick: () => emit('update:primaryFormData', { rootBusinessKey: 'ROOT-1' }) }, 'allocate root') } })

describe('nested row dialog primary identity', () => {
  it('reports a nested primary-key allocation to the host before the parent row is saved', async () => {
    const wrapper = mount(Dialog, { props: { visible: false, mode: 'add', columns: [], nestedSubTables: [
      { bindingId: 99, tableName: 'Renamed child', columns: [] },
    ] }, global: { stubs: { teleport: true, 'el-form': form, NestedSubTableField: nested } } })
    await wrapper.setProps({ visible: true })
    await flushPromises()
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('update:primaryFormData')).toEqual([[{ rootBusinessKey: 'ROOT-1' }]])
    expect(wrapper.emitted('save')).toBeUndefined()
    wrapper.unmount()
  })
})
