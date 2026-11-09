/**
 * Role List 业务逻辑 composable
 *
 * 组件仅保留 template + 调用此 composable + useTabRefresh。
 */

import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifyConfirm, notifyError, notifySuccess } from '@/utils/notify'
import { roleApi, Role, type RoleType } from '@/api/role'
import { useRoleStore } from '@/stores/role'
import { canWrite } from '@/utils/permission'
import { confirmMessageWithReferences } from '@/composables/useReferenceNotice'
import type { ReferenceNotice } from '@/types/referenceNotice'
import type { RoleListTab } from '@/utils/roleList'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'

const ACTIONS_COL_WIDTH = 220

export function useRole() {
  const { t } = useI18n()
  const roleStore = useRoleStore()

  // Business (Custom) roles: every Technical Lead (§8.3, D-12); System roles are changed by nobody (AC-13).
  const canWriteRole = canWrite('roles')
  const canDeleteRole = canWrite('roles')

  const loading = ref(false)
  const activeTab = ref<RoleListTab>('CUSTOM')
  const query = reactive<{ type: RoleType | '' }>({ type: '' })
  const typeFilter = ref<RoleType | ''>('')

  const formDialogVisible = ref(false)
  const membersDialogVisible = ref(false)
  const currentRole = ref<Role | null>(null)
  /** Where the role being edited is used; the edit dialog shows it (§8.3, notice only). */
  const currentReferences = ref<ReferenceNotice | null>(null)

  const grid = useAdminListGrid<Role>({
    storageKey: 'admin-list-layout:roles',
    extraWidth: ACTIONS_COL_WIDTH,
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
    currentReferences.value = null
    formDialogVisible.value = true
  }

  const showEditDialog = async (role: Role) => {
    // A failed load is reported by the request layer; the dialog then stays closed.
    currentReferences.value = await roleApi.getReferences(role.id)
    currentRole.value = role
    formDialogVisible.value = true
  }

  const showMembersDialog = (role: Role) => {
    currentRole.value = role
    membersDialogVisible.value = true
  }

  const handleDelete = async (role: Role) => {
    const references = await roleApi.getReferences(role.id)
    await notifyConfirm(confirmMessageWithReferences(t('role.confirmDeleteRole'), references), t('user.hint'),
      { type: 'warning' })
    await roleStore.deleteRole(role.id)
    notifySuccess(t('common.success'))
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
    currentReferences,
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
