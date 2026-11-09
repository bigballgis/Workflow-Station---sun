import { afterEach, describe, expect, it } from 'vitest'
import { h } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import TaskActionBar from '@/components/tasks/TaskActionBar.vue'
import type { TaskActionInfo } from '@/api/task'

let wrapper: VueWrapper | null = null

const ACTIONS: TaskActionInfo[] = [
  { actionId: 'a1', actionName: 'Approve', actionType: 'APPROVE' } as TaskActionInfo,
  { actionId: 'a2', actionName: 'Transfer', actionType: 'TRANSFER' } as TaskActionInfo,
  { actionId: 'a3', actionName: 'Delegate', actionType: 'DELEGATE' } as TaskActionInfo,
]

/** Mounts the bar; a flag left out of `flags` is not passed at all, as a caller that does not say. */
function buttons(flags: { canTransfer?: boolean; canDelegate?: boolean }): string[] {
  wrapper = mount(TaskActionBar, {
    props: {
      isCompletedTask: false,
      actions: ACTIONS,
      ...flags,
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

/**
 * The task page passes canTransfer / canDelegate from Portal (D-56, D-60): a task someone else holds can be neither
 * transferred nor delegated by a delegate.
 */
describe('TaskActionBar Transfer and Delegate buttons', () => {
  it('are hidden when the signed-in user may not transfer or delegate the task', () => {
    expect(buttons({ canTransfer: false, canDelegate: false })).toEqual(['Approve'])
  })

  it('show for the holder, or on a task nobody holds', () => {
    expect(buttons({ canTransfer: true, canDelegate: true })).toEqual(['Approve', 'Transfer', 'Delegate'])
  })

  it('each follows its own flag', () => {
    expect(buttons({ canTransfer: true, canDelegate: false })).toEqual(['Approve', 'Transfer'])
  })

  it('are hidden when the page does not say', () => {
    expect(buttons({})).toEqual(['Approve'])
  })
})
