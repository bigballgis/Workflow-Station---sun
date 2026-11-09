/**
 * Virtual Group Members 业务逻辑 composable
 *
 * 成员按名单列出（团队有 Developer、Technical Lead 两份，其他组只有成员），每项带来源（AD / 人工）。
 * 这里只加、撤人工成员；AD 来源由 AD 同步维护（平台访问模型 §5.5）。
 */
import { ref, reactive, computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifySuccess, notifyError, notifyConfirm, notifyWarning } from '@/utils/notify'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { virtualGroupApi, type MemberListType, type VirtualGroup, type VirtualGroupMember } from '@/api/virtualGroup'
import { userApi, type User } from '@/api/user'

const LIST_LABEL_KEYS: Record<MemberListType, string> = {
  MEMBER: 'virtualGroup.listMember',
  DEVELOPER: 'virtualGroup.listDeveloper',
  TECH_LEAD: 'virtualGroup.listTechLead',
}

export function useVirtualGroupMembers(group: Ref<VirtualGroup | null>) {
  const { t } = useI18n()
  const terr = (code: string) => t(errorTranslator(code))

  const loading = ref(false)
  const members = ref<VirtualGroupMember[]>([])
  const showAddDialog = ref(false)
  const addLoading = ref(false)
  const searchLoading = ref(false)
  const userOptions = ref<User[]>([])
  const newMember = reactive({ userId: '', listType: 'MEMBER' as MemberListType })

  const lists = computed<MemberListType[]>(() =>
    group.value?.type === 'DEVELOPER' ? ['DEVELOPER', 'TECH_LEAD'] : ['MEMBER'])
  const activeList = ref<MemberListType>('MEMBER')
  const listMembers = computed(() => members.value.filter(m => m.listType === activeList.value))
  const listLabel = (list: MemberListType) => t(LIST_LABEL_KEYS[list])
  const countOf = (list: MemberListType) => members.value.filter(m => m.listType === list).length

  const loadMembers = async () => {
    if (!group.value) return
    if (!lists.value.includes(activeList.value)) activeList.value = lists.value[0]
    loading.value = true
    try { members.value = await virtualGroupApi.getMembers(group.value.id) }
    catch { members.value = []; notifyError(terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED)) }
    finally { loading.value = false }
  }

  const openAddDialog = () => {
    newMember.userId = ''; newMember.listType = activeList.value; userOptions.value = []; showAddDialog.value = true
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

  /** Revokes the manual membership of one list; an AD membership of the same list stays. */
  const revokeManual = async (member: VirtualGroupMember) => {
    const name = member.fullName || member.username
    const message = group.value?.systemAdministrators
      ? t('virtualGroup.adminRevokeConfirm', { name })
      : t('virtualGroup.revokeManualConfirm', { name })
    try { await notifyConfirm(message, t('common.confirm'), { type: 'warning' }) }
    catch { return }
    try {
      await virtualGroupApi.removeMember(group.value!.id, member.userId, member.listType)
      notifySuccess(t('common.success'))
      await loadMembers()
    } catch (e: unknown) { const msg = e instanceof Error ? e.message : undefined; notifyError(msg || terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED)) }
  }

  const addMember = async () => {
    if (!newMember.userId) { notifyWarning(t('role.selectUser')); return }
    if (group.value?.systemAdministrators) {
      const user = userOptions.value.find(u => u.id === newMember.userId)
      const name = user ? user.fullName || user.username : newMember.userId
      try { await notifyConfirm(t('virtualGroup.adminAddConfirm', { name }), t('common.confirm'), { type: 'warning' }) }
      catch { return }
    }
    addLoading.value = true
    try {
      await virtualGroupApi.addMember(group.value!.id, { userId: newMember.userId, listType: newMember.listType })
      showAddDialog.value = false
      activeList.value = newMember.listType
      notifySuccess(t('common.success'))
      await loadMembers()
    } catch (e: unknown) { const msg = e instanceof Error ? e.message : undefined; notifyError(msg || terr(AppErrorCode.BUSINESS_UNIT_OPERATION_FAILED)) }
    finally { addLoading.value = false }
  }

  return { loading, members, showAddDialog, addLoading, searchLoading, userOptions, newMember,
    lists, activeList, listMembers, listLabel, countOf,
    loadMembers, openAddDialog, loadDefaultUsers, searchUsers, addMember, revokeManual }
}
