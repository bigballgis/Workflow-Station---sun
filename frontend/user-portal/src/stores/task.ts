import { defineStore } from 'pinia'
import { ref } from 'vue'
import { taskApi, type TaskInfo, type TaskQueryRequest } from '@/api/task'

export const useTaskStore = defineStore('task', () => {
  const tasks = ref<TaskInfo[]>([])
  const currentTask = ref<TaskInfo | null>(null)
  const loading = ref(false)
  const total = ref(0)

  const fetchTasks = async (params: TaskQueryRequest) => {
    loading.value = true
    try {
      const res = await taskApi.getMyTasks(params)
      tasks.value = res.data.content
      total.value = res.data.totalElements
    } finally {
      loading.value = false
    }
  }

  const fetchTaskDetail = async (taskId: string) => {
    loading.value = true
    try {
      const res = await taskApi.getTaskDetail(taskId)
      currentTask.value = res.data
    } finally {
      loading.value = false
    }
  }

  const claimTask = async (taskId: string) => {
    await taskApi.claimTask(taskId)
  }

  const completeTask = async (taskId: string, data: { variables?: Record<string, any>; comment?: string; action?: string }) => {
    await taskApi.completeTask(taskId, {
      taskId,
      action: data.action || 'approve',
      comment: data.comment,
      variables: data.variables
    })
  }

  const transferTask = async (taskId: string, targetUserId: string, reason: string) => {
    await taskApi.transferTask(taskId, { targetUserId, reason })
  }

  const delegateTask = async (taskId: string, targetUserId: string, reason: string) => {
    await taskApi.delegateTask(taskId, { targetUserId, reason })
  }

  return {
    tasks,
    currentTask,
    loading,
    total,
    fetchTasks,
    fetchTaskDetail,
    claimTask,
    completeTask,
    transferTask,
    delegateTask
  }
})
