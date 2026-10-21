import { describe, it, expect } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createI18n } from 'vue-i18n'
import { createPinia } from 'pinia'
import FormRenderer from '../FormRenderer.vue'

const global = { plugins: [createPinia(), ElementPlus, createI18n({ legacy: false, locale: 'en', messages: { en: {} }, missingWarn: false, fallbackWarn: false })] }
const field = (key: string) => ({ key, label: key, type: 'text' })

describe('FormRenderer uses published task permissions for main fields', () => {
  it('locks the old release field and restores editing when the new context allows it', async () => {
    const wrapper = mount(FormRenderer, { props: {
      fields: [field('protected_value'), field('ordinary_value')],
      modelValue: { protected_value: 'original', ordinary_value: 'editable' },
      fieldPermissions: { protected_value: 'READONLY', '77:ordinary_value': 'READONLY' },
    }, global })
    try {
      await flushPromises()
      const inputs = wrapper.findAll<HTMLInputElement>('input')
      expect(inputs).toHaveLength(2)
      expect(inputs[0].element.disabled || inputs[0].element.readOnly).toBe(true)
      expect(inputs[1].element.disabled || inputs[1].element.readOnly).toBe(false)
      await wrapper.setProps({ fieldPermissions: {} })
      expect(inputs[0].element.disabled || inputs[0].element.readOnly).toBe(false)
    } finally { wrapper.unmount() }
  })

  it('applies permissions in tabs and after tabs without unlocking designer readonly fields', async () => {
    const wrapper = mount(FormRenderer, { props: {
      fields: [], tabs: [{ name: 'main', label: 'Main', fields: [field('tab_value')] }],
      fieldsAfterTabs: [{ ...field('designer_value'), readonly: true }, field('after_value')],
      fieldPermissions: { tab_value: 'READONLY', after_value: 'READONLY', designer_value: 'EDITABLE' },
    }, global })
    try {
      await flushPromises()
      const inputs = wrapper.findAll<HTMLInputElement>('input')
      expect(inputs).toHaveLength(3)
      expect(inputs.every(input => input.element.disabled || input.element.readOnly)).toBe(true)
    } finally { wrapper.unmount() }
  })
})
