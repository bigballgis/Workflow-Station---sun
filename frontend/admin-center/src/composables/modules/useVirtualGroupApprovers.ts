/**
 * Virtual Group Approvers 业务逻辑 composable
 */
import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifySuccess, notifyError, notifyConfirm } from '@/utils/notify'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { virtualGroupApi, type VirtualGroup, type Approver } from '@/api/virtualGroup'
import { userApi, type User } from '@/api/user'

export function useVirtualGroupApprovers(group: Ref<VirtualGroup | null>) {
  const { t } = useI18n()
  const terr = (code: string) => t(errorTranslator(code))

  const loading = ref(false)
  const searchLoading = ref(false)
  const approvers = ref<Approver[]>([])
  const searchResults = ref<User[]>([])
  const selectedUserId = ref('')
  const defaultUsersLoaded = ref(false)

  const fetchApprovers = async () => {
    if (!group.value) return
    loading.value = true
    try { approvers.value = await virtualGroupApi.getApprovers(group.value.id) }
    catch { notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED)) }
    finally { loading.value = false }
  }

  const loadDefaultUsers = async () => {
    if (defaultUsersLoaded.value || searchResults.value.length > 0) return
    searchLoading.value = true
    try {
      const result = await userApi.list({ size: 20 })
      const ids = new Set(approvers.value.map(a => a.userId))
      searchResults.value = result.content.filter(u => !ids.has(u.id))
      defaultUsersLoaded.value = true
    } catch { /* silent */ } finally { searchLoading.value = false }
  }

  const searchUsers = async (query: string) => {
    if (!query) { loadDefaultUsers(); return }
    searchLoading.value = true
    try {
      const result = await userApi.list({ keyword: query, size: 20 })
      const ids = new Set(approvers.value.map(a => a.userId))
      searchResults.value = result.content.filter(u => !ids.has(u.id))
    } catch { /* silent */ } finally { searchLoading.value = false }
  }

  const addApprover = async () => {
    if (!group.value || !selectedUserId.value) return
    try {
      await virtualGroupApi.addApprover(group.value.id, selectedUserId.value)
      notifySuccess(t('common.success'))
      selectedUserId.value = ''; searchResults.value = []; defaultUsersLoaded.value = false
      await fetchApprovers()
    } catch { notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED)) }
  }

  const removeApprover = async (approver: Approver) => {
    try { await notifyConfirm(t('common.confirm'), t('common.confirm'), { type: 'warning' }) }
    catch { return }
    try {
      await virtualGroupApi.removeApprover(approver.id)
      notifySuccess(t('common.success'))
      await fetchApprovers()
    } catch { notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED)) }
  }

  const resetDialog = () => { defaultUsersLoaded.value = false; searchResults.value = []; selectedUserId.value = '' }

  return { loading, searchLoading, approvers, searchResults, selectedUserId,
    fetchApprovers, loadDefaultUsers, searchUsers, addApprover, removeApprover, resetDialog }
}
