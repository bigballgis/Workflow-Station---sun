import { defineStore } from 'pinia'
import { ref } from 'vue'
import { roleApi, permissionApi, Role, Permission, type CreateRoleRequest, type UpdateRoleRequest, type RolePermission, type RoleType } from '@/api/role'

export const useRoleStore = defineStore('role', () => {
  const roles = ref<Role[]>([])
  const permissions = ref<Permission[]>([])
  const loading = ref(false)
  const currentRole = ref<Role | null>(null)
  const total = ref(0)
  const currentPage = ref(0)
  const pageSize = ref(20)

  const fetchRoles = async (params?: { type?: RoleType; status?: string; page?: number; size?: number }) => {
    loading.value = true
    try {
      const p = { page: params?.page ?? currentPage.value, size: params?.size ?? pageSize.value, ...(params?.type ? { type: params.type } : {}), ...(params?.status ? { status: params.status } : {}) }
      const res = await roleApi.list(p)
      roles.value = res.content
      total.value = res.totalElements
      currentPage.value = p.page
      pageSize.value = p.size
    } finally {
      loading.value = false
    }
  }

  /** Load every page so client-side System/Custom tabs are complete. */
  const fetchAllRoles = async () => {
    loading.value = true
    try {
      const pageSizeAll = 100
      let page = 0
      const all: Role[] = []
      let totalElements = Number.POSITIVE_INFINITY
      while (all.length < totalElements) {
        const res = await roleApi.list({ page, size: pageSizeAll })
        const batch = res.content ?? []
        totalElements = res.totalElements ?? batch.length
        all.push(...batch)
        if (batch.length === 0) break
        page += 1
        if (page > 1000) break
      }
      roles.value = all
      total.value = all.length
      currentPage.value = 0
      pageSize.value = pageSizeAll
    } finally {
      loading.value = false
    }
  }

  const fetchPermissionTree = async () => {
    permissions.value = await permissionApi.getTree()
  }

  const createRole = async (data: CreateRoleRequest) => {
    await roleApi.create(data)
    await fetchAllRoles()
  }

  const updateRole = async (id: string, data: UpdateRoleRequest) => {
    await roleApi.update(id, data)
    await fetchAllRoles()
  }

  const deleteRole = async (id: string) => {
    await roleApi.delete(id)
    await fetchAllRoles()
  }

  const updateRolePermissions = async (id: string, permissions: RolePermission[]) => {
    await roleApi.updatePermissions(id, permissions)
  }

  return {
    roles,
    permissions,
    loading,
    total,
    currentPage,
    pageSize,
    currentRole,
    fetchRoles,
    fetchAllRoles,
    fetchPermissionTree,
    createRole,
    updateRole,
    deleteRole,
    updateRolePermissions,
  }
})
