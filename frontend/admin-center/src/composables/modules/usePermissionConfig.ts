/**
 * 权限配置业务逻辑 composable
 */
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useRoleStore } from '@/stores/role'
import { roleApi, permissionApi, type Role } from '@/api/role'

export function usePermissionConfig() {
  const { t } = useI18n()
  const roleStore = useRoleStore()

  const roleFilter = ref('')
  const selectedRoleId = ref('')
  const selectedRole = ref<Role | null>(null)
  const actions = ['CREATE', 'READ', 'UPDATE', 'DELETE', 'EXECUTE']
  const loading = ref(false)
  const permissionMatrix = ref<any[]>([])
  const allPermissions = ref<any[]>([])

  const filteredRoles = computed(() =>
    roleStore.roles.filter(r => !roleFilter.value || r.name.includes(roleFilter.value))
  )

  const handleRoleSelect = async (roleId: string) => {
    selectedRoleId.value = roleId
    selectedRole.value = roleStore.roles.find(r => r.id === roleId) || null
    if (!selectedRole.value) return

    loading.value = true
    try {
      const rolePermissions = await roleApi.getPermissions(roleId)
      permissionMatrix.value = allPermissions.value.map(permission => {
        const rolePermission = rolePermissions.find((rp: any) => rp.permissionId === permission.id)
        const permissionActions = rolePermission?.actions || []
        return {
          id: permission.id, name: permission.name,
          permissions: {
            CREATE: permissionActions.includes('CREATE'), READ: permissionActions.includes('READ'),
            UPDATE: permissionActions.includes('UPDATE'), DELETE: permissionActions.includes('DELETE'),
            EXECUTE: permissionActions.includes('EXECUTE')
          }
        }
      })
    } catch (error) {
      console.error('Failed to load role permissions:', error)
      ElMessage.error(t('permission.loadRolePermissionFailed'))
    } finally { loading.value = false }
  }

  const handleSave = async () => {
    if (!selectedRole.value) return
    loading.value = true
    try {
      const permissions = permissionMatrix.value
        .filter(item => Object.values(item.permissions).some(v => v))
        .map(item => ({
          roleId: selectedRole.value!.id, permissionId: item.id,
          actions: Object.entries(item.permissions).filter(([_, enabled]) => enabled).map(([action]) => action)
        }))
      await roleApi.updatePermissions(selectedRole.value.id, permissions)
      ElMessage.success(t('common.success'))
    } catch (error) {
      console.error('Failed to save permissions:', error)
      ElMessage.error(t('permission.savePermissionFailed'))
    } finally { loading.value = false }
  }

  onMounted(async () => {
    await roleStore.fetchRoles()
    try { allPermissions.value = await permissionApi.getTree() }
    catch (error) {
      console.error('Failed to load permissions:', error)
      ElMessage.error(t('permission.loadPermissionListFailed'))
    }
  })

  return {
    roleFilter, selectedRoleId, selectedRole, actions, loading,
    permissionMatrix, filteredRoles, handleRoleSelect, handleSave,
  }
}
