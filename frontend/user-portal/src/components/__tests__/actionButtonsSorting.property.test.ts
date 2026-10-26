import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import TaskActionBar from '@/components/tasks/TaskActionBar.vue'
import type { TaskActionInfo } from '@/api/task'

let wrapper: VueWrapper | undefined
const actions: TaskActionInfo[] = [
  { actionId: '35', actionName: 'Reject', actionType: 'REJECT', configJson: '{"sortOrder":999}' },
  { actionId: '12', actionName: 'Save Draft', actionType: 'SAVE', configJson: '{"sortOrder":0}' },
  { actionId: '22', actionName: 'Approve', actionType: 'APPROVE', configJson: '{"sortOrder":1}' },
]
function render(items: TaskActionInfo[], canDelegate = true) {
  wrapper = mount(TaskActionBar, {
    props: { actions: items, isCompletedTask: false, canDelegate, getIconComponent: () => null, getActionLabel: a => a.actionName },
    global: { plugins: [ElementPlus], mocks: { $t: (k: string) => k, $router: { back() {} } } },
  })
  return wrapper.findAll('.right-actions button').map(button => button.text())
}

afterEach(() => { wrapper?.unmount(); localStorage.clear() })
describe('TaskActionBar preserves node binding order', () => {
  it('renders the supplied node sequence and ignores historical action-level sortOrder', () => {
    expect(render(actions)).toEqual(['Reject', 'Save Draft', 'Approve'])
  })
  it('supports another node using the same definitions in a different order', () => {
    expect(render([actions[1], actions[2], actions[0]])).toEqual(['Save Draft', 'Approve', 'Reject'])
  })
  it('keeps survivor order after role and delegation filtering', () => {
    localStorage.setItem('ws_up_user', JSON.stringify({ roles: ['reviewer'] }))
    expect(render([
      actions[0],
      { actionId: 'hidden', actionName: 'Restricted', actionType: 'SAVE', configJson: '{"allowedRoles":["finance"]}' },
      { actionId: 'delegate', actionName: 'Delegate', actionType: 'DELEGATE' },
      actions[1], actions[2],
    ], false)).toEqual(['Reject', 'Save Draft', 'Approve'])
  })
  it('renders no actions for an explicitly empty binding list', () => {
    expect(render([])).toEqual([])
  })
})
