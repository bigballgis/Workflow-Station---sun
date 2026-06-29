/**
 * User Detail 业务逻辑 composable
 */
import { ref, reactive, computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifySuccess, notifyError, notifyWarning, notifyConfirm } from '@/utils/notify'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { userApi, type UserDetail, type UserBusinessUnitMembership, type UserVirtualGroupMembership, type UserBusinessUnitRole } from '@/api/user'
import { listAssignableBuBoundedRoles, type BuBoundedRole } from '@/api/taskAssignment'

export function useUserDetail(userId: Ref<string>) {
  const { t } = useI18n()
  const terr = (code: string) => t(errorTranslator(code))

  const loading = ref(false)
  const detailActiveTab = ref<'portal' | 'platform'>('portal')
  const user = ref<UserDetail | null>(null)
  const businessUnits = ref<UserBusinessUnitMembership[]>([])
  const portalVirtualGroups = ref<UserVirtualGroupMembership[]>([])
  const platformVirtualGroups = ref<UserVirtualGroupMembership[]>([])
  const platformRoles = ref<{ id: string; name: string; code: string; type: string }[]>([])
  const buRoles = ref<UserBusinessUnitRole[]>([])

  const getPlatformRoleTagType = (type?: string) => {
    if (type === 'BU_BOUNDED') return 'warning'
    if (type === 'BU_UNBOUNDED') return 'success'
    if (type === 'ADMIN') return 'danger'
    if (type === 'DEVELOPER') return 'primary'
    return 'info'
  }

  const assignDialogVisible = ref(false)
  const assignRoleLoading = ref(false)
  const assignSubmitting = ref(false)
  const assignRoleOptions = ref<BuBoundedRole[]>([])
  const assignRoleLoaded = ref(false)
  const assignForm = reactive({ businessUnitId: '', roleId: '' })

  const buRoleGroups = computed(() => {
    const map = new Map<string, { businessUnitId: string; businessUnitName: string; rows: UserBusinessUnitRole[] }>()
    for (const r of buRoles.value) {
      const k = r.businessUnitId
      if (!map.has(k)) map.set(k, { businessUnitId: k, businessUnitName: r.businessUnitName || k, rows: [] })
      map.get(k)!.rows.push(r)
    }
    return [...map.values()]
  })

  const statusType = (s: string): 'success'|'info'|'danger'|'warning' => {
    const map: Record<string, 'success'|'info'|'danger'|'warning'> = { ACTIVE: 'success', DISABLED: 'info', LOCKED: 'danger', PENDING: 'warning' }
    return map[s] || 'info'
  }
  const statusText = (s: string) => {
    const map: Record<string, string> = { ACTIVE: t('user.active'), DISABLED: t('user.disabled'), LOCKED: t('user.locked'), PENDING: t('user.pending') }
    return map[s] || s
  }
  const formatDate = (d: string) => d ? new Date(d).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-'

  const loadDetail = async () => {
    if (!userId.value) return
    loading.value = true
    try {
      const [ud, bu, pvg, plvg, pr, ubr] = await Promise.all([
        userApi.getById(userId.value), userApi.getBusinessUnits(userId.value),
        userApi.getVirtualGroups(userId.value, 'PORTAL'), userApi.getVirtualGroups(userId.value, 'ADMIN'),
        userApi.getRoles(userId.value, 'ADMIN'), userApi.getBusinessUnitRoles(userId.value),
      ])
      user.value = ud; businessUnits.value = bu; portalVirtualGroups.value = pvg; platformVirtualGroups.value = plvg
      platformRoles.value = pr || []; buRoles.value = ubr
    } catch (e: unknown) { const msg = e instanceof Error ? e.message : undefined; notifyError(msg || terr(AppErrorCode.USER_ACTION_FAILED)) }
    finally { loading.value = false }
  }

  const reloadBuRoles = async () => { if (userId.value) buRoles.value = await userApi.getBusinessUnitRoles(userId.value) }

  const resetAssignDialog = () => { assignForm.businessUnitId = ''; assignForm.roleId = ''; assignRoleOptions.value = []; assignRoleLoaded.value = false }

  const onAssignBuChange = async () => {
    const buId = assignForm.businessUnitId; assignForm.roleId = ''; assignRoleOptions.value = []; assignRoleLoaded.value = false
    if (!buId) return
    // eligible-roles 接口按 BU code 查询（任务分配链路统一 code），而表单/分配接口用 BU id
    const buCode = businessUnits.value.find(b => b.id === buId)?.code
    if (!buCode) { assignRoleLoaded.value = true; return }
    assignRoleLoading.value = true
    try {
      const assignable = await listAssignableBuBoundedRoles(buCode)
      const taken = new Set(buRoles.value.filter(r => r.businessUnitId === buId).map(r => r.roleId))
      assignRoleOptions.value = assignable.filter(r => !taken.has(r.id))
    } catch (e: unknown) { const msg = e instanceof Error ? e.message : undefined; notifyError(msg || terr(AppErrorCode.USER_ACTION_FAILED)) }
    finally { assignRoleLoading.value = false; assignRoleLoaded.value = true }
  }

  const openAssignBuRole = async () => {
    if (!businessUnits.value.length) { notifyWarning(t('user.assignBuRoleNeedMembership')); return }
    resetAssignDialog(); assignForm.businessUnitId = businessUnits.value[0]!.id; assignDialogVisible.value = true
    await onAssignBuChange()
  }

  const submitAssignBuRole = async () => {
    if (!user.value || !assignForm.businessUnitId || !assignForm.roleId) { notifyWarning(t('user.selectRoleForBu')); return }
    assignSubmitting.value = true
    try {
      await userApi.assignBusinessUnitRole(user.value.id, assignForm.businessUnitId, assignForm.roleId)
      notifySuccess(t('common.success')); assignDialogVisible.value = false; await reloadBuRoles()
    } catch (e: unknown) { const msg = e instanceof Error ? e.message : undefined; notifyError(msg || terr(AppErrorCode.USER_ACTION_FAILED)) }
    finally { assignSubmitting.value = false }
  }

  const removeBuRole = async (row: UserBusinessUnitRole) => {
    if (!user.value) return
    try { await notifyConfirm(t('user.confirmRemoveBuRole', { role: row.roleName || row.roleCode || row.roleId }), t('common.confirm'), { type: 'warning' }) }
    catch { return }
    try { await userApi.removeBusinessUnitRole(user.value.id, row.businessUnitId, row.roleId); notifySuccess(t('common.success')); await reloadBuRoles() }
    catch (e: unknown) { const msg = e instanceof Error ? e.message : undefined; notifyError(msg || terr(AppErrorCode.USER_ACTION_FAILED)) }
  }

  const resetPassword = async () => {
    if (!user.value) return
    try { await notifyConfirm(t('user.resetPassword') + ` - ${user.value.fullName}?`, t('common.confirm'), { type: 'warning' }) }
    catch { return }
    try { await userApi.resetPassword(user.value.id); notifySuccess(t('user.passwordResetNoPlaintext')) }
    catch (e: unknown) { const msg = e instanceof Error ? e.message : undefined; notifyError(msg || terr(AppErrorCode.USER_ACTION_FAILED)) }
  }

  return { loading, detailActiveTab, user, businessUnits, portalVirtualGroups, platformVirtualGroups,
    platformRoles, buRoles, buRoleGroups, assignDialogVisible, assignRoleLoading, assignSubmitting,
    assignRoleOptions, assignRoleLoaded, assignForm,
    getPlatformRoleTagType, statusType, statusText, formatDate,
    loadDetail, reloadBuRoles, resetAssignDialog, onAssignBuChange, openAssignBuRole, submitAssignBuRole, removeBuRole, resetPassword,
  }
}
