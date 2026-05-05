/**
 * Tab 可见性刷新 composable
 * 
 * 消除 RoleList / BusinessUnitTree 中重复的 visibilitychange 监听模式。
 * 当用户从其他 Tab 切回时自动刷新数据。
 * 
 * @example
 * const { refreshNow } = useTabRefresh(() => fetchData())
 * // 自动在 onMounted 注册，onUnmounted 移除
 */

import { onMounted, onUnmounted } from 'vue'

export function useTabRefresh(refreshFn: () => void | Promise<void>) {
  const handler = () => {
    if (document.visibilityState === 'visible') {
      refreshFn()
    }
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', handler)
  })

  onUnmounted(() => {
    document.removeEventListener('visibilitychange', handler)
  })

  return {
    /** 手动触发刷新 */
    refreshNow: refreshFn,
  }
}
