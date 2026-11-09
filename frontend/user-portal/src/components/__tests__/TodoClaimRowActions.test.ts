import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import TodoClaimRowActions from '@/components/tasks/TodoClaimRowActions.vue'
import TodoAssigneeCell from '@/components/tasks/TodoAssigneeCell.vue'
import type { TaskInfo } from '@/api/task'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

let wrapper: VueWrapper | null = null

function task(overrides: Partial<TaskInfo>): TaskInfo {
  return {
    taskId: 't1',
    assignee: 'alice',
    assigneeName: 'Alice Chen',
    claimPoolTask: true,
    ...overrides,
  } as TaskInfo
}

function mountRow(overrides: Partial<TaskInfo>) {
  wrapper = mount(TodoClaimRowActions, {
    props: { task: task(overrides), loading: false },
    global: { plugins: [ElementPlus] },
  })
  return wrapper
}

function mountCell(overrides: Partial<TaskInfo>) {
  wrapper = mount(TodoAssigneeCell, {
    props: { task: task(overrides) },
    global: { plugins: [ElementPlus] },
  })
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

describe('TodoClaimRowActions — holder lost Portal entry', () => {
  it('offers Take over, with Release Claim and Reassign under More, when the user may take the task over', async () => {
    const w = mountRow({ assigneeLostPortalEntry: true, canTakeOver: true, canForceUnclaim: true, canReassign: true })

    expect(w.find('[data-test="todo-take-over-btn"]').text()).toBe('task.takeOver')
    expect(w.find('[data-test="todo-more-btn"]').text()).toBe('common.more')
    expect(w.find('[data-test="todo-reassign-btn"]').exists()).toBe(false)
    await w.find('[data-test="todo-take-over-btn"]').trigger('click')
    expect(w.emitted('take-over')?.[0]?.[0]).toMatchObject({ taskId: 't1' })

    const more = w.findComponent({ name: 'ElDropdown' })
    more.vm.$emit('command', 'force-unclaim')
    more.vm.$emit('command', 'reassign')
    expect(w.emitted('force-unclaim')?.[0]?.[0]).toMatchObject({ taskId: 't1' })
    expect(w.emitted('reassign')?.[0]?.[0]).toMatchObject({ taskId: 't1' })
  })

  it('lists under More only the actions the user may take', async () => {
    const w = mountRow({ assigneeLostPortalEntry: true, canTakeOver: true, canForceUnclaim: true, canReassign: false })

    await w.find('[data-test="todo-more-btn"]').trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 0))
    const items = document.body.querySelectorAll('[data-test^="todo-more-"]')
    const tests = Array.from(items).map((el) => el.getAttribute('data-test'))
    expect(tests).toContain('todo-more-force-unclaim')
    expect(tests).not.toContain('todo-more-reassign')
  })

  it('shows no Take over without canTakeOver, even when the holder lost entry', () => {
    const w = mountRow({ assigneeLostPortalEntry: true, canForceUnclaim: true })

    expect(w.find('[data-test="todo-take-over-btn"]').exists()).toBe(false)
    expect(w.find('[data-test="todo-force-unclaim-btn"]').exists()).toBe(true)
  })

  it('keeps Claim / Unclaim ahead of Take over', () => {
    const w = mountRow({ claimedByCurrentUser: true, canTakeOver: true })

    expect(w.find('[data-test="todo-unclaim-btn"]').exists()).toBe(true)
    expect(w.find('[data-test="todo-take-over-btn"]').exists()).toBe(false)
  })
})

describe('TodoAssigneeCell', () => {
  it('flags a holder who lost Portal entry next to the holder name', () => {
    const w = mountCell({ assigneeLostPortalEntry: true })

    expect(w.find('[data-test="todo-current-assignee"]').text()).toBe('Alice Chen')
    expect(w.find('[data-test="todo-assignee-lost-entry"]').text()).toBe('task.assigneeLostPortalEntry')
  })

  it('shows no flag while the holder still has Portal entry', () => {
    const w = mountCell({ assigneeLostPortalEntry: false })

    expect(w.find('[data-test="todo-current-assignee"]').text()).toBe('Alice Chen')
    expect(w.find('[data-test="todo-assignee-lost-entry"]').exists()).toBe(false)
  })

  it('still says "You" for my own hold', () => {
    const w = mountCell({ claimedByCurrentUser: true })

    expect(w.find('[data-test="todo-current-assignee"]').text()).toBe('task.claimedByMe')
  })
})
