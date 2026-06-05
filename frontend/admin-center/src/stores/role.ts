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

  const fetchPermissionTree = async () => {
    permissions.value = await permissionApi.getTree()
  }

  const createRole = async (data: CreateRoleRequest) => {
    await roleApi.create(data)
    await fetchRoles()
  }

  const updateRole = async (id: string, data: UpdateRoleRequest) => {
    await roleApi.update(id, data)
    await fetchRoles()
  }

  const deleteRole = async (id: string) => {
    await roleApi.delete(id)
    await fetchRoles()
  }

  const updateRolePermissions = async (id: string, permissions: RolePermission[]) => {
    await roleApi.updatePermissions(id, permissions)
  }

  return { roles, permissions, loading, total, currentPage, pageSize, currentRole, fetchRoles, fetchPermissionTree, createRole, updateRole, deleteRole, updateRolePermissions }
})
