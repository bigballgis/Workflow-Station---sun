/**
 * 用户管理业务逻辑 composable
 *
 * 封装 UserList.vue 的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 *
 * 所有 notify* / notifyConfirm 调用均在此处处理。
 */

import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifyConfirm, notifyError, notifySuccess } from '@/utils/notify'
import type { User } from '@/api/user'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { extractErrorDetail } from '@/utils/errorTranslator'
import { usePagination } from '@/composables/usePagination'
import { hasPermission, PERMISSIONS } from '@/utils/permission'

export function useUser() {
  const { t } = useI18n()
  const store = useUserStore()

  // ==================== Permissions ====================

  const canWriteUser = hasPermission(PERMISSIONS.USER_WRITE)
  const canDeleteUser = hasPermission(PERMISSIONS.USER_DELETE)

  // ==================== Data Fetching ====================

  const fetchFn = async (params: any) => {
    await store.fetchUsers(params)
    return { content: store.users, totalElements: store.total }
  }

  const { data: users, total, loading, query, handleSearch, handleReset } = usePagination(
    fetchFn,
    { keyword: '', status: '' },
  )

  // ==================== State ====================

  // Dialog visibility
  const formDialogVisible = ref(false)
  const detailDialogVisible = ref(false)
  const importDialogVisible = ref(false)

  // Current selections
  const currentUser = ref<User | null>(null)
  const currentUserId = ref('')

  // ==================== Dialog Actions (UI helpers) ====================

  const showCreateDialog = () => {
    currentUser.value = null
    formDialogVisible.value = true
  }

  const showEditDialog = (user: User) => {
    currentUser.value = user
    formDialogVisible.value = true
  }

  const showDetailDialog = (user: User) => {
    currentUserId.value = user.id
    detailDialogVisible.value = true
  }

  const showImportDialog = () => {
    importDialogVisible.value = true
  }

  // ==================== Command Handler ====================

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
      case 'resetPassword':
        await handleResetPassword(user)
        break
      case 'delete':
        await handleDelete(user)
        break
    }
  }

  // ==================== Status Change ====================

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

  // ==================== Reset Password ====================

  const handleResetPassword = async (user: User) => {
    try {
      await notifyConfirm(
        t('user.confirmResetPassword', { name: user.fullName }),
        t('user.hint'),
        { type: 'warning' },
      )
      await userApi.resetPassword(user.id)
      notifySuccess(t('user.passwordResetNoPlaintext'))
    } catch (error: unknown) {
      if (error !== 'cancel') {
        notifyError(extractErrorDetail(error) || t('user.resetPasswordFailed'))
      }
    }
  }

  // ==================== Delete ====================

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

  // ==================== Return ====================

  return {
    // Permissions
    canWriteUser,
    canDeleteUser,
    // State
    loading,
    users,
    total,
    query,
    // Dialog visibility
    formDialogVisible,
    detailDialogVisible,
    importDialogVisible,
    // Current selections
    currentUser,
    currentUserId,
    // Methods
    handleSearch,
    handleReset,
    showCreateDialog,
    showEditDialog,
    showDetailDialog,
    showImportDialog,
    handleCommand,
    handleStatusChange,
    handleResetPassword,
    handleDelete,
  }
}
