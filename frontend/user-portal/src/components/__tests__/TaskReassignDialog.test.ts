import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import TaskReassignDialog from '@/components/tasks/TaskReassignDialog.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

const getUserSummary = vi.hoisted(() => vi.fn(async (id: string) => ({ id, name: `Name ${id}`, username: id })))
vi.mock('@/api/user', () => ({ userApi: { getUserSummary } }))

let wrapper: VueWrapper | null = null

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  getUserSummary.mockClear()
})

async function openDialog(targetUserIds: string[]) {
  wrapper = mount(TaskReassignDialog, {
    props: { modelValue: false, targetUserIds, submitting: false },
    global: { plugins: [ElementPlus], stubs: { PortalHelpLink: true } },
    attachTo: document.body,
  })
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
  return wrapper
}

describe('TaskReassignDialog', () => {
  it('offers exactly the server reassign targets (pool members with Portal entry), deduplicated', async () => {
    const w = await openDialog(['u2', ' u3 ', 'u2'])

    expect(getUserSummary.mock.calls.map(([id]) => id)).toEqual(['u2', 'u3'])
    const options = w.findAllComponents({ name: 'ElOption' }).map((o) => [o.props('value'), o.props('label')])
    expect(options).toEqual([['u2', 'Name u2'], ['u3', 'Name u3']])
  })

  it('says nobody is available when the server lists no target', async () => {
    const w = await openDialog([])

    expect(getUserSummary).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('task.reassignNoCandidates')
    expect(w.findAllComponents({ name: 'ElOption' })).toHaveLength(0)
  })
})
