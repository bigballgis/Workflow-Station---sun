import { watch, type Ref } from 'vue'
import { getSubTableData } from '@/api/task'
import { useSubTableWebSocket, type SubTableUpdateMessage } from '@/composables/useSubTableWebSocket'
import type { SubTableFieldEmit, SubTableFieldProps } from './subTableFieldTypes'

/** Real-time sync (interval polling + WebSocket subscription) for MI sub-table rows. */
export function useSubTablePollingSync(
  props: SubTableFieldProps,
  rows: Ref<any[]>,
  emit: SubTableFieldEmit,
  deps: { resolveSubTableRowMergeKey: (row: Record<string, unknown> | null | undefined) => string | number | null },
) {
  const { resolveSubTableRowMergeKey } = deps

  // Real-time polling functionality
  let pollingTimer: ReturnType<typeof setInterval> | null = null

  // WebSocket functionality
  const { connected: wsConnected, subscribe: wsSubscribe, unsubscribe: wsUnsubscribe } = useSubTableWebSocket()

  async function refreshSubTableData() {
    if (!props.taskId) return

    try {
      const response = await getSubTableData(props.taskId)
      const result = response.data || response

      if (result.rows && Array.isArray(result.rows)) {
        // Merge the refreshed data with existing rows.
        // IMPORTANT: do NOT match by `id` when missing/undefined, or the first refreshed row
        // can be merged into every row, causing cross-row field leakage (e.g. assignee).
        const refreshedRows = result.rows as Array<Record<string, any>>
        const refreshedByPk = new Map<string | number, Record<string, any>>()
        for (const r of refreshedRows) {
          const pk = resolveSubTableRowMergeKey(r)
          if (pk != null && pk !== '') refreshedByPk.set(pk, r)
        }

        const updatedRows = rows.value.map((existingRow, idx) => {
          const pk = resolveSubTableRowMergeKey(existingRow as Record<string, unknown>)
          const refreshedRow =
            (pk != null && refreshedByPk.get(pk)) ||
            null
          if (refreshedRow) {
            return { ...existingRow, ...refreshedRow }
          }
          // Fallback: if no PKs are available, only merge by index when both sides exist.
          // This is safer than "undefined id" matching.
          const byIndex = refreshedRows[idx]
          if (pk == null && byIndex && resolveSubTableRowMergeKey(byIndex) == null) {
            return { ...existingRow, ...byIndex }
          }
          return existingRow
        })

        rows.value = updatedRows
        emit('update:modelValue', [...rows.value])
        emit('dataRefreshed', updatedRows)
      }
    } catch (error) {
      console.error('Failed to refresh sub-table data:', error)
      // Silently fail - don't show error message for background polling
    }
  }

  function startPolling() {
    if (!props.enablePolling || !props.taskId) return

    stopPolling()

    const interval = props.pollingInterval || 5000 // Default 5 seconds
    pollingTimer = setInterval(() => {
      refreshSubTableData()
    }, interval)
  }

  function stopPolling() {
    if (pollingTimer) {
      clearInterval(pollingTimer)
      pollingTimer = null
    }
  }

  // WebSocket subscription management
  function startWebSocketSubscription() {
    if (!props.enableWebSocket || !props.taskId) return

    stopWebSocketSubscription()

    wsSubscribe(props.taskId, (message: SubTableUpdateMessage) => {
      console.log('[SubTableField] Received WebSocket update:', message)
      // Refresh data when receiving update notification
      refreshSubTableData()
    })
  }

  function stopWebSocketSubscription() {
    wsUnsubscribe()
  }

  // Watch for enablePolling changes
  watch(() => props.enablePolling, (enabled) => {
    if (enabled) {
      startPolling()
    } else {
      stopPolling()
    }
  })

  // Watch for enableWebSocket changes
  watch(() => props.enableWebSocket, (enabled) => {
    if (enabled) {
      startWebSocketSubscription()
    } else {
      stopWebSocketSubscription()
    }
  })

  // Watch for taskId changes
  watch(() => props.taskId, () => {
    if (props.enablePolling) {
      stopPolling()
      startPolling()
    }
    if (props.enableWebSocket) {
      stopWebSocketSubscription()
      startWebSocketSubscription()
    }
  })

  return { startPolling, stopPolling, startWebSocketSubscription, stopWebSocketSubscription }
}
