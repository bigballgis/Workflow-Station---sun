import { flushPromises, shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import SubTableField from '../SubTableField.vue'

const post = vi.hoisted(() => vi.fn())
vi.mock('vue-i18n', async original => ({ ...await original<typeof import('vue-i18n')>(), useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/api/request', () => ({ request: { get: vi.fn(), post } }))
vi.mock('@/api/task', () => ({ assignSubTableRow: vi.fn(), getSubTableData: vi.fn(), getTaskDetail: vi.fn() }))
vi.mock('@/api/user', () => ({ userApi: { searchUsers: vi.fn() } }))

describe('General sub-table assignment column labels', () => {
  for (const field of ['assignee_id', 'handler_code']) {
    it(`uses explicit BPMN mapping ${field}, even without legacy assigneeField prop`, async () => {
      post.mockResolvedValue({ data: [{ id: 'person-uuid', username: 'alice', name: 'Alice Example' }] })
      const original = [{ line_key: 'person-uuid', [field]: 'person-uuid' }]
      const table = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) })
      const column = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.({ row: original[0], $index: 0 })) })
      const wrapper = shallowMount(SubTableField, { props: {
        title: 'General', columns: [{ field, label: 'Assignee' }, { field: 'line_key', label: 'Line key' }],
        modelValue: structuredClone(original), editable: false,
        assignmentConfig: { allowUser: true, allowRole: false, assigneeField: field },
      }, global: { directives: { loading: {} }, stubs: {
        ElTable: table, ElTableColumn: column, ElButton: true, ElIcon: true, ElAlert: true,
        ElDialog: true, ElTag: true, ElSlider: true, ElSwitch: true, ElRate: true,
        ElInput: true, ElDescriptions: true, ElDescriptionsItem: true, ElLink: true,
        ElEmpty: true, ElRow: true, ElForm: true,
      } } })
      await flushPromises()
      const state = wrapper.vm as unknown as {
        isAssignmentPersonColumn: (field: string) => boolean
        assignmentPersonLabel: (value: unknown) => string
      }
      expect(typeof state.assignmentPersonLabel).toBe('function')
      expect(state.isAssignmentPersonColumn(field)).toBe(true)
      expect(state.isAssignmentPersonColumn('line_key')).toBe(false)
      expect(state.assignmentPersonLabel('person-uuid')).toBe('Alice Example')
      expect(wrapper.text()).toContain('Alice Example')
      expect(wrapper.text()).toContain('person-uuid') // ordinary line key remains raw
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
      expect(wrapper.props('modelValue')).toEqual(original)
      wrapper.unmount()
    })
  }
})
