import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import MiAssignmentPlaceholderWidget from '../MiAssignmentPlaceholderWidget.vue'
import {
  MI_ASSIGNMENT_CONFIG_KEY,
  MI_ASSIGNMENT_MODE_KEY,
  type AssignmentMode,
} from '@/utils/miAssignmentConfig'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (k: string) => k }) }
})

const CONFIG = {
  allowUser: true, allowRole: true,
  assigneeField: 'assignee', roleField: 'role_code', buField: 'bu_code',
}

/**
 * form-create forwards NOTHING from rule.props to components whose drag rule declares
 * `input: false` (verified at runtime: the widget saw only onFc.updateValue / onFc.el /
 * class / id / style). Mode and setter therefore travel via provide/inject — these tests
 * lock that contract so it can't regress to a prop/emit-based one that silently no-ops.
 */
describe('MiAssignmentPlaceholderWidget — mode wiring via inject', () => {
  const mountWidget = (mode: AssignmentMode, setMode = vi.fn(), slot?: string) =>
    mount(MiAssignmentPlaceholderWidget, {
      slots: slot ? { default: slot } : {},
      global: {
        provide: {
          [MI_ASSIGNMENT_CONFIG_KEY as symbol]: ref(CONFIG),
          [MI_ASSIGNMENT_MODE_KEY as symbol]: { mode: ref(mode), setMode },
        },
        stubs: { 'el-alert': true },
      },
    })

  it('reflects the injected active mode', () => {
    expect(mountWidget('person').findAll('.mi-assignment-mode-card')[0]!
      .attributes('aria-checked')).toBe('true')
    expect(mountWidget('role').findAll('.mi-assignment-mode-card')[1]!
      .attributes('aria-checked')).toBe('true')
  })

  it('calls the injected setMode when the other card is clicked', async () => {
    const setMode = vi.fn()
    const wrapper = mountWidget('person', setMode)
    await wrapper.findAll('.mi-assignment-mode-card')[1]!.trigger('click')
    expect(setMode).toHaveBeenCalledWith('role')
  })

  it('does not re-fire for the already-active mode', async () => {
    const setMode = vi.fn()
    const wrapper = mountWidget('person', setMode)
    await wrapper.findAll('.mi-assignment-mode-card')[0]!.trigger('click')
    expect(setMode).not.toHaveBeenCalled()
  })

  it('renders its nested fields in the slot', () => {
    const wrapper = mountWidget('person', vi.fn(), '<div class="probe-child">field</div>')
    expect(wrapper.find('.mi-assignment-widget__fields .probe-child').exists()).toBe(true)
  })
})
