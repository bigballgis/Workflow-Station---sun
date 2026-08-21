/**
 * Virtual Group Roles 业务逻辑 composable
 */
import { ref, computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifySuccess, notifyError, notifyConfirm } from '@/utils/notify'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { virtualGroupApi, type VirtualGroup, type VirtualGroupRole } from '@/api/virtualGroup'
import { roleApi, type Role } from '@/api/role'

export function useVirtualGroupRoles(group: Ref<VirtualGroup | null>) {
  const { t } = useI18n()
  const terr = (code: string) => t(errorTranslator(code))

  const loading = ref(false)
  const boundRole = ref<VirtualGroupRole | null>(null)
  const allRoles = ref<Role[]>([])
  const selectedRoleId = ref('')

  const isSystemGroup = computed(() => group.value?.type === 'SYSTEM')
  const availableRoles = computed(() =>
    allRoles.value.filter(r => {
      const rt = r.type as string
      return r.status === 'ACTIVE'
        && (rt === 'BU_BOUNDED' || rt === 'BU_UNBOUNDED' || rt === 'BUSINESS')
        && r.id !== boundRole.value?.roleId
    })
  )

  const getRoleTypeLabel = (type?: string) => {
    const map: Record<string, string> = {
      BU_BOUNDED: t('role.buBounded'), BU_UNBOUNDED: t('role.buUnbounded'),
      BUSINESS: t('role.businessRole'), ADMIN: t('role.adminRole'), AUDITOR: t('role.auditorRole'), DEVELOPER: t('role.developerRole'),
    }
    return map[type || ''] || type || ''
  }

  const fetchRoles = async () => {
    if (!group.value) return
    loading.value = true
    try {
      const [roles, boundRoles] = await Promise.all([roleApi.list({ size: 9999 }).then(r => r.content), virtualGroupApi.getBoundRoles(group.value.id)])
      allRoles.value = roles
      boundRole.value = boundRoles.length > 0 ? boundRoles[0] : null
    } catch { notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED)) }
    finally { loading.value = false }
  }

  const bindRole = async () => {
    if (!group.value || !selectedRoleId.value) return
    if (boundRole.value) {
      try { await notifyConfirm(t('virtualGroup.replaceRoleConfirm'), t('common.confirm'), { type: 'warning' }) }
      catch { return }
    }
    try {
      await virtualGroupApi.bindRole(group.value.id, selectedRoleId.value)
      notifySuccess(t('common.success'))
      selectedRoleId.value = ''
      await fetchRoles()
    } catch { notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED)) }
  }

  const unbindRole = async () => {
    if (!group.value || !boundRole.value) return
    try { await notifyConfirm(t('virtualGroup.unbindRoleConfirm'), t('common.confirm'), { type: 'warning' }) }
    catch { return }
    try {
      await virtualGroupApi.unbindRole(group.value.id, boundRole.value.roleId)
      notifySuccess(t('common.success'))
      await fetchRoles()
    } catch { notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED)) }
  }

  return { loading, boundRole, allRoles, selectedRoleId, isSystemGroup, availableRoles,
    getRoleTypeLabel, fetchRoles, bindRole, unbindRole }
}
