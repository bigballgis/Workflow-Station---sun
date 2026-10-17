import { computed, ref, shallowRef, watch } from 'vue'
import type { ComputedRef, Ref } from 'vue'

interface BatchCanvas {
  getRule?: () => unknown[]
  getOption?: () => Record<string, unknown>
  setRule?: (rule: unknown[]) => void
  setOption?: (options: Record<string, unknown>) => void
  activeModule?: string
}

export interface BatchCanvasDraft {
  rule: unknown[]
  options: Record<string, unknown>
}

type BatchCanvasRef = (canvas: BatchCanvas | null) => void
interface BatchDesignerDrafts {
  refs: ComputedRef<Array<BatchCanvas | undefined>>
  cache: Ref<Record<number, BatchCanvasDraft>>
  visited: Ref<Set<number>>
  failed: ComputedRef<boolean>
  register: (bindingId: number, canvas: BatchCanvas | null) => void
  reset: () => void
  refFor: (bindingId: number) => BatchCanvasRef
}

/** A batch canvas belongs to a form/binding, not the current array slot. */
interface BatchDraftOptions {
  formId: () => number | null | undefined
  bindingIds: () => number[]
  collect: (canvas: BatchCanvas) => BatchCanvasDraft
  reportFailure: (error: unknown) => void
}

export function useBatchDesignerDrafts(options: BatchDraftOptions): BatchDesignerDrafts {
  const owners = new Map<number, symbol>()
  const canvases = shallowRef<Record<number, BatchCanvas>>({})
  const cache = ref<Record<number, BatchCanvasDraft>>({})
  const visited = ref(new Set<number>())
  const failedBindings = ref(new Set<number>())
  const failed = computed(() => failedBindings.value.size > 0)
  const refs = computed(() => options.bindingIds().map(bindingId => canvases.value[bindingId]))

  function reset(): void {
    owners.clear()
    canvases.value = {}
    cache.value = {}
    visited.value = new Set()
    failedBindings.value = new Set()
  }
  watch(options.formId, reset, { flush: 'sync' })
  const refFor = (bindingId: number): BatchCanvasRef => createOwnedRef(bindingId, owners, register)

  function register(bindingId: number, canvas: BatchCanvas | null): void {
    // Old form unmount callbacks must not clear or poison a new form's state.
    if (options.formId() == null || !options.bindingIds().includes(bindingId)) return
    if (canvas === canvases.value[bindingId]) return
    if (canvas) { canvases.value = { ...canvases.value, [bindingId]: canvas }; return }
    const previous = canvases.value[bindingId]
    if (!previous) return
    if (visited.value.has(bindingId)) {
      try { cache.value[bindingId] = options.collect(previous) }
      catch (error) { failedBindings.value.add(bindingId); options.reportFailure(error) }
    }
    const next = { ...canvases.value }
    delete next[bindingId]
    canvases.value = next
  }

  return { refs, cache, visited, failed, register, reset, refFor }
}

function createOwnedRef(bindingId: number, owners: Map<number, symbol>, register: BatchDesignerDrafts['register']): BatchCanvasRef {
  const owner = Symbol()
  owners.set(bindingId, owner)
  return (canvas: BatchCanvas | null) => { if (owners.get(bindingId) === owner) register(bindingId, canvas) }
}
