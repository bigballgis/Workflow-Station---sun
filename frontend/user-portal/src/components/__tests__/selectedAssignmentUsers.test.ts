import { flushPromises, mount } from '@vue/test-utils'
import { computed, defineComponent, reactive, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useSelectedAssignmentUsers } from '@platform-shared/batch/useSelectedAssignmentUsers'
import type { BatchAssignmentDirectory } from '@platform-shared/batch/assignmentDirectoryTypes'

const alice = { id: 'user-A', username: 'alice', name: 'Alice Example' }
function setup(ids: string[]) {
  const selected = ref(ids)
  const directory: BatchAssignmentDirectory = {
    error: ref(''), businessUnits: ref([]), userOptions: reactive({}), userLoading: reactive({}),
    loadUsers: vi.fn().mockResolvedValue([alice]), searchUsers: vi.fn(), report: vi.fn(),
    loadBusinessUnits: vi.fn(), loadRoles: vi.fn(), requireBusinessUnitCode: vi.fn(), requireBusinessUnitId: vi.fn(),
  }
  let result!: ReturnType<typeof useSelectedAssignmentUsers>
  const wrapper = mount(defineComponent({ setup() {
    result = useSelectedAssignmentUsers(directory, computed(() => selected.value))
    return () => null
  } }))
  return { wrapper, directory, selected, options: (key = 'row:1', id = 'user-A') => result.userOptionsFor(key, id) }
}
describe('selected assignment user labels', () => {
  it('keeps General lists within the directory endpoint batch limit', async () => {
    const test = setup([])
    vi.mocked(test.directory.loadUsers).mockImplementation(async ids => ids.map(id => ({ id, username: id, name: `Name ${id}` })))
    test.selected.value = Array.from({ length: 201 }, (_, index) => `person-${index}`)
    await flushPromises()
    expect(vi.mocked(test.directory.loadUsers).mock.calls.map(([ids]) => ids.length)).toEqual([200, 1])
    expect(test.options('general', 'person-200')[0]?.name).toBe('Name person-200')
    test.wrapper.unmount()
  })
  it('loads distinct saved IDs in a batch and retains labels when searching has no matches', async () => {
    const test = setup(['user-A', 'user-A'])
    await flushPromises()
    expect(test.directory.loadUsers).toHaveBeenCalledTimes(1)
    expect(test.directory.loadUsers).toHaveBeenCalledWith(['user-A'])
    test.directory.userOptions['row:1'] = []
    expect(test.options()).toEqual([alice])
    test.selected.value = []
    await flushPromises()
    test.selected.value = ['user-A']
    await flushPromises()
    expect(test.options('uniform')).toEqual([alice])
    expect(test.directory.loadUsers).toHaveBeenCalledTimes(1)
    test.wrapper.unmount()
  })
  it('preserves a new search selection before another search replaces the results', async () => {
    const test = setup([])
    test.directory.userOptions.uniform = [alice]
    test.selected.value = ['user-A']
    await flushPromises()
    test.directory.userOptions.uniform = []
    expect(test.options()).toEqual([alice])
    expect(test.directory.loadUsers).not.toHaveBeenCalled()
    test.wrapper.unmount()
  })
  it('exposes missing directory identities rather than changing the stored ID', async () => {
    const test = setup([])
    vi.mocked(test.directory.loadUsers).mockResolvedValue([])
    test.selected.value = ['missing']
    await flushPromises()
    expect(test.directory.report).toHaveBeenCalledWith(expect.any(Error))
    expect(test.selected.value).toEqual(['missing'])
    test.wrapper.unmount()
  })
})
