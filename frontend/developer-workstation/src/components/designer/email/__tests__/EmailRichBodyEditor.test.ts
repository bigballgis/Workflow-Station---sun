import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { defineComponent } from 'vue'
import EmailRichBodyEditor from '../EmailRichBodyEditor.vue'

const PARSED_HTML = '<table><tbody><tr><td>flattened</td></tr></tbody></table>'

vi.mock('@wangeditor/editor-for-vue', () => {
  const Toolbar = defineComponent({ name: 'Toolbar', template: '<div />' })
  const Editor = defineComponent({
    name: 'Editor',
    emits: ['onCreated', 'onChange'],
    mounted() {
      this.$emit('onCreated', {
        getHtml: () => PARSED_HTML,
        destroy: () => undefined,
        restoreSelection: () => undefined,
        insertText: () => undefined,
      })
    },
    template: '<div />',
  })
  return { Toolbar, Editor }
})

vi.mock('@/composables/email/useEmailTemplateVariables', () => ({
  useEmailTemplateVariables: () => ({
    groups: { value: [] },
    loading: { value: false },
    load: vi.fn().mockResolvedValue(undefined),
  }),
  resolveEmailVariableGroupLabel: (label: string) => label,
}))

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  messages: {
    en: {
      emailTemplate: {
        insertVariable: 'Insert Variable',
        insertVariableHint: 'hint',
        bodyPlaceholder: 'body',
      },
    },
  },
})

describe('EmailRichBodyEditor', () => {
  it('pushes getHtml into the model after Visual parse so preview can catch up', async () => {
    const wrapper = mount(EmailRichBodyEditor, {
      props: {
        modelValue: '<style>.x{color:red}</style><table class="x"><tr><td>A</td></tr></table>',
        functionUnitId: 1,
      },
      global: {
        plugins: [i18n],
        stubs: {
          'el-select': { template: '<div />' },
          'el-option-group': { template: '<div />' },
          'el-option': { template: '<div />' },
        },
      },
    })
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([PARSED_HTML])
    wrapper.unmount()
  })
})
