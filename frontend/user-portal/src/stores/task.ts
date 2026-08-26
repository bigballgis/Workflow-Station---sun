import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  queryTasks,
  getTaskDetail,
  claimTask as claimTaskApi,
  completeTask as completeTaskApi,
  transferTask as transferTaskApi,
  delegateTask as delegateTaskApi,
  type TaskInfo,
  type TaskQueryRequest,
  type TaskCompleteRequest,
  type TaskDelegateRequest
} from '@/api/task'

export const useTaskStore = defineStore('task', () => {
  const tasks = ref<TaskInfo[]>([])
  const currentTask = ref<TaskInfo | null>(null)
  const loading = ref(false)
  const total = ref(0)

  const fetchTasks = async (params: TaskQueryRequest) => {
    loading.value = true
    try {
      const res = await queryTasks(params)
      tasks.value = res.data.content
      total.value = res.data.totalElements
    } finally {
      loading.value = false
    }
  }

  const fetchTaskDetail = async (taskId: string) => {
    loading.value = true
    try {
      const res = await getTaskDetail(taskId)
      currentTask.value = res.data
    } finally {
      loading.value = false
    }
  }

  const claimTask = async (taskId: string) => {
    await claimTaskApi(taskId)
  }

  const completeTask = async (taskId: string, data: TaskCompleteRequest) => {
    await completeTaskApi(taskId, data)
  }

  const transferTask = async (taskId: string, toUserId: string, reason?: string) => {
    await transferTaskApi(taskId, toUserId, reason)
  }

  const delegateTask = async (taskId: string, body: TaskDelegateRequest) => {
    await delegateTaskApi(taskId, body)
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
