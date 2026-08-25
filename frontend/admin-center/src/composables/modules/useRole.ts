/**
 * Role List 业务逻辑 composable
 *
 * 组件仅保留 template + 调用此 composable + useTabRefresh。
 */

import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { notifyError } from '@/utils/notify'
import { roleApi, Role, type RoleType } from '@/api/role'
import { useRoleStore } from '@/stores/role'
import { hasPermission, PERMISSIONS } from '@/utils/permission'
import type { RoleListTab } from '@/utils/roleList'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'

const ACTIONS_COL_WIDTH = 220
const ROLE_COL_WIDTHS: Record<string, number> = {
  name: 160,
  code: 140,
  type: 130,
  status: 100,
  isSystem: 110,
}

export function useRole() {
  const { t } = useI18n()
  const roleStore = useRoleStore()

  const canWriteRole = hasPermission(PERMISSIONS.ROLE_WRITE)
  const canDeleteRole = hasPermission(PERMISSIONS.ROLE_DELETE)

  const loading = ref(false)
  const activeTab = ref<RoleListTab>('CUSTOM')
  const query = reactive<{ type: RoleType | '' }>({ type: '' })
  const typeFilter = ref<RoleType | ''>('')

  const formDialogVisible = ref(false)
  const membersDialogVisible = ref(false)
  const currentRole = ref<Role | null>(null)

  const grid = useAdminListGrid<Role>({
    storageKey: 'admin-list-layout:roles',
    extraWidth: ACTIONS_COL_WIDTH,
    defaultWidthOf: (field) => ROLE_COL_WIDTHS[field] ?? 120,
  })

  const fetchRoles = async () => {
    const seq = grid.beginQuery()
    loading.value = true
    try {
      const page = await roleApi.query({
        ...grid.buildQuery(),
        tab: activeTab.value,
        type: typeFilter.value || undefined,
      })
      if (!grid.isCurrentQuery(seq)) return
      grid.applyPage(page, 'roles/query response is missing its column declaration')
    } catch (error: unknown) {
      if (!grid.isCurrentQuery(seq)) return
      if (!(error as { response?: unknown })?.response) {
        notifyError(error instanceof Error ? error.message : t('common.failed'))
      }
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
    }
  }

  watch(activeTab, () => {
    grid.resetPage()
    void fetchRoles()
  })

  const handleSearch = () => {
    typeFilter.value = query.type
    grid.resetPage()
    void fetchRoles()
  }

  const handleReset = () => {
    query.type = ''
    typeFilter.value = ''
    grid.resetPage()
    void fetchRoles()
  }

  const showCreateDialog = () => {
    currentRole.value = null
    formDialogVisible.value = true
  }

  const showEditDialog = (role: Role) => {
    currentRole.value = role
    formDialogVisible.value = true
  }

  const showMembersDialog = (role: Role) => {
    currentRole.value = role
    membersDialogVisible.value = true
  }

  const handleDelete = async (role: Role) => {
    await ElMessageBox.confirm(t('role.confirmDeleteRole'), t('user.hint'), { type: 'warning' })
    await roleStore.deleteRole(role.id)
    ElMessage.success(t('common.success'))
    await fetchRoles()
  }

  return {
    loading,
    canWriteRole,
    canDeleteRole,
    activeTab,
    query,
    formDialogVisible,
    membersDialogVisible,
    currentRole,
    fetchRoles,
    handleSearch,
    handleReset,
    showCreateDialog,
    showEditDialog,
    showMembersDialog,
    handleDelete,
    ACTIONS_COL_WIDTH,
    ...grid,
  }
}
