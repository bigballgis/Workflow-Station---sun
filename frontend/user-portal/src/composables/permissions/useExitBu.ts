import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { useI18n } from 'vue-i18n'
import { permissionApi, type UserBusinessUnitRole } from '@/api/permission'
import { getStoredUser } from '@/api/auth'

type TFn = ReturnType<typeof useI18n>['t']

export interface UseExitBuDeps {
  /** 提交成功后刷新「我的申请」进行中列表 */
  loadPendingRequests: () => void
  /** 提交成功后刷新「我的申请」已完成列表 */
  loadHistoryRequests: () => void
  /** 提交成功后刷新「我的业务单元角色」 */
  loadMyBuRoles: () => void
}

/** 「申请退出业务单元」：成员关系列表、退出对话框与提交。 */
export function useExitBu(t: TFn, deps: UseExitBuDeps) {
  const loadingExitBu = ref(false)
  const exitBuRows = ref<{ businessUnitId: string; businessUnitName: string; joinedAt?: string }[]>([])
  const exitBuDialogVisible = ref(false)
  const exitBuSubmitting = ref(false)
  const loadingExitBuBeneficiarySearch = ref(false)
  const exitBuBeneficiaryOptions = ref<{ userId: string; username: string; displayName?: string }[]>([])
  const exitBuForm = reactive({
    businessUnitId: '',
    businessUnitName: '',
    beneficiaryUserId: '' as string,
    reason: ''
  })

  const loadExitBuMemberships = async () => {
    loadingExitBu.value = true
    try {
      const res = await permissionApi.getMyMemberships()
      const data = (res as any)?.data?.data || (res as any)?.data || res
      const buMap = new Map<string, { businessUnitId: string; businessUnitName: string; joinedAt?: string }>()
      if (data?.businessUnitRoles) {
        for (const role of data.businessUnitRoles as UserBusinessUnitRole[]) {
          if (!buMap.has(role.businessUnitId)) {
            buMap.set(role.businessUnitId, {
              businessUnitId: role.businessUnitId,
              businessUnitName: role.businessUnitName,
              joinedAt: role.assignedAt
            })
          }
        }
      }
      if (data?.businessUnits) {
        for (const bu of data.businessUnits as { businessUnitId?: string; id?: string; businessUnitName?: string; name?: string; joinedAt?: string }[]) {
          const id = bu.businessUnitId || bu.id
          if (id && !buMap.has(id)) {
            buMap.set(id, {
              businessUnitId: id,
              businessUnitName: bu.businessUnitName || bu.name || id,
              joinedAt: bu.joinedAt
            })
          }
        }
      }
      exitBuRows.value = Array.from(buMap.values())
    } catch (e) {
      console.error('Failed to load exit BU memberships:', e)
      exitBuRows.value = []
    } finally {
      loadingExitBu.value = false
    }
  }

  const searchExitBuBeneficiaries = async (query: string) => {
    loadingExitBuBeneficiarySearch.value = true
    try {
      const res = (await permissionApi.searchUsersForDelegation({
        keyword: query || undefined,
        page: 0,
        size: 20
      })) as any
      const payload = res?.data ?? res
      exitBuBeneficiaryOptions.value = Array.isArray(payload?.content) ? payload.content : []
    } catch {
      exitBuBeneficiaryOptions.value = []
    } finally {
      loadingExitBuBeneficiarySearch.value = false
    }
  }

  const openExitBuDialog = (row: { businessUnitId: string; businessUnitName: string }) => {
    exitBuForm.businessUnitId = row.businessUnitId
    exitBuForm.businessUnitName = row.businessUnitName
    exitBuForm.beneficiaryUserId = ''
    exitBuForm.reason = ''
    exitBuBeneficiaryOptions.value = []
    exitBuDialogVisible.value = true
  }

  const submitExitBu = async () => {
    if (!exitBuForm.reason.trim()) {
      ElMessage.warning(t('permission.enterReason'))
      return
    }
    try {
      await ElMessageBox.confirm(
        t('exitRole.exitBuConfirm', { bu: exitBuForm.businessUnitName || exitBuForm.businessUnitId }),
        t('common.confirm'),
        { type: 'warning' }
      )
    } catch {
      return
    }
    exitBuSubmitting.value = true
    try {
      const body: { businessUnitId: string; reason: string; beneficiaryUserId?: string } = {
        businessUnitId: exitBuForm.businessUnitId,
        reason: exitBuForm.reason.trim()
      }
      const me = getStoredUser()?.userId
      if (exitBuForm.beneficiaryUserId && exitBuForm.beneficiaryUserId !== me) {
        body.beneficiaryUserId = exitBuForm.beneficiaryUserId
      }
      await permissionApi.requestBusinessUnitExit(body)
      ElMessage.success(t('exitRole.exitRequestSuccess'))
      exitBuDialogVisible.value = false
      loadExitBuMemberships()
      deps.loadPendingRequests()
      deps.loadHistoryRequests()
      deps.loadMyBuRoles()
    } catch (e: unknown) {
      const err = e as { message?: string }
      ElMessage.error(err.message || t('exitRole.exitFailed'))
    } finally {
      exitBuSubmitting.value = false
    }
  }

  return {
    loadingExitBu,
    exitBuRows,
    exitBuDialogVisible,
    exitBuSubmitting,
    loadingExitBuBeneficiarySearch,
    exitBuBeneficiaryOptions,
    exitBuForm,
    loadExitBuMemberships,
    searchExitBuBeneficiaries,
    openExitBuDialog,
    submitExitBu
  }
}
