import { afterEach, expect, it } from 'vitest'
import { reactive } from 'vue'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import ApproveDialog from '@/components/tasks/ApproveDialog.vue'

let wrapper: VueWrapper | undefined
afterEach(() => { wrapper?.unmount(); wrapper = undefined })
it.each(['PENDING_SYNC', 'SYNC_CONFLICT', 'DELIVERY_UNKNOWN', 'READY'] as const)(
  'retains input and blocks a second confirmation in %s while allowing a status query', async state => {
    const form = reactive({ comment: 'Original entered comment' })
    const i18n = createI18n({ legacy: false, locale: 'en', missingWarn: false, fallbackWarn: false, messages: { en: {
      common: { cancel: 'Cancel', confirm: 'Confirm' }, task: { comment: 'Comments', commentPlaceholder: 'Comments', assignmentCompletion: {
        check: 'Check status', operationId: 'Operation ID', pending: 'Synchronizing', conflict: 'Synchronization conflict',
        unknown: 'Result not confirmed', ready: 'Original submission complete', notAccepted: 'No accepted record found',
      } },
    } } })
    wrapper = mount(ApproveDialog, { attachTo: document.body, props: { modelValue: true, title: 'Approve', formData: form, submitting: false,
      completion: { operationId: '00000000-0000-4000-8000-000000000001', state } },
    global: { plugins: [ElementPlus, i18n], stubs: { teleport: true, transition: false,
      ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue" role="dialog"><slot /><slot name="footer" /></div>' },
    } } })
    await flushPromises()
    const confirm = wrapper.find('button.el-button--primary')
    expect(confirm.exists(), wrapper.html()).toBe(true)
    expect((confirm.element as HTMLButtonElement).disabled).toBe(true)
    await confirm.trigger('click')
    expect(wrapper.emitted('confirm')).toBeUndefined()
    await wrapper.find('.completion-status button').trigger('click')
    expect(wrapper.emitted('refresh-completion')).toHaveLength(1)
    expect(form.comment).toBe('Original entered comment')
    await wrapper.setProps({ completion: { operationId: '00000000-0000-4000-8000-000000000001', state: 'NOT_ACCEPTED' } })
    expect((confirm.element as HTMLButtonElement).disabled).toBe(false)
    await confirm.trigger('click')
    expect(wrapper.emitted('confirm')).toHaveLength(1)
    await wrapper.setProps({ modelValue: false })
    await flushPromises()
    expect(wrapper.find('[data-testid="completion-operation-status"]').exists()).toBe(true)
    expect(form.comment).toBe('Original entered comment')
  },
)
