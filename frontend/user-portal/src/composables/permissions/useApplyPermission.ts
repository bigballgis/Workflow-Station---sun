import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import type { useI18n } from 'vue-i18n'
import { permissionApi, type BusinessUnit, type RoleInfo } from '@/api/permission'

type TFn = ReturnType<typeof useI18n>['t']

export interface UseApplyPermissionDeps {
  /** 提交成功后刷新「我的申请」进行中列表 */
  loadPendingRequests: () => void
  /** 提交成功后刷新「我的申请」已完成列表 */
  loadHistoryRequests: () => void
}

/** 「申请权限」对话框：业务单元 / 角色加载、受益人搜索、表单提交。 */
export function useApplyPermission(t: TFn, deps: UseApplyPermissionDeps) {
  const applyDialogVisible = ref(false)
  const submitting = ref(false)
  const loadingBusinessUnits = ref(false)
  const loadingRoles = ref(false)
  const loadingBeneficiarySearch = ref(false)
  const beneficiaryOptions = ref<{ userId: string; username: string; displayName?: string }[]>([])
  const applicableBusinessUnits = ref<BusinessUnit[]>([])
  const eligibleRoles = ref<RoleInfo[]>([])

  // 申请表单
  const applyForm = reactive({
    beneficiaryUserId: '' as string,
    businessUnitId: '',
    roleId: '',
    reason: ''
  })

  const loadApplicableBusinessUnits = async () => {
    loadingBusinessUnits.value = true
    try {
      const res = await permissionApi.getApplicableBusinessUnits() as any
      // axios 拦截器返回 response.data，即 ApiResponse { success, data: [...] }
      if (res?.data && Array.isArray(res.data)) {
        applicableBusinessUnits.value = res.data
      } else if (Array.isArray(res)) {
        applicableBusinessUnits.value = res
      } else {
        applicableBusinessUnits.value = []
      }
      if (applicableBusinessUnits.value.length === 0) {
        const cat = await permissionApi.getBusinessUnits() as any
        const raw = cat?.data ?? cat
        if (Array.isArray(raw)) {
          applicableBusinessUnits.value = raw.map((b: BusinessUnit) => ({
            id: b.id,
            name: b.name || b.id
          })) as BusinessUnit[]
        }
      }
    } catch (e) {
      console.error('Failed to load applicable business units:', e)
      applicableBusinessUnits.value = []
    } finally {
      loadingBusinessUnits.value = false
    }
  }

  const searchBeneficiaryUsers = async (query: string) => {
    loadingBeneficiarySearch.value = true
    try {
      const res = (await permissionApi.searchUsersForDelegation({
        keyword: query || undefined,
        page: 0,
        size: 20
      })) as any
      const payload = res?.data ?? res
      beneficiaryOptions.value = Array.isArray(payload?.content) ? payload.content : []
    } catch {
      beneficiaryOptions.value = []
    } finally {
      loadingBeneficiarySearch.value = false
    }
  }

  const loadEligibleRoles = async (businessUnitId: string) => {
    if (!businessUnitId) {
      eligibleRoles.value = []
      return
    }
    loadingRoles.value = true
    try {
      const res = await permissionApi.getBusinessUnitRoles(businessUnitId) as any
      if (res?.data && Array.isArray(res.data)) {
        eligibleRoles.value = res.data
      } else if (Array.isArray(res)) {
        eligibleRoles.value = res
      } else {
        eligibleRoles.value = []
      }
    } catch (e) {
      console.error('Failed to load eligible roles:', e)
      eligibleRoles.value = []
    } finally {
      loadingRoles.value = false
    }
  }

  // 对话框操作
  const showApplyDialog = () => {
    applyForm.beneficiaryUserId = ''
    applyForm.businessUnitId = ''
    applyForm.roleId = ''
    applyForm.reason = ''
    beneficiaryOptions.value = []
    eligibleRoles.value = []
    applyDialogVisible.value = true

    loadApplicableBusinessUnits()
  }

  const onBusinessUnitChange = async (businessUnitId: string) => {
    applyForm.roleId = ''
    await loadEligibleRoles(businessUnitId)
  }

  const submitApply = async () => {
    if (!applyForm.businessUnitId) {
      ElMessage.warning(t('permission.selectBusinessUnit'))
      return
    }

    if (!applyForm.roleId) {
      ElMessage.warning(t('permission.selectRole'))
      return
    }

    if (!applyForm.reason.trim()) {
      ElMessage.warning(t('permission.enterReason'))
      return
    }

    submitting.value = true
    try {
      const payload: Record<string, unknown> = {
        businessUnitId: applyForm.businessUnitId,
        roleIds: [applyForm.roleId],
        reason: applyForm.reason.trim()
      }
      if (applyForm.beneficiaryUserId) {
        payload.beneficiaryUserId = applyForm.beneficiaryUserId
      }
      await permissionApi.requestBusinessUnitRole(payload as any)
      ElMessage.success(t('permission.businessUnitRequestSuccess'))

      applyDialogVisible.value = false
      deps.loadPendingRequests()
      deps.loadHistoryRequests()
    } catch (e: any) {
      const msg = e.response?.data?.message || e.message || t('permission.requestFailed')
      ElMessage.error(msg)
    } finally {
      submitting.value = false
    }
  }

  return {
    applyDialogVisible,
    submitting,
    loadingBusinessUnits,
    loadingRoles,
    loadingBeneficiarySearch,
    beneficiaryOptions,
    applicableBusinessUnits,
    eligibleRoles,
    applyForm,
    loadApplicableBusinessUnits,
    searchBeneficiaryUsers,
    loadEligibleRoles,
    showApplyDialog,
    onBusinessUnitChange,
    submitApply
  }
}
