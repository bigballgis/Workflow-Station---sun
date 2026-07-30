import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import SubTableBindingSelect from '../SubTableBindingSelect.vue'

vi.mock('../lookupStore', () => ({
  lookupStore: {
    switchToBinding: null,
  },
}))

function mountSelect(modelValue: number | null) {
  const i18n = createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        form: {
          subTableSelectPlaceholder: 'Select Sub Table',
          subTableSelectEmpty: 'No Sub Tables available',
          subTableGoToDesigner: 'Go To Designer',
          subTablePlaceholderStale: 'Binding stale',
          subTableBindingIdLabel: 'Binding ID',
          subTableScriptHideKeyLabel: 'Script hide key',
          subTableScriptHideKeyHint:
            'Use with api.hidden(true, key) in Event scripts — not the Serial Number.',
          subTableBindingIdCopy: 'Copy',
          subTableBindingIdCopied: 'Copied',
          subTableBindingIdCopyFailed: 'Copy failed',
        },
      },
    },
  })
  return mount(SubTableBindingSelect, {
    props: {
      modelValue,
      subBindings: [
        {
          id: 271,
          tableName: 'hmdc_tx',
          tableDisplayName: 'HMDC Transaction',
          tableDescription: '',
          bindingType: 'SUB',
        },
      ],
    },
    global: {
      plugins: [i18n, ElementPlus],
    },
  })
}

describe('SubTableBindingSelect binding ID panel', () => {
  it('shows Binding ID and __subTable_* hide key when bound', () => {
    const wrapper = mountSelect(271)
    expect(wrapper.find('.binding-id-panel').exists()).toBe(true)
    expect(wrapper.text()).toContain('Binding ID')
    expect(wrapper.text()).toContain('271')
    expect(wrapper.text()).toContain('__subTable_271')
    expect(wrapper.text()).toContain('Script hide key')
  })

  it('hides binding ID panel when unbound', () => {
    const wrapper = mountSelect(null)
    expect(wrapper.find('.binding-id-panel').exists()).toBe(false)
  })
})
