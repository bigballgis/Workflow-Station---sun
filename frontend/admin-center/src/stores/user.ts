import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi, type User, type UserQuery, type PageResult, type UserDetail } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const users = ref<User[]>([])
  const total = ref(0)
  const loading = ref(false)
  const currentUser = ref<UserDetail | null>(null)

  const fetchUsers = async (query: UserQuery) => {
    loading.value = true
    try {
      const result: PageResult<User> = await userApi.list(query)
      users.value = result.content
      total.value = result.totalElements
    } finally {
      loading.value = false
    }
  }

  const fetchUserDetail = async (id: string) => {
    loading.value = true
    try {
      currentUser.value = await userApi.getById(id)
    } finally {
      loading.value = false
    }
  }

  const createUser = async (data: any) => {
    return await userApi.create(data)
  }

  const updateUser = async (id: string, data: any) => {
    await userApi.update(id, data)
  }

  const deleteUser = async (id: string) => {
    await userApi.delete(id)
  }

  const updateStatus = async (id: string, status: 'ACTIVE' | 'DISABLED' | 'LOCKED', reason?: string) => {
    await userApi.updateStatus(id, { status, reason })
  }

  const resetPassword = async (id: string) => {
    return await userApi.resetPassword(id)
  }

  return { 
    users, total, loading, currentUser, 
    fetchUsers, fetchUserDetail, createUser, updateUser, deleteUser, updateStatus, resetPassword 
  }
})
