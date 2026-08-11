/**
 * 虚拟组业务逻辑 composable
 *
 * 封装 virtual-group 页面的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 */

import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { notifyError, notifySuccess } from '@/utils/notify'
import type { VirtualGroup } from '@/api/virtualGroup'
import { storeToRefs } from 'pinia'
import { useVirtualGroupStore } from '@/stores/virtualGroup'
import {
  filterSortVirtualGroups,
  paginateVirtualGroups,
  type VirtualGroupTab,
} from '@/utils/virtualGroupList'

export function useVirtualGroup() {
  const { t, locale } = useI18n()
  const store = useVirtualGroupStore()
  const { groups, loading } = storeToRefs(store)

  const formDialogVisible = ref(false)
  const membersDialogVisible = ref(false)
  const rolesDialogVisible = ref(false)
  const currentGroup = ref<VirtualGroup | null>(null)
  const statusToggleLoadingId = ref<string | null>(null)

  const activeTab = ref<VirtualGroupTab>('CUSTOM')
  const searchKeyword = ref('')
  const listPagination = ref({ page: 1, size: 20 })

  const filteredGroups = computed(() =>
    filterSortVirtualGroups(groups.value, activeTab.value, searchKeyword.value, locale.value)
  )

  const listTotal = computed(() => filteredGroups.value.length)

  const pagedGroups = computed(() =>
    paginateVirtualGroups(filteredGroups.value, listPagination.value.page, listPagination.value.size)
  )

  watch([activeTab, searchKeyword], () => {
    listPagination.value.page = 1
  })

  const fetchGroups = async () => {
    try {
      await store.fetchGroups()
    } catch {
      notifyError(t(errorTranslator(AppErrorCode.VIRTUAL_GROUP_LOAD_FAILED)))
    }
  }

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

  const handleCreateSuccess = async (createdType?: VirtualGroupTab) => {
    await fetchGroups()
    if (createdType === 'CUSTOM' || createdType === 'DEVELOPER') {
      activeTab.value = createdType
    }
  }

  const { handleDelete: deleteById } = useConfirmDelete(
    (id: string) => store.deleteGroup(id),
    {
      confirmMessage: t('common.confirm'),
      onSuccess: fetchGroups,
    }
  )

  const handleDelete = async (id: string) => {
    const r = await deleteById(id)
    if (r.cancelled) return
    if (r.ok) notifySuccess(t('common.success'))
    else notifyError(t(errorTranslator(r.code || AppErrorCode.VIRTUAL_GROUP_LOAD_FAILED)))
  }

  const handleListSizeChange = () => {
    listPagination.value.page = 1
  }

  const handleToggleStatus = async (group: VirtualGroup) => {
    const activating = group.status !== 'ACTIVE'
    try {
      await ElMessageBox.confirm(
        t(activating ? 'virtualGroup.confirmActivate' : 'virtualGroup.confirmDeactivate'),
        t('common.confirm'),
        { type: 'warning' }
      )
    } catch {
      return
    }
    statusToggleLoadingId.value = group.id
    try {
      if (activating) await store.activateGroup(group.id)
      else await store.deactivateGroup(group.id)
      notifySuccess(t('common.success'))
      await fetchGroups()
    } catch {
      notifyError(t(errorTranslator(AppErrorCode.VIRTUAL_GROUP_LOAD_FAILED)))
    } finally {
      statusToggleLoadingId.value = null
    }
  }

  return {
    loading,
    groups,
    activeTab,
    searchKeyword,
    listPagination,
    listTotal,
    pagedGroups,
    formDialogVisible,
    membersDialogVisible,
    rolesDialogVisible,
    currentGroup,
    statusToggleLoadingId,
    fetchGroups,
    showCreateDialog,
    showEditDialog,
    showMembersDialog,
    showRolesDialog,
    handleDelete,
    handleCreateSuccess,
    handleListSizeChange,
    handleToggleStatus,
  }
}
