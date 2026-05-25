/**
 * Business Unit Members 业务逻辑 composable
 */

import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifySuccess, notifyError } from '@/utils/notify'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { businessUnitApi, type BusinessUnit } from '@/api/businessUnit'
import { userApi, type User } from '@/api/user'

export function useBusinessUnitMembers(businessUnit: Ref<BusinessUnit | null>) {
  const { t } = useI18n()
  const terr = (code: string) => t(errorTranslator(code))

  const loading = ref(false)
  const searchLoading = ref(false)
  const members = ref<any[]>([])
  const searchResults = ref<User[]>([])
  const selectedUserId = ref('')
  const defaultUsersLoaded = ref(false)

  const fetchMembers = async () => {
    if (!businessUnit.value) return
    loading.value = true
    try {
      const result = await businessUnitApi.getMembers(businessUnit.value.id, { page: 0, size: 100 })
      members.value = result.content || []
    } catch {
      notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED))
    } finally {
      loading.value = false
    }
  }

  const loadDefaultUsers = async () => {
    if (defaultUsersLoaded.value || searchResults.value.length > 0) return
    searchLoading.value = true
    try {
      const result = await userApi.list({ size: 20 })
      const memberIds = new Set(members.value.map(m => m.id))
      searchResults.value = result.content.filter(u => !memberIds.has(u.id))
      defaultUsersLoaded.value = true
    } catch { /* silent */ } finally {
      searchLoading.value = false
    }
  }

  const searchUsers = async (query: string) => {
    if (!query) { loadDefaultUsers(); return }
    searchLoading.value = true
    try {
      const result = await userApi.list({ keyword: query, size: 20 })
      const memberIds = new Set(members.value.map(m => m.id))
      searchResults.value = result.content.filter(u => !memberIds.has(u.id))
    } catch { /* silent */ } finally {
      searchLoading.value = false
    }
  }

  const addMember = async () => {
    if (!businessUnit.value || !selectedUserId.value) return false
    try {
      await businessUnitApi.addMember(businessUnit.value.id, selectedUserId.value)
      notifySuccess(t('common.success'))
      selectedUserId.value = ''
      searchResults.value = []
      defaultUsersLoaded.value = false
      await fetchMembers()
      return true
    } catch {
      notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED))
      return false
    }
  }

  const removeMember = async (member: any) => {
    try {
      await businessUnitApi.removeMember(businessUnit.value!.id, member.id)
      notifySuccess(t('common.success'))
      await fetchMembers()
      return true
    } catch {
      notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED))
      return false
    }
  }

  const resetDialog = () => {
    defaultUsersLoaded.value = false
    searchResults.value = []
    selectedUserId.value = ''
  }

  return {
    loading, searchLoading, members, searchResults, selectedUserId,
    fetchMembers, loadDefaultUsers, searchUsers, addMember, removeMember, resetDialog,
  }
}
