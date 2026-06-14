import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { useI18n } from 'vue-i18n'
import {
  permissionApi,
  type FunctionUnitRemovalGroup,
  type RemovalOptionsByFunctionUnitPayload
} from '@/api/permission'

type TFn = ReturnType<typeof useI18n>['t']

export interface UseRemovePermissionDeps {
  /** 构造去重选择键，与展示层共用同一实现 */
  rowRemovalKey: (businessUnitId: string, roleId: string) => string
  /** 提交成功后刷新「我的申请」进行中列表 */
  loadPendingRequests: () => void
  /** 提交成功后刷新「我的申请」已完成列表 */
  loadHistoryRequests: () => void
  /** 提交成功后刷新「我的业务单元角色」 */
  loadMyBuRoles: () => void
  /** 提交成功后刷新「退出业务单元」成员关系 */
  loadExitBuMemberships: () => void
}

/** 「按功能单元移除 BU 角色」对话框：选项加载、勾选切换、批量提交。 */
export function useRemovePermission(t: TFn, deps: UseRemovePermissionDeps) {
  const { rowRemovalKey } = deps

  const removePermissionDialogVisible = ref(false)
  const removalBeneficiaryUserId = ref('')
  const removalBeneficiaryOptions = ref<{ userId: string; username: string; displayName?: string }[]>([])
  const loadingRemovalBeneficiarySearch = ref(false)
  const removalPayload = ref<RemovalOptionsByFunctionUnitPayload | null>(null)
  const loadingRemovalOptions = ref(false)
  const selectedRemovalKeys = ref<string[]>([])
  const activeFuCollapseNames = ref<string[]>([])
  const removePermissionReason = ref('')
  const submittingRemovalBatch = ref(false)

  const totalRemovableCount = computed(() => {
    const p = removalPayload.value
    if (!p) return 0
    const inFu = p.functionUnitGroups.reduce((n, g) => n + g.assignments.length, 0)
    return inFu + p.otherAssignments.length
  })

  const groupCheckState = (group: FunctionUnitRemovalGroup) => {
    const keys = group.assignments.map((x) => rowRemovalKey(x.businessUnitId, x.roleId))
    const n = keys.filter((k) => selectedRemovalKeys.value.includes(k)).length
    return {
      checked: n === keys.length && n > 0,
      indeterminate: n > 0 && n < keys.length
    }
  }

  const toggleRemovalKey = (key: string, on: boolean) => {
    const s = new Set(selectedRemovalKeys.value)
    if (on) s.add(key)
    else s.delete(key)
    selectedRemovalKeys.value = [...s]
  }

  const toggleGroupAll = (group: FunctionUnitRemovalGroup, checked: boolean) => {
    const s = new Set(selectedRemovalKeys.value)
    for (const a of group.assignments) {
      const k = rowRemovalKey(a.businessUnitId, a.roleId)
      if (checked) s.add(k)
      else s.delete(k)
    }
    selectedRemovalKeys.value = [...s]
  }

  const toggleOtherAll = (checked: boolean) => {
    const p = removalPayload.value
    if (!p) return
    const s = new Set(selectedRemovalKeys.value)
    for (const a of p.otherAssignments) {
      const k = rowRemovalKey(a.businessUnitId, a.roleId)
      if (checked) s.add(k)
      else s.delete(k)
    }
    selectedRemovalKeys.value = [...s]
  }

  const searchRemovalBeneficiaries = async (query: string) => {
    loadingRemovalBeneficiarySearch.value = true
    try {
      const res = (await permissionApi.searchUsersForDelegation({
        keyword: query || undefined,
        page: 0,
        size: 20
      })) as any
      const payload = res?.data ?? res
      removalBeneficiaryOptions.value = Array.isArray(payload?.content) ? payload.content : []
    } catch {
      removalBeneficiaryOptions.value = []
    } finally {
      loadingRemovalBeneficiarySearch.value = false
    }
  }

  const openRemovePermissionDialog = () => {
    removalBeneficiaryUserId.value = ''
    removalBeneficiaryOptions.value = []
    removalPayload.value = null
    selectedRemovalKeys.value = []
    activeFuCollapseNames.value = []
    removePermissionReason.value = ''
    removePermissionDialogVisible.value = true
  }

  const loadRemovalOptions = async () => {
    loadingRemovalOptions.value = true
    try {
      const res = (await permissionApi.getRemovalOptionsByFunctionUnit(
        removalBeneficiaryUserId.value || undefined
      )) as any
      const data = res?.data ?? res
      const payload: RemovalOptionsByFunctionUnitPayload = {
        functionUnitGroups: Array.isArray(data?.functionUnitGroups) ? data.functionUnitGroups : [],
        otherAssignments: Array.isArray(data?.otherAssignments) ? data.otherAssignments : []
      }
      removalPayload.value = payload
      selectedRemovalKeys.value = []
      activeFuCollapseNames.value = payload.functionUnitGroups.map((g) => g.functionUnitId)
    } catch (e: any) {
      const msg = e.response?.data?.message || e.message || t('permission.requestRemoveBuRoleFailed')
      ElMessage.error(msg)
      removalPayload.value = { functionUnitGroups: [], otherAssignments: [] }
    } finally {
      loadingRemovalOptions.value = false
    }
  }

  const submitRemovalBatch = async () => {
    if (!removePermissionReason.value.trim()) {
      ElMessage.warning(t('permission.enterReason'))
      return
    }
    if (selectedRemovalKeys.value.length === 0) {
      return
    }
    const reason = removePermissionReason.value.trim()
    const beneficiary = removalBeneficiaryUserId.value || undefined
    submittingRemovalBatch.value = true
    let ok = 0
    let fail = 0
    try {
      for (const key of selectedRemovalKeys.value) {
        const sep = key.indexOf('::')
        if (sep < 0) continue
        const businessUnitId = key.slice(0, sep)
        const roleId = key.slice(sep + 2)
        try {
          await permissionApi.requestBusinessUnitRoleRemoval({
            businessUnitId,
            roleId,
            reason,
            beneficiaryUserId: beneficiary
          })
          ok++
        } catch {
          fail++
        }
      }
      if (ok > 0) {
        ElMessage.success(t('permission.requestRemoveBuRoleSuccess'))
        removePermissionDialogVisible.value = false
        deps.loadPendingRequests()
        deps.loadHistoryRequests()
        deps.loadMyBuRoles()
        deps.loadExitBuMemberships()
        if (fail > 0) {
          ElMessage.warning(t('permission.removalPartialFailures'))
        }
      } else if (fail > 0) {
        ElMessage.error(t('permission.requestRemoveBuRoleFailed'))
      }
    } finally {
      submittingRemovalBatch.value = false
    }
  }

  return {
    removePermissionDialogVisible,
    removalBeneficiaryUserId,
    removalBeneficiaryOptions,
    loadingRemovalBeneficiarySearch,
    removalPayload,
    loadingRemovalOptions,
    selectedRemovalKeys,
    activeFuCollapseNames,
    removePermissionReason,
    submittingRemovalBatch,
    totalRemovableCount,
    groupCheckState,
    toggleRemovalKey,
    toggleGroupAll,
    toggleOtherAll,
    searchRemovalBeneficiaries,
    openRemovePermissionDialog,
    loadRemovalOptions,
    submitRemovalBatch
  }
}
