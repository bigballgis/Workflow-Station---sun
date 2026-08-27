import { afterEach, describe, expect, it } from 'vitest'
import { h } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import TaskActionBar from '@/components/tasks/TaskActionBar.vue'

let wrapper: VueWrapper | null = null

function mountActionBar(overrides: { isCompletedTask?: boolean; claimLocked?: boolean } = {}) {
  wrapper = mount(TaskActionBar, {
    props: {
      isCompletedTask: false,
      showImplicitSaveAction: true,
      savingTaskForm: false,
      actions: null,
      getButtonType: () => 'primary',
      getIconComponent: () => h('span'),
      getActionLabel: () => 'action',
      ...overrides,
    },
    global: {
      plugins: [ElementPlus],
      mocks: { $t: (key: string) => key, $router: { back: () => {} } },
    },
  })
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

describe('TaskActionBar claim lock', () => {
  it('shows approve, reject and save on a task the user may process', () => {
    const w = mountActionBar()
    expect(w.find('.action-section').exists()).toBe(true)
    expect(w.text()).toContain('task.approve')
  })

  /**
   * The whole point of Hold: another member of the BU Role can open the request but must not be
   * able to submit, delegate or urge it while a colleague is editing.
   */
  it('hides every action while another member of the role holds the request', () => {
    const w = mountActionBar({ claimLocked: true })
    expect(w.find('.action-section').exists()).toBe(false)
    expect(w.text()).not.toContain('task.approve')
    expect(w.text()).not.toContain('task.delegate')
    expect(w.text()).not.toContain('common.save')
  })
})
