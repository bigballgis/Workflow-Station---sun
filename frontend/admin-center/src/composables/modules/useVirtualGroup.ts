/**
 * 虚拟组业务逻辑 composable
 *
 * 封装 virtual-group 页面的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 */

import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { notifyError, notifySuccess } from '@/utils/notify'
import { virtualGroupApi, type VirtualGroup } from '@/api/virtualGroup'
import { useVirtualGroupStore } from '@/stores/virtualGroup'
import type { VirtualGroupTab } from '@/utils/virtualGroupList'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'

const ACTIONS_COL_WIDTH = 400
const VG_COL_WIDTHS: Record<string, number> = {
  name: 160,
  code: 160,
  type: 120,
  boundRoleName: 160,
  boundRoleType: 120,
  adGroup: 140,
  memberCount: 100,
  status: 100,
}

export function useVirtualGroup() {
  const { t } = useI18n()
  const store = useVirtualGroupStore()

  const loading = ref(false)
  const formDialogVisible = ref(false)
  const membersDialogVisible = ref(false)
  const rolesDialogVisible = ref(false)
  const currentGroup = ref<VirtualGroup | null>(null)
  const statusToggleLoadingId = ref<string | null>(null)

  const activeTab = ref<VirtualGroupTab>('CUSTOM')
  const searchKeyword = ref('')

  const grid = useAdminListGrid<VirtualGroup>({
    storageKey: 'admin-list-layout:virtual-groups',
    extraWidth: ACTIONS_COL_WIDTH,
    defaultWidthOf: (field) => VG_COL_WIDTHS[field] ?? 120,
  })

  const fetchGroups = async () => {
    const seq = grid.beginQuery()
    loading.value = true
    try {
      const page = await virtualGroupApi.query({
        ...grid.buildQuery(),
        type: activeTab.value,
        keyword: searchKeyword.value || undefined,
      })
      if (!grid.isCurrentQuery(seq)) return
      grid.applyPage(page, 'virtual-groups/query response is missing its column declaration')
    } catch {
      if (!grid.isCurrentQuery(seq)) return
      notifyError(t(errorTranslator(AppErrorCode.VIRTUAL_GROUP_LOAD_FAILED)))
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
    }
  }

  watch(activeTab, () => {
    grid.resetPage()
    void fetchGroups()
  })

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
    if ((createdType === 'CUSTOM' || createdType === 'DEVELOPER') && activeTab.value !== createdType) {
      activeTab.value = createdType
      return
    }
    await fetchGroups()
  }

  const { handleDelete: deleteById } = useConfirmDelete(
    (id: string) => store.deleteGroup(id),
    {
      confirmMessage: t('common.confirm'),
      onSuccess: fetchGroups,
    },
  )

  const handleDelete = async (id: string) => {
    const r = await deleteById(id)
    if (r.cancelled) return
    if (r.ok) notifySuccess(t('common.success'))
    else notifyError(t(errorTranslator(r.code || AppErrorCode.VIRTUAL_GROUP_LOAD_FAILED)))
  }

  const handleToggleStatus = async (group: VirtualGroup) => {
    const activating = group.status !== 'ACTIVE'
    try {
      await ElMessageBox.confirm(
        t(activating ? 'virtualGroup.confirmActivate' : 'virtualGroup.confirmDeactivate'),
        t('common.confirm'),
        { type: 'warning' },
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
    activeTab,
    searchKeyword,
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
    handleToggleStatus,
    ACTIONS_COL_WIDTH,
    ...grid,
  }
}
