import { defineStore } from 'pinia'
import { ref } from 'vue'
import { roleApi, permissionApi, Role, Permission, type CreateRoleRequest, type UpdateRoleRequest, type RolePermission, type RoleType } from '@/api/role'

export const useRoleStore = defineStore('role', () => {
  const roles = ref<Role[]>([])
  const permissions = ref<Permission[]>([])
  const loading = ref(false)
  const currentRole = ref<Role | null>(null)

  const fetchRoles = async (params?: { type?: RoleType; status?: string }) => {
    loading.value = true
    try {
      roles.value = await roleApi.list(params)
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

  return { roles, permissions, loading, currentRole, fetchRoles, fetchPermissionTree, createRole, updateRole, deleteRole, updateRolePermissions }
})
