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
import type Node from 'element-plus/es/components/tree/src/model/node'
import type { AllowDropType } from 'element-plus/es/components/tree/src/tree.type'

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

  /** el-tree Node（level 1 = 第一级；level 0 = 隐藏根） */
  type TreeNodeLike = Node

  /** 第一级固定按名称排序、不可拖动；只有第二级及以下可以拖 */
  const allowDrag = (node: TreeNodeLike) => node.level > 1

  /**
   * 放进任意节点内部都行（含第一级）；但不能放到第一级的前/后——那会把它变成新的第一级节点，
   * 而第一级顺序由名称决定、不由拖拽决定。
   */
  const allowDrop = (_dragging: TreeNodeLike, drop: TreeNodeLike, type: AllowDropType) =>
    type === 'inner' || drop.level > 1

  /**
   * 从 drop 落点推算新父级和新同级下标。
   * el-tree 在 emit node-drop 前已经把 draggingNode 从旧位置 remove 并在新位置 insert 了一个新 Node，
   * 所以 draggingNode.parent 是过期的，必须从 dropNode 一侧读。
   */
  const resolveDropTarget = (draggingNode: TreeNodeLike, dropNode: TreeNodeLike, dropType: string) => {
    const parent = dropType === 'inner' ? dropNode : dropNode.parent
    if (!parent) return null
    const siblings = parent.childNodes ?? []
    const sortOrder = siblings.findIndex((n) => n.data?.id === draggingNode.data.id)
    return {
      newParentId: parent.level > 0 ? parent.data.id : undefined,
      parentName: parent.level > 0 ? parent.data.name : '',
      sortOrder: sortOrder < 0 ? undefined : sortOrder,
    }
  }

  const handleNodeDrop = async (draggingNode: TreeNodeLike, dropNode: TreeNodeLike, dropType: string) => {
    if (dropType === 'none') return
    const target = resolveDropTarget(draggingNode, dropNode, dropType)
    if (!target || !target.newParentId) {
      await orgStore.fetchTree()
      return
    }
    // 同父级内仅换顺序不用确认；换父级会连带子级、成员、角色、审批人一起迁移，先确认
    const parentChanged = target.newParentId !== draggingNode.data.parentId
    if (parentChanged) {
      try {
        await notifyConfirm(
          t('organization.moveConfirm', { name: draggingNode.data.name, target: target.parentName }),
          t('common.confirm'),
          { type: 'warning' },
        )
      } catch {
        await orgStore.fetchTree() // 取消：el-tree 已本地挪动，回读服务端恢复
        return
      }
    }
    try {
      await orgStore.moveBusinessUnit(draggingNode.data.id, {
        newParentId: target.newParentId,
        sortOrder: target.sortOrder,
      })
      notifySuccess(t('common.success'))
      if (selectedBusinessUnit.value?.id === draggingNode.data.id) await refreshDetail()
    } catch {
      await orgStore.fetchTree() // 失败（成环 / 同名等，request 拦截器已弹错）：恢复服务端状态
    }
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
    } catch (e: unknown) {
      const ax = e as { response?: { data?: { message?: string } } }
      notifyError(ax.response?.data?.message || t('common.failed'))
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
    allowDrag,
    allowDrop,
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
