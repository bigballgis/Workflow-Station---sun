import { shallowMount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import HermesEventConfig from '../HermesEventConfig.vue'
import HermesFnConfig from '../HermesFnConfig.vue'
import { ensureEmptyRuleComponentEvents, emptyFormLevelEventFunction } from '@/utils/formCreateDefaultEvents'

vi.mock('@form-create/designer/src/components/FnEditor.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/i18n', () => ({ default: { global: { t: (key: string) => key } } }))

function componentEditor() {
  const rule = reactive<Record<string, any>>({ type: 'lookup', field: 'person', _fc_id: 'test',
    _on: { change: '$FNX:api.setValue("email", value.email)' } })
  ensureEmptyRuleComponentEvents(rule)
  const wrapper = shallowMount(HermesEventConfig, {
    props: { modelValue: rule._on, eventName: ['change', 'blur', 'focus'] },
    global: { provide: { designer: { setupState: { activeRule: rule, t: (key: string) => key } } } },
  })
  return { wrapper, vm: wrapper.vm as any, rule }
}

describe('designer event drafts', () => {
  it('counts and opens only configured scripts despite seeded defaults and reselection', async () => {
    const { vm, rule, wrapper } = componentEditor()
    expect(vm.eventNum).toBe(1)
    vm.visible = true
    await nextTick()
    expect(Object.keys(vm.event)).toEqual(['change'])
    vm.visible = false
    await nextTick()
    ensureEmptyRuleComponentEvents(rule)
    expect(vm.eventNum).toBe(1)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it.each(['x', 'cancel'])('discards saved draft additions and deletions on %s', async close => {
    const { vm, rule, wrapper } = componentEditor()
    const before = JSON.stringify(rule)
    vm.visible = true
    await nextTick()
    vm.destroy()
    vm.event = { hook_load: ['api.setValue("email", "draft")'] }
    if (close === 'cancel') vm.cancelDialog()
    else vm.visible = false
    await nextTick()
    expect(JSON.stringify(rule)).toBe(before)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('applies scripts once on OK and keeps empty templates out of stored handlers', async () => {
    const { vm, rule, wrapper } = componentEditor()
    vm.visible = true
    await nextTick()
    vm.destroy()
    vm.event = { blur: ['api.setValue("email", "ok")'], hook_load: [''], hook_value: ['api.hidden(true, "email")'] }
    vm.submit()
    await nextTick()
    expect(rule._on).toEqual({ blur: '$FNX:api.setValue("email", "ok")' })
    expect(rule._hook).toEqual({ value: '$FNX:api.hidden(true, "email")' })
    expect(wrapper.emitted('update:modelValue')).toHaveLength(1)
  })

  it('form events count actual bodies rather than empty wrapped functions', () => {
    const wrapper = shallowMount(HermesFnConfig, {
      props: { modelValue: {
        onMounted: emptyFormLevelEventFunction('onMounted', 'api'),
        onChange: emptyFormLevelEventFunction('onChange', 'field, value', 'api.setValue("email", value)'),
      } },
      global: { provide: { designer: { setupState: { t: (key: string) => key } } } },
    })
    expect((wrapper.vm as any).eventNum).toBe(1)
  })
})
