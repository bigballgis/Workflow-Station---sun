/**
 * 虚拟组业务逻辑 composable
 *
 * 封装 virtual-group 页面的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 */

import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { ElMessage } from 'element-plus'
import type { VirtualGroup } from '@/api/virtualGroup'
import { storeToRefs } from 'pinia'
import { useVirtualGroupStore } from '@/stores/virtualGroup'

export function useVirtualGroup() {
  const { t } = useI18n()
  const store = useVirtualGroupStore()
  const { groups, loading } = storeToRefs(store)

  // ==================== State ====================
  const formDialogVisible = ref(false)
  const membersDialogVisible = ref(false)
  const rolesDialogVisible = ref(false)
  const approversDialogVisible = ref(false)
  const currentGroup = ref<VirtualGroup | null>(null)

  // ==================== Data Fetching ====================

  const fetchGroups = async () => {
    try {
      await store.fetchGroups()
    } catch (e) {
      ElMessage.error(t('common.failed'))
    }
  }

  // ==================== Dialog Actions ====================

  const showCreateDialog = () => {
    currentGroup.value = null
    formDialogVisible.value = true
  }

  const showEditDialog = (group: VirtualGroup) => {
    currentGroup.value = group
    formDialogVisible.value = true
  }

  const showMembersDialog = (group: VirtualGroup) => {
    currentGroup.value = group
    membersDialogVisible.value = true
  }

  const showRolesDialog = (group: VirtualGroup) => {
    currentGroup.value = group
    rolesDialogVisible.value = true
  }

  const showApproversDialog = (group: VirtualGroup) => {
    currentGroup.value = group
    approversDialogVisible.value = true
  }

  // ==================== Delete ====================

  const { handleDelete } = useConfirmDelete(
    (id: string) => store.deleteGroup(id),
    {
      confirmMessage: t('common.confirm'),
      successMessage: t('common.success'),
      errorMessage: t('common.failed'),
      onSuccess: fetchGroups,
    }
  )

  // ==================== Return ====================

  return {
    // State
    loading,
    groups,
    formDialogVisible,
    membersDialogVisible,
    rolesDialogVisible,
    approversDialogVisible,
    currentGroup,
    // Methods
    fetchGroups,
    showCreateDialog,
    showEditDialog,
    showMembersDialog,
    showRolesDialog,
    showApproversDialog,
    handleDelete,
  }
}
