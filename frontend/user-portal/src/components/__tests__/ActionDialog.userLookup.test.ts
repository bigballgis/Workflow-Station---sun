import { describe, expect, it, vi } from 'vitest'
import { nextTick, reactive } from 'vue'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ActionDialog from '@/components/tasks/ActionDialog.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
  createI18n: () => ({
    global: { t: (key: string) => key, locale: { value: 'en' } },
    install: () => {},
  }),
}))

vi.mock('@/api/permission', () => ({
  permissionApi: {
    getBusinessUnitsTree: vi.fn(),
    getBusinessUnitRoles: vi.fn(),
  },
}))

const LookupFieldStub = {
  name: 'LookupField',
  props: [
    'tableId',
    'searchFields',
    'displayField',
    'displayFields',
    'selectedDisplayField',
    'viewFields',
    'modelValue',
    'prefetchLimit',
    'remoteFilter',
  ],
  emits: ['update:modelValue', 'select', 'clear'],
  template:
    '<button class="stub-lookup" type="button" @click="$emit(\'select\', { id: \'user-b\', display_name: \'Li Si\', username: \'lisi\' })">lookup</button>',
}

describe('ActionDialog USER lookup picker', () => {
  it('binds sys_users lookup and writes the selected row id', async () => {
    const formData = reactive({
      targetUserId: '',
      reason: '',
      targetType: 'USER' as const,
      delegatedBuId: '',
      delegatedBuCode: '',
      delegatedRoleCode: '',
    })
    const wrapper = mount(ActionDialog, {
      props: {
        modelValue: true,
        title: 'Delegate',
        currentAction: 'delegate',
        formData,
        userOptions: [],
        submitting: false,
      },
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        mocks: { $t: (key: string) => key },
        stubs: {
          LookupField: LookupFieldStub,
          DesignerHelpLink: true,
          ElDialog: {
            template: '<div class="el-dialog"><slot name="header" /><slot /><slot name="footer" /></div>',
          },
          teleport: true,
          transition: false,
        },
      },
    })
    await nextTick()
    await nextTick()

    const lookupHost = wrapper.find('[data-testid="task-action-user-lookup"]')
    expect(lookupHost.exists()).toBe(true)
    const lookup = wrapper.getComponent({ name: 'LookupField' })
    expect(lookup.props('tableId')).toBe(-1_000_000_001)
    expect(lookup.props('searchFields')).toEqual(
      expect.arrayContaining(['id', 'username', 'display_name', 'email', 'employee_id']),
    )
    expect(lookup.props('displayFields')).toEqual(
      ['username', 'display_name', 'full_name', 'email', 'employee_id'],
    )
    expect(lookup.props('viewFields')).toEqual(
      expect.arrayContaining([expect.objectContaining({ fieldName: 'display_name', visible: true })]),
    )
    expect(lookup.props('prefetchLimit')).toBe(200)
    expect(lookup.props('remoteFilter')).toBe(true)

    await lookupHost.find('.stub-lookup').trigger('click')
    expect(formData.targetUserId).toBe('user-b')
    wrapper.unmount()
  })

  it('keeps the original user select on Transfer and does not mount LookupField', async () => {
    const formData = reactive({
      targetUserId: '',
      reason: '',
    })
    const wrapper = mount(ActionDialog, {
      props: {
        modelValue: true,
        title: 'Transfer',
        currentAction: 'transfer',
        formData,
        userOptions: [{ id: 'user-a', name: 'Zhang San', username: 'zhangsan' }],
        submitting: false,
      },
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        mocks: { $t: (key: string, vars?: { name: string; username: string }) =>
          vars ? `${vars.name} (${vars.username})` : key },
        stubs: {
          LookupField: LookupFieldStub,
          DesignerHelpLink: true,
          ElDialog: {
            template: '<div class="el-dialog"><slot name="header" /><slot /><slot name="footer" /></div>',
          },
          teleport: true,
          transition: false,
        },
      },
    })
    await nextTick()
    await nextTick()

    expect(wrapper.find('[data-testid="task-action-user-lookup"]').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'LookupField' }).exists()).toBe(false)
    expect(wrapper.find('.el-select').exists()).toBe(true)
    wrapper.unmount()
  })
})
