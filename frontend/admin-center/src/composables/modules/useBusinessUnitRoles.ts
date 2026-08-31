/**
 * Business Unit Roles 业务逻辑 composable
 */

import { ref, computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifySuccess, notifyError, notifyConfirm } from '@/utils/notify'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { businessUnitApi, type BusinessUnit, type RoleLeaderGroup } from '@/api/businessUnit'
import { roleApi, type Role } from '@/api/role'
import { unwrapApiData } from '@/utils/apiResponse'

export function useBusinessUnitRoles(businessUnit: Ref<BusinessUnit | null>) {
  const { t } = useI18n()
  const terr = (code: string) => t(errorTranslator(code))

  const loading = ref(false)
  const boundRoles = ref<any[]>([])
  const roleLeaders = ref<Record<string, string>>({})
  const allRoles = ref<Role[]>([])
  const selectedRoleId = ref('')

  const availableRoles = computed(() => {
    const boundIds = new Set(boundRoles.value.map((r: any) => r.id))
    return allRoles.value.filter(r => r.status === 'ACTIVE' && r.type === 'BU_BOUNDED' && !boundIds.has(r.id))
  })

  const fetchRoles = async () => {
    if (!businessUnit.value) return
    loading.value = true
    try {
      const [roles, bound, leaders] = await Promise.all([
        roleApi.list({ size: 9999 }).then(r => r.content),
        businessUnitApi.getBoundRoles(businessUnit.value.id),
        // FALLBACK(ux): leaders column is informational; missing leaders must not block eligible-role editing
        businessUnitApi.getRoleLeaders(businessUnit.value.id)
          .then((body) => {
            const list = unwrapApiData<RoleLeaderGroup[]>(body)
            return Array.isArray(list) ? list : []
          })
          .catch(() => [] as RoleLeaderGroup[]),
      ])
      allRoles.value = roles
      boundRoles.value = bound
      const map: Record<string, string> = {}
      for (const group of leaders) {
        const names = (group.leaders || [])
          .map(u => u.userFullName || u.userName || u.userId)
          .filter(Boolean)
        map[group.roleId] = names.length ? names.join(', ') : ''
      }
      roleLeaders.value = map
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

  return { loading, boundRoles, allRoles, availableRoles, selectedRoleId, roleLeaders, fetchRoles, bindRole, unbindRole }
}
