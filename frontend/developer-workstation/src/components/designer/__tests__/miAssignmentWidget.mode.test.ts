import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import MiAssignmentPlaceholderWidget from '../MiAssignmentPlaceholderWidget.vue'
import {
  MI_ASSIGNMENT_CONFIG_KEY,
  MI_ASSIGNMENT_MODE_KEY,
  type AssignmentConfig,
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

/**
 * BPMN configuring only ONE mode ("user" or "role", not "both") must still show
 * BOTH cards — the reader sees the mode was deliberately fixed, not that the
 * block only ever had one option — with the non-configured card locked, not
 * hidden. Previously this rendered a static single-line summary with no cards
 * at all; that behavior must not come back.
 */
describe('MiAssignmentPlaceholderWidget — single-mode contract locks the other card', () => {
  const ROLE_ONLY: AssignmentConfig = {
    allowUser: false, allowRole: true,
    assigneeField: undefined, roleField: 'role_code', buField: 'bu_code',
  }
  const USER_ONLY: AssignmentConfig = {
    allowUser: true, allowRole: false,
    assigneeField: 'assignee', roleField: undefined, buField: undefined,
  }

  const mountLocked = (config: AssignmentConfig, mode: AssignmentMode, setMode = vi.fn()) =>
    mount(MiAssignmentPlaceholderWidget, {
      global: {
        provide: {
          [MI_ASSIGNMENT_CONFIG_KEY as symbol]: ref(config),
          [MI_ASSIGNMENT_MODE_KEY as symbol]: { mode: ref(mode), setMode },
        },
        stubs: { 'el-alert': true },
      },
    })

  it('still renders both cards when only "role" is configured', () => {
    const wrapper = mountLocked(ROLE_ONLY, 'role')
    const cards = wrapper.findAll('.mi-assignment-mode-card')
    expect(cards).toHaveLength(2)
  })

  it('locks the non-configured card (aria-disabled, is-disabled class)', () => {
    const wrapper = mountLocked(ROLE_ONLY, 'role')
    const [personCard, roleCard] = wrapper.findAll('.mi-assignment-mode-card')
    expect(personCard!.attributes('aria-disabled')).toBe('true')
    expect(personCard!.classes()).toContain('is-disabled')
    expect(roleCard!.attributes('aria-disabled')).toBe('false')
    expect(roleCard!.classes()).not.toContain('is-disabled')
  })

  it('does not call setMode when the locked card is clicked', async () => {
    const setMode = vi.fn()
    const wrapper = mountLocked(ROLE_ONLY, 'role', setMode)
    await wrapper.findAll('.mi-assignment-mode-card')[0]!.trigger('click')
    expect(setMode).not.toHaveBeenCalled()
  })

  it('still allows clicking the already-configured card (no-op, same mode)', async () => {
    const setMode = vi.fn()
    const wrapper = mountLocked(USER_ONLY, 'person', setMode)
    await wrapper.findAll('.mi-assignment-mode-card')[0]!.trigger('click')
    expect(setMode).not.toHaveBeenCalled()
  })
})
