/**
 * 业务单元树业务逻辑 composable
 *
 * 封装 BusinessUnitTree.vue 的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable + useTabRefresh。
 *
 * 所有 notify* / notifyConfirm 调用均在此处处理。
 */

import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifyConfirm, notifyError, notifySuccess } from '@/utils/notify'
import { useOrganizationStore } from '@/stores/organization'
import { type BusinessUnit, organizationApi } from '@/api/organization'
import { businessUnitApi, type Approver } from '@/api/businessUnit'

export function useBusinessUnit() {
  const { t } = useI18n()
  const orgStore = useOrganizationStore()

  // ==================== State ====================

  const treeRef = ref()
  const filterText = ref('')
  const selectedBusinessUnit = ref<BusinessUnit | null>(null)
  const businessUnitMembers = ref<any[]>([])
  const businessUnitApprovers = ref<Approver[]>([])
  const dialogVisible = ref(false)
  const rolesDialogVisible = ref(false)
  const approversDialogVisible = ref(false)
  const membersDialogVisible = ref(false)
  const userDetailVisible = ref(false)
  const selectedUserId = ref('')
  const currentBusinessUnit = ref<BusinessUnit | null>(null)
  const parentBusinessUnit = ref<BusinessUnit | null>(null)

  // ==================== Pure Functions ====================

  /** 树节点过滤：无需 API，纯前端筛选 */
  const filterNode = (value: string, data: any) => !value || data.name.includes(value)

  // ==================== Data Fetching ====================

  const fetchMembers = async () => {
    if (!selectedBusinessUnit.value) return
    try {
      const result = await organizationApi.getMembers(selectedBusinessUnit.value.id, { page: 0, size: 50 })
      businessUnitMembers.value = result.content || []
    } catch (e) {
      businessUnitMembers.value = []
    }
  }

  const fetchApprovers = async () => {
    if (!selectedBusinessUnit.value) return
    try {
      businessUnitApprovers.value = await businessUnitApi.getApprovers(selectedBusinessUnit.value.id)
    } catch (e) {
      businessUnitApprovers.value = []
    }
  }

  // ==================== Node Click ====================

  /** 点击树节点：获取详情 + 并行加载成员和审批人 */
  const handleNodeClick = async (data: BusinessUnit) => {
    try {
      const detail = await organizationApi.getById(data.id)
      selectedBusinessUnit.value = detail
    } catch (e) {
      selectedBusinessUnit.value = data
    }
    // 并行加载成员和审批人
    await Promise.all([fetchMembers(), fetchApprovers()])
  }

  // ==================== Drag & Drop ====================

  const handleNodeDrop = async (draggingNode: any, dropNode: any, dropType: string) => {
    const newParentId = dropType === 'inner' ? dropNode.data.id : dropNode.data.parentId
    await orgStore.moveBusinessUnit(draggingNode.data.id, { newParentId })
    notifySuccess(t('common.success'))
  }

  // ==================== Dialog Actions ====================

  const showCreateDialog = (parent?: BusinessUnit) => {
    currentBusinessUnit.value = null
    parentBusinessUnit.value = parent || null
    dialogVisible.value = true
  }

  const showEditDialog = async (bu: BusinessUnit) => {
    try {
      const detail = await organizationApi.getById(bu.id)
      currentBusinessUnit.value = detail
    } catch (e) {
      currentBusinessUnit.value = bu
    }
    parentBusinessUnit.value = null
    dialogVisible.value = true
  }

  const showRolesDialog = () => { rolesDialogVisible.value = true }
  const showApproversDialog = () => { approversDialogVisible.value = true }
  const showMembersDialog = () => { membersDialogVisible.value = true }

  const showUserDetail = (userId: string) => {
    selectedUserId.value = userId
    userDetailVisible.value = true
  }

  // ==================== Form / CRUD Handlers ====================

  const handleFormSuccess = async () => {
    await orgStore.fetchTree()
    if (selectedBusinessUnit.value) {
      try {
        const detail = await organizationApi.getById(selectedBusinessUnit.value.id)
        selectedBusinessUnit.value = detail
      } catch (e) {
        selectedBusinessUnit.value = null
      }
    }
  }

  const handleDelete = async (bu: BusinessUnit) => {
    await notifyConfirm(t('organization.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    try {
      await orgStore.deleteBusinessUnit(bu.id)
      notifySuccess(t('common.success'))
      if (selectedBusinessUnit.value?.id === bu.id) selectedBusinessUnit.value = null
    } catch {
      notifyError(e.response?.data?.message || t('common.failed'))
    }
  }

  const handleMembersChange = async () => {
    await fetchMembers()
    await orgStore.fetchTree()
  }

  /** tab 切回时刷新详情（与 handleNodeClick 类似但不接受 data 参数） */
  const refreshDetail = async () => {
    if (!selectedBusinessUnit.value?.id) return
    try {
      const detail = await organizationApi.getById(selectedBusinessUnit.value.id)
      selectedBusinessUnit.value = detail
    } catch {
      /* 保持当前选中 */
    }
  }

  // ==================== Return ====================

  return {
    // State
    treeRef,
    filterText,
    selectedBusinessUnit,
    businessUnitMembers,
    businessUnitApprovers,
    dialogVisible,
    rolesDialogVisible,
    approversDialogVisible,
    membersDialogVisible,
    userDetailVisible,
    selectedUserId,
    currentBusinessUnit,
    parentBusinessUnit,
    // Pure functions
    filterNode,
    // Methods
    fetchMembers,
    fetchApprovers,
    handleNodeClick,
    handleNodeDrop,
    handleFormSuccess,
    handleDelete,
    showCreateDialog,
    showEditDialog,
    showRolesDialog,
    showApproversDialog,
    showMembersDialog,
    showUserDetail,
    handleMembersChange,
    refreshDetail,
  }
}
