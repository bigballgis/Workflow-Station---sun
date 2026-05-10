/**
 * Business Unit Roles 业务逻辑 composable
 */

import { ref, computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifySuccess, notifyError, notifyConfirm } from '@/utils/notify'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { businessUnitApi, type BusinessUnit } from '@/api/businessUnit'
import { roleApi, type Role } from '@/api/role'

export function useBusinessUnitRoles(businessUnit: Ref<BusinessUnit | null>) {
  const { t } = useI18n()
  const terr = (code: string) => t(errorTranslator(code))

  const loading = ref(false)
  const boundRoles = ref<any[]>([])
  const allRoles = ref<Role[]>([])
  const selectedRoleId = ref('')

  const availableRoles = computed(() => {
    const boundIds = new Set(boundRoles.value.map((r: any) => r.id))
    return allRoles.value.filter(r => r.type === 'BU_BOUNDED' && !boundIds.has(r.id))
  })

  const fetchRoles = async () => {
    if (!businessUnit.value) return
    loading.value = true
    try {
      const [roles, bound] = await Promise.all([
        roleApi.list(),
        businessUnitApi.getBoundRoles(businessUnit.value.id)
      ])
      allRoles.value = roles
      boundRoles.value = bound
    } catch {
      notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED))
    } finally {
      loading.value = false
    }
  }

  const bindRole = async () => {
    if (!businessUnit.value || !selectedRoleId.value) return
    try {
      await businessUnitApi.bindRole(businessUnit.value.id, selectedRoleId.value)
      notifySuccess(t('common.success'))
      selectedRoleId.value = ''
      await fetchRoles()
    } catch {
      notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED))
    }
  }

  const unbindRole = async (role: any) => {
    if (!businessUnit.value) return
    try { await notifyConfirm(t('common.confirm'), t('common.confirm'), { type: 'warning' }) }
    catch { return }
    try {
      await businessUnitApi.unbindRole(businessUnit.value.id, role.id)
      notifySuccess(t('common.success'))
      await fetchRoles()
    } catch {
      notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED))
    }
  }

  return { loading, boundRoles, allRoles, availableRoles, selectedRoleId, fetchRoles, bindRole, unbindRole }
}
