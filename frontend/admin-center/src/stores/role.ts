import { defineStore } from 'pinia'
import { ref } from 'vue'
import { roleApi, permissionApi, Role, Permission, RoleType } from '@/api/role'

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

  const createRole = async (data: any) => {
    await roleApi.create(data)
    await fetchRoles()
  }

  const updateRole = async (id: string, data: any) => {
    await roleApi.update(id, data)
    await fetchRoles()
  }

  const deleteRole = async (id: string) => {
    await roleApi.delete(id)
    await fetchRoles()
  }

  const updateRolePermissions = async (id: string, permissions: any[]) => {
    await roleApi.updatePermissions(id, permissions)
  }

  return { roles, permissions, loading, currentRole, fetchRoles, fetchPermissionTree, createRole, updateRole, deleteRole, updateRolePermissions }
})
