import { defineStore } from 'pinia'
import { ref } from 'vue'
import { queryTasks } from '@/api/task'

/**
 * 侧边栏「To Do」旁展示的待处理任务数量（与 /tasks 列表总数一致）。
 */
export const usePendingTaskStore = defineStore('pendingTask', () => {
  const count = ref(0)

  const fetchPendingCount = async () => {
    try {
      const res = (await queryTasks({ page: 0, size: 1 })) as {
        data?: { totalElements?: number }
        totalElements?: number
      }
      const total = res?.data?.totalElements ?? res?.totalElements ?? 0
      count.value = typeof total === 'number' ? total : 0
    } catch {
      count.value = 0
    }
  }

  return { count, fetchPendingCount }
})
