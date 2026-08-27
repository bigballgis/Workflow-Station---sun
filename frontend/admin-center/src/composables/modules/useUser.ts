/**
 * 用户管理业务逻辑 composable
 *
 * 封装 UserList.vue 的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 *
 * 所有 notify* / notifyConfirm 调用均在此处处理。
 */

import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifyConfirm, notifyError, notifySuccess } from '@/utils/notify'
import type { User } from '@/api/user'
import { userApi } from '@/api/user'
import { extractErrorDetail } from '@/utils/errorTranslator'
import { hasPermission, PERMISSIONS } from '@/utils/permission'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'

const ACTIONS_COL_WIDTH = 140

export function useUser() {
  const { t } = useI18n()

  const canWriteUser = hasPermission(PERMISSIONS.USER_WRITE)
  const canDeleteUser = hasPermission(PERMISSIONS.USER_DELETE)

  const loading = ref(false)
  const query = reactive({ keyword: '', status: '' })

  const grid = useAdminListGrid<User>({
    storageKey: 'admin-list-layout:users',
    extraWidth: ACTIONS_COL_WIDTH,
  })

  const loadUsers = async () => {
    const seq = grid.beginQuery()
    loading.value = true
    try {
      const page = await userApi.query({
        ...grid.buildQuery(),
        keyword: query.keyword || undefined,
        status: query.status || undefined,
      })
      if (!grid.isCurrentQuery(seq)) return
      grid.applyPage(page, 'users/query response is missing its column declaration')
    } catch (error: unknown) {
      if (!grid.isCurrentQuery(seq)) return
      if (!(error as { response?: unknown })?.response) {
        notifyError(error instanceof Error ? error.message : t('user.actionFailed', { action: t('common.search') }))
      }
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
    }
  }

  const handleSearch = () => {
    void loadUsers()
  }

  const handleReset = () => {
    query.keyword = ''
    query.status = ''
    void loadUsers()
  }

  const formDialogVisible = ref(false)
  const detailDialogVisible = ref(false)
  const importDialogVisible = ref(false)

  const currentUser = ref<User | null>(null)
  const currentUserId = ref('')

  const showCreateDialog = () => {
    currentUser.value = null
    formDialogVisible.value = true
  }
  const showEditDialog = async (user: User) => {
    try {
      const detail = await userApi.getById(user.id)
      currentUser.value = detail
    } catch {
      // Fallback to row data to keep edit action available even when detail API fails.
      currentUser.value = user
    }
    formDialogVisible.value = true
  }

  const showDetailDialog = (user: User) => {
    currentUserId.value = user.id
    detailDialogVisible.value = true
  }

  const showImportDialog = () => {
    importDialogVisible.value = true
  }

  const handleCommand = async (user: User, command: string) => {
    switch (command) {
      case 'enable':
        await handleStatusChange(user, 'ACTIVE', t('user.enableUser'))
        break
      case 'disable':
        await handleStatusChange(user, 'DISABLED', t('user.disableUser'))
        break
      case 'unlock':
        await handleStatusChange(user, 'ACTIVE', t('user.unlockUser'))
        break
      case 'delete':
        await handleDelete(user)
        break
    }
  }

  const handleStatusChange = async (user: User, status: string, action: string) => {
    try {
      await notifyConfirm(
        t('user.confirmAction', { action, name: user.fullName }),
        t('user.hint'),
        { type: 'warning' },
      )
      await userApi.updateStatus(user.id, { status: status as 'ACTIVE' | 'DISABLED' | 'LOCKED' })
      notifySuccess(t('user.actionSuccess', { action }))
      handleSearch()
    } catch (error: unknown) {
      if (error !== 'cancel') {
        notifyError(extractErrorDetail(error) || t('user.actionFailed', { action }))
      }
    }
  }

  const handleDelete = async (user: User) => {
    try {
      await notifyConfirm(
        t('user.confirmDeleteUser', { name: user.fullName }),
        t('user.warning'),
        {
          type: 'warning',
          confirmButtonText: t('user.confirmDelete'),
          confirmButtonClass: 'el-button--danger',
        },
      )
      await userApi.delete(user.id)
      notifySuccess(t('user.deleteSuccess'))
      handleSearch()
    } catch (error: unknown) {
      if (error !== 'cancel') {
        notifyError(extractErrorDetail(error) || t('user.deleteFailed'))
      }
    }
  }

  return {
    canWriteUser,
    canDeleteUser,
    loading,
    query,
    formDialogVisible,
    detailDialogVisible,
    importDialogVisible,
    currentUser,
    currentUserId,
    handleSearch,
    handleReset,
    loadUsers,
    showCreateDialog,
    showEditDialog,
    showDetailDialog,
    showImportDialog,
    handleCommand,
    handleStatusChange,
    handleDelete,
    ACTIONS_COL_WIDTH,
    ...grid,
  }
}
