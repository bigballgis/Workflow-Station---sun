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

interface DesignerSubBinding {
  id: number
  tableName: string
  tableDisplayName?: string
  tableDescription: string
  bindingType: string
}

function mountSelect(modelValue: number | null, subBindings: DesignerSubBinding[]) {
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
    props: { modelValue, subBindings },
    global: {
      plugins: [i18n, ElementPlus],
    },
  })
}

/**
 * ACTION bindings (FORM_POPUP 弹窗写入的记录表，如 "Meeting Remark") render through the same
 * subTable canvas widget as SUB bindings — see FormDesigner.vue componentRule.subTable and
 * SubTableField.vue for the read-only enforcement. This selector must offer them, not just SUB.
 */
describe('SubTableBindingSelect — ACTION binding selectability', () => {
  const actionBinding = {
    id: 305,
    tableName: 'meeting_remark',
    tableDisplayName: 'Meeting Remark',
    tableDescription: '',
    bindingType: 'ACTION',
  }

  it('accepts an ACTION binding as a valid (non-stale) selection', () => {
    const wrapper = mountSelect(305, [actionBinding])

    expect(wrapper.text()).not.toContain('Binding stale')
  })

  it('still flags a SUB-only stale binding when only an ACTION binding is available', () => {
    // Guards the filter itself: 999 exists in neither SUB nor ACTION bindings, so it must
    // stay flagged stale even though the pool now includes ACTION-typed entries.
    const wrapper = mountSelect(999, [actionBinding])

    expect(wrapper.text()).toContain('Binding stale')
  })
})
