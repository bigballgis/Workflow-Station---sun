import type { TaskFormSubmitRequest } from '@/api/processForm'
import type { Ref } from 'vue'

export type SubTableBatchChange = NonNullable<TaskFormSubmitRequest['subTableBatchChanges']>[number]

/** Detach only the submitted generation; edits during the request remain pending. */
export function beginBatchSave(pending: Ref<SubTableBatchChange[]>, payload: Pick<TaskFormSubmitRequest, 'subTableBatchChanges'>): () => void {
  const submitted = payload.subTableBatchChanges ?? []
  if (submitted === pending.value) pending.value = []
  return () => { pending.value = pending.value.reduce(mergeBatchChanges, submitted) }
}

/** One row has one effective membership operation; removed drafts never become server deletions. */
export function mergeBatchChanges(pending: SubTableBatchChange[], change: SubTableBatchChange): SubTableBatchChange[] {
  let next = pending
  let incoming = change
  if (change.operation === 'DELETE') {
    const addedIds = new Set(pending.filter(item => item.bindingId === change.bindingId && item.operation === 'ADD')
      .flatMap(item => item.rowPatches.map(patch => patch.rowId)))
    const removedIds = new Set(change.rowPatches.map(patch => patch.rowId))
    next = pending.map(item => item.bindingId === change.bindingId && item.operation !== 'DELETE'
      ? { ...item, rowPatches: item.rowPatches.filter(patch => !removedIds.has(patch.rowId)) } : item)
      .filter(item => item.rowPatches.length > 0)
    incoming = { ...change, rowPatches: change.rowPatches.filter(patch => !addedIds.has(patch.rowId)) }
  }
  if (!incoming.rowPatches.length) return next
  const matches = (item: SubTableBatchChange): boolean => item.batchControlId === incoming.batchControlId
    && item.bindingId === incoming.bindingId && item.operation === incoming.operation
  const previous = next.find(matches)
  if (!previous) return [...next, incoming]
  const patches = new Map(previous.rowPatches.map(patch => [patch.rowId, patch]))
  for (const patch of incoming.rowPatches) {
    const old = patches.get(patch.rowId)
    patches.set(patch.rowId, { ...old, ...patch,
      ...(old?.fields || patch.fields ? { fields: [...new Set([...(old?.fields ?? []), ...(patch.fields ?? [])])] } : {}),
    })
  }
  return next.map(item => matches(item) ? { ...incoming, rowPatches: [...patches.values()] } : item)
}
