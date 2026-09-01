import { onMounted, onUnmounted, ref } from 'vue'

/**
 * Interval auto-refresh for Admin Center audit list.
 * Pauses when the document is hidden so a slow tab does not stack downloads.
 */
export function useAuditAutoRefresh(opts: {
  intervalSeconds: number
  shouldSkip: () => boolean
  run: () => void
}) {
  const refreshCountdown = ref(opts.intervalSeconds)
  const autoRefreshPaused = ref(false)
  let refreshTimer: ReturnType<typeof setInterval> | null = null

  const stopAutoRefresh = () => {
    if (refreshTimer !== null) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  }

  const startAutoRefresh = () => {
    stopAutoRefresh()
    refreshCountdown.value = opts.intervalSeconds
    if (autoRefreshPaused.value || document.hidden) {
      return
    }
    refreshTimer = setInterval(() => {
      if (document.hidden) {
        return
      }
      refreshCountdown.value -= 1
      if (refreshCountdown.value > 0) {
        return
      }
      if (opts.shouldSkip()) {
        refreshCountdown.value = opts.intervalSeconds
        return
      }
      opts.run()
    }, 1000)
  }

  const toggleAutoRefresh = () => {
    autoRefreshPaused.value = !autoRefreshPaused.value
    if (autoRefreshPaused.value) {
      stopAutoRefresh()
    } else {
      startAutoRefresh()
    }
  }

  const onVisibility = () => {
    if (document.hidden) {
      stopAutoRefresh()
    } else if (!autoRefreshPaused.value) {
      startAutoRefresh()
    }
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', onVisibility)
  })

  onUnmounted(() => {
    stopAutoRefresh()
    document.removeEventListener('visibilitychange', onVisibility)
  })

  return {
    refreshCountdown,
    autoRefreshPaused,
    toggleAutoRefresh,
    startAutoRefresh,
    stopAutoRefresh,
  }
}
