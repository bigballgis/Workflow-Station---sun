import { computed, getCurrentScope, onScopeDispose, ref, watch } from 'vue'

/** Save is allowed only after every scheduled canvas for the current selection has loaded. */
export function useDesignerHydration() {
  let generation = 0
  const pending = ref(0)
  const failed = ref(false)
  const mountedWaiters = new Set<() => void>()
  function cancelWaiters(): void {
    mountedWaiters.forEach(stop => stop())
    mountedWaiters.clear()
  }
  if (getCurrentScope()) onScopeDispose(cancelWaiters)
  const blocked = computed(() => pending.value > 0 || failed.value)
  function begin(): number {
    cancelWaiters()
    generation++
    pending.value = 1 // Metadata loading owns the initial ticket.
    failed.value = false
    return generation
  }
  const current = (token: number): boolean => token === generation
  function finish(token: number): void {
    if (current(token) && pending.value > 0) pending.value--
  }
  function track(token: number): () => void {
    if (current(token)) pending.value++
    let done = false
    return () => {
      if (!done) finish(token)
      done = true
    }
  }
  function fail(token: number): void { if (current(token)) failed.value = true }
  function whenMounted<T>(token: number, source: () => T | null | undefined, load: () => void): void {
    if (!current(token)) return
    if (source()) { load(); return }
    const stop = watch(source, value => {
      if (!value) return
      stop()
      mountedWaiters.delete(stop)
      if (current(token)) load()
    }, { flush: 'post' })
    mountedWaiters.add(stop)
  }
  return { blocked, begin, current, finish, track, fail, whenMounted, token: () => generation }
}

export type DesignerHydration = ReturnType<typeof useDesignerHydration>
