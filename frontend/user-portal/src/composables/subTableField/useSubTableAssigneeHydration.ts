import { ref, watch, type Ref } from 'vue'
import { extractUserIdFromCellValue } from '@/components/subTableAddDialogHelpers'
import { userApi } from '@/api/user'
import type { SubTableFieldEmit, SubTableFieldProps } from './subTableFieldTypes'
import { resolveDisplayNameFromAssigneeCell } from './useSubTableAssignment'

/** Align sub-table assignee column with task detail: resolve display names when only IDs are stored (e.g. completed tasks). */
export function useSubTableAssigneeHydration(
  props: SubTableFieldProps,
  rows: Ref<any[]>,
  emit: SubTableFieldEmit,
  deps: {
    userNameCache: Ref<Record<string, string>>
    resolveRowAssigneeCell: (row: Record<string, unknown> | null | undefined) => unknown
  },
) {
  const { userNameCache, resolveRowAssigneeCell } = deps

  const assigneeDisplayHydrateGeneration = ref(0)
  let assigneeDisplayHydrateTimer: ReturnType<typeof setTimeout> | null = null

  function scheduleHydrateAssigneeDisplayNames() {
    const af = props.assigneeField
    if (!af) return
    if (assigneeDisplayHydrateTimer) clearTimeout(assigneeDisplayHydrateTimer)
    assigneeDisplayHydrateTimer = setTimeout(() => {
      assigneeDisplayHydrateTimer = null
      void hydrateAssigneeDisplayNamesFromUserDirectory()
    }, 200)
  }

  async function hydrateAssigneeDisplayNamesFromUserDirectory() {
    const af = props.assigneeField
    if (!af || !rows.value.length) return
    const gen = ++assigneeDisplayHydrateGeneration.value

    let changed = false
    let next = rows.value.map(r => {
      if (!r || typeof r !== 'object') return r
      const rec = r as Record<string, unknown>
      const rawAssignee = resolveRowAssigneeCell(rec)
      const sid = extractUserIdFromCellValue(rawAssignee)
      if (!sid) return r
      const fromCell = resolveDisplayNameFromAssigneeCell(rawAssignee)
      if (fromCell) {
        const existing = r.assignee_display_name
        if (existing !== fromCell) {
          changed = true
          userNameCache.value = { ...userNameCache.value, [sid]: fromCell }
          return { ...r, assignee_display_name: fromCell }
        }
        return r
      }
      const existing = r.assignee_display_name
      if (existing != null && String(existing).trim() !== '') return r
      const cached = userNameCache.value[sid]
      if (!cached) return r
      changed = true
      return { ...r, assignee_display_name: cached }
    })
    if (changed) {
      rows.value = next
      emit('update:modelValue', [...next])
    }

    const idsToFetch = [...new Set(
      rows.value
        .map(r => (r && typeof r === 'object' ? extractUserIdFromCellValue(resolveRowAssigneeCell(r as Record<string, unknown>)) : ''))
        .filter(s => s.length > 0)
    )].filter(sid => {
      const row = rows.value.find(
        r => r && extractUserIdFromCellValue(resolveRowAssigneeCell(r as Record<string, unknown>)) === sid
      )
      if (!row) return false
      const hasName = row.assignee_display_name != null && String(row.assignee_display_name).trim() !== ''
      if (hasName) return false
      return !userNameCache.value[sid]
    })

    if (idsToFetch.length === 0) return

    await Promise.all(
      idsToFetch.map(async sid => {
        try {
          const info = await userApi.getUserSummary(sid)
          if (info?.name) {
            userNameCache.value = { ...userNameCache.value, [sid]: info.name }
          }
        } catch {
          /* ignore */
        }
      })
    )

    if (gen !== assigneeDisplayHydrateGeneration.value) return

    let changed2 = false
    const merged = rows.value.map(r => {
      if (!r || typeof r !== 'object') return r
      const sid = extractUserIdFromCellValue(resolveRowAssigneeCell(r as Record<string, unknown>))
      if (!sid) return r
      const existing = r.assignee_display_name
      if (existing != null && String(existing).trim() !== '') return r
      const cached = userNameCache.value[sid]
      if (!cached) return r
      changed2 = true
      return { ...r, assignee_display_name: cached }
    })
    if (changed2) {
      rows.value = merged
      emit('update:modelValue', [...merged])
    }
  }

  watch(
    () => [props.assigneeField, props.modelValue],
    () => scheduleHydrateAssigneeDisplayNames(),
    { immediate: true }
  )

  /** onBeforeUnmount cleanup (kept in the SFC to preserve original hook ordering). */
  function clearAssigneeDisplayHydrateTimer() {
    if (assigneeDisplayHydrateTimer) {
      clearTimeout(assigneeDisplayHydrateTimer)
      assigneeDisplayHydrateTimer = null
    }
  }

  return { clearAssigneeDisplayHydrateTimer }
}
