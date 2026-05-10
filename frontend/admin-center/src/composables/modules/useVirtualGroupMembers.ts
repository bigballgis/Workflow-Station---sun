/**
 * Virtual Group Members 业务逻辑 composable
 */
import { ref, reactive, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifySuccess, notifyError, notifyConfirm, notifyWarning } from '@/utils/notify'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { virtualGroupApi, type VirtualGroupMember } from '@/api/virtualGroup'
import { userApi, type User } from '@/api/user'

export function useVirtualGroupMembers(group: Ref<any>) {
  const { t } = useI18n()
  const terr = (code: string) => t(errorTranslator(code))

  const loading = ref(false)
  const members = ref<VirtualGroupMember[]>([])
  const showAddDialog = ref(false)
  const addLoading = ref(false)
  const searchLoading = ref(false)
  const userOptions = ref<User[]>([])
  const newMember = reactive({ userId: '', role: 'MEMBER' as const })

  const loadMembers = async () => {
    if (!group.value) return
    loading.value = true
    try { members.value = await virtualGroupApi.getMembers(group.value.id) }
    catch { members.value = []; notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED)) }
    finally { loading.value = false }
  }

  const openAddDialog = () => {
    newMember.userId = ''; newMember.role = 'MEMBER'; userOptions.value = []; showAddDialog.value = true
  }

  const loadDefaultUsers = async () => {
    if (userOptions.value.length > 0) return
    searchLoading.value = true
    try { userOptions.value = (await userApi.list({ size: 20 })).content }
    catch { /* silent */ } finally { searchLoading.value = false }
  }

  const searchUsers = async (query: string) => {
    if (!query) { await loadDefaultUsers(); return }
    searchLoading.value = true
    try { userOptions.value = (await userApi.list({ keyword: query, size: 20 })).content }
    catch { /* silent */ } finally { searchLoading.value = false }
  }

  const removeMember = async (member: VirtualGroupMember) => {
    try { await notifyConfirm(t('common.confirm'), t('common.confirm'), { type: 'warning' }) }
    catch (e: unknown) { if (e === 'cancel') return }
    try {
      await virtualGroupApi.removeMember(group.value.id, member.userId)
      notifySuccess(t('common.success'))
      await loadMembers()
    } catch (e: unknown) { const msg = e instanceof Error ? e.message : undefined; notifyError(msg || terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED)) }
  }

  const addMember = async () => {
    if (!newMember.userId) { notifyWarning(t('role.selectUser')); return }
    addLoading.value = true
    try {
      await virtualGroupApi.addMember(group.value.id, { userId: newMember.userId, role: newMember.role })
      showAddDialog.value = false
      notifySuccess(t('common.success'))
      await loadMembers()
    } catch (e: unknown) { const msg = e instanceof Error ? e.message : undefined; notifyError(msg || terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED)) }
    finally { addLoading.value = false }
  }

  return { loading, members, showAddDialog, addLoading, searchLoading, userOptions, newMember,
    loadMembers, openAddDialog, loadDefaultUsers, searchUsers, addMember, removeMember }
}
