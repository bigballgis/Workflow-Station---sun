import { afterEach, describe, expect, it } from 'vitest'
import { h } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import TaskActionBar from '@/components/tasks/TaskActionBar.vue'
import type { TaskActionInfo } from '@/api/task'

let wrapper: VueWrapper | null = null

const ACTIONS: TaskActionInfo[] = [
  { actionId: 'a1', actionName: 'Approve', actionType: 'APPROVE' } as TaskActionInfo,
  { actionId: 'a2', actionName: 'Delegate', actionType: 'DELEGATE' } as TaskActionInfo,
]

function buttons(canDelegate: boolean): string[] {
  wrapper = mount(TaskActionBar, {
    props: {
      isCompletedTask: false,
      actions: ACTIONS,
      canDelegate,
      getIconComponent: () => h('span'),
      getActionLabel: (a: TaskActionInfo) => a.actionName,
    },
    global: {
      plugins: [ElementPlus],
      mocks: { $t: (key: string) => key, $router: { back: () => {} } },
    },
  })
  return wrapper.findAll('.right-actions button').map((b) => b.text())
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

/** The task page passes canDelegate = the engine's `delegable` (D-56): Delegate shows only where it works. */
describe('TaskActionBar Delegate button', () => {
  it('is hidden on a task that cannot be delegated', () => {
    expect(buttons(false)).toEqual(['Approve'])
  })

  it('shows on a task that can be delegated', () => {
    expect(buttons(true)).toEqual(['Approve', 'Delegate'])
  })
})
