import { computed, onBeforeUnmount, reactive, watch, type Ref } from 'vue'
import type { BatchAssignmentDirectory, BatchUserOption } from './assignmentDirectoryTypes'

/** Label hydration is display-only: never mutate the draft or its user IDs. */
export function useSelectedAssignmentUsers(directory: BatchAssignmentDirectory, selectedIds: Ref<string[]>) {
  const resolved = reactive(new Map<string, BatchUserOption>())
  const pending = new Set<string>()
  let disposed = false
  const known = computed(() => {
    const users = new Map(resolved)
    for (const options of Object.values(directory.userOptions)) {
      for (const user of options) users.set(user.id, user)
    }
    return users
  })
  function userOptionsFor(key: string, selectedId: string): BatchUserOption[] {
    const options = directory.userOptions[key] ?? []
    const selected = known.value.get(selectedId)
    return selected && !options.some(user => user.id === selectedId) ? [selected, ...options] : options
  }
  watch(selectedIds, async ids => {
    // Persist known selections before another remote search replaces its options.
    for (const id of ids) {
      const user = known.value.get(id)
      if (user) resolved.set(id, user)
    }
    const missing = [...new Set(ids)].filter(id => id && !resolved.has(id) && !pending.has(id))
    if (!missing.length) return
    missing.forEach(id => pending.add(id))
    try {
      const batches = Array.from({ length: Math.ceil(missing.length / 200) }, (_, index) => missing.slice(index * 200, (index + 1) * 200))
      const users = (await Promise.all(batches.map(ids => directory.loadUsers(ids)))).flat()
      if (disposed) return
      users.forEach(user => resolved.set(user.id, user))
      if (missing.some(id => !resolved.has(id))) throw new Error('common.operationFailed')
    } catch (cause) { if (!disposed) directory.report(cause) }
    finally { missing.forEach(id => pending.delete(id)) }
  }, { immediate: true })
  onBeforeUnmount(() => { disposed = true })
  return { userOptionsFor }
}
