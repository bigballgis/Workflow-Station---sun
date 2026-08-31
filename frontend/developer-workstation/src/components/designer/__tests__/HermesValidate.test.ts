import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import HermesValidate from '../HermesValidate.vue'

function mountValidate(modelValue: unknown[] = []) {
  return mount(HermesValidate, {
    props: { modelValue },
    global: {
      provide: {
        designer: {
          setupState: {
            t: (key: string) => key,
            activeRule: { title: 'Name', _menu: {} },
          },
        },
      },
      stubs: {
        LanguageInput: true,
        FnInput: true,
        ElDropdown: { template: '<div><slot /><slot name="dropdown" /></div>' },
        ElDropdownMenu: { template: '<div><slot /></div>' },
        ElDropdownItem: { template: '<div><slot /></div>' },
        ElRow: { template: '<div><slot /></div>' },
        ElCol: { template: '<div><slot /></div>' },
        ElFormItem: { template: '<div><slot /></div>' },
        ElSelect: { template: '<div><slot /></div>' },
        ElOption: { template: '<div><slot /></div>' },
        ElInput: true,
        ElInputNumber: true,
        ElButton: true,
      },
    },
  })
}

describe('HermesValidate', () => {
  it('emits the new Validation+ row when Error is left empty', () => {
    const wrapper = mountValidate([])
    ;(wrapper.vm as { handleCommand: (mode: string) => void }).handleCommand('email')
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    const payload = emitted![0][0] as Array<Record<string, unknown>>
    expect(payload).toHaveLength(1)
    expect(payload[0]).toMatchObject({
      mode: 'email',
      email: true,
      trigger: 'blur',
      adapter: true,
    })
    expect(payload[0].message).toBeUndefined()
  })

  it('appends a second Validation+ row without requiring Error', () => {
    const wrapper = mountValidate([
      { mode: 'email', email: true, trigger: 'blur', adapter: true },
    ])
    ;(wrapper.vm as { handleCommand: (mode: string) => void }).handleCommand('len')
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    const payload = emitted![0][0] as Array<Record<string, unknown>>
    expect(payload).toHaveLength(2)
    expect(payload[1]).toMatchObject({ mode: 'len', len: 0, trigger: 'blur', adapter: true })
    expect(payload[1].message).toBeUndefined()
  })
})
