import { afterEach, describe, expect, it } from 'vitest'
import { h } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import TaskActionBar from '@/components/tasks/TaskActionBar.vue'
import type { TaskActionInfo } from '@/api/task'

let wrapper: VueWrapper | null = null

const APPROVE_ONLY: TaskActionInfo[] = [{ actionId: 'a1', actionName: 'Approve', actionType: 'APPROVE' } as TaskActionInfo]

function mountActionBar(overrides: {
  isCompletedTask?: boolean
  claimLocked?: boolean
  actions?: TaskActionInfo[] | null
} = {}) {
  wrapper = mount(TaskActionBar, {
    props: {
      isCompletedTask: false,
      actions: APPROVE_ONLY,
      // TaskActionBar 的必填 prop，此前漏传（组件里按它决定是否渲染 Delegate）。
      // 本用例只关心 claim 锁定，故给 false 保持既有行为。
      canDelegate: false,
      getIconComponent: () => h('span'),
      getActionLabel: (a: TaskActionInfo) => a.actionName,
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
  it('shows the configured actions on a task the user may process', () => {
    const w = mountActionBar()
    expect(w.find('.action-section').exists()).toBe(true)
    expect(w.text()).toContain('Approve')
  })

  /** Developer Workstation is the truth: one Action bound to the node means one button. */
  it('renders one button per configured action and nothing else', () => {
    const w = mountActionBar()
    const buttons = w.findAll('.right-actions button').map((b) => b.text())
    expect(buttons).toEqual(['Approve'])
    expect(w.text()).not.toContain('common.save')
  })

  /**
   * The bar has no switch for buttons of its own. The implicit Save came back once through such a
   * switch (removed 2026-05-09, re-enabled 2026-08-21 via a `showImplicitSaveAction` prop). A Save
   * button is a SAVE Action bound to the node in Developer Workstation; widening this list is a
   * deliberate product decision, not a refactor.
   */
  it('offers no prop that adds buttons beyond the configured actions', () => {
    expect(Object.keys(TaskActionBar.props ?? {}).sort()).toEqual([
      'actions',
      'canDelegate',
      'claimLocked',
      'getActionLabel',
      'getIconComponent',
      'isCompletedTask',
    ])
  })

  it('invents no default Approve / Reject when no actions are known', () => {
    const w = mountActionBar({ actions: null })
    expect(w.findAll('.right-actions button')).toHaveLength(0)
    expect(w.text()).not.toContain('task.approve')
    expect(w.text()).not.toContain('task.reject')
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
