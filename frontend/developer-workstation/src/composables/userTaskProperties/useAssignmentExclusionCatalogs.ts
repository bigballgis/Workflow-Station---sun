import { computed, ref, watch, type Ref } from 'vue'
import { adminCenterApi, type BusinessUnitInfo, type RoleInfo } from '@/api/adminCenter'
import { userApi, type PlatformUserOption } from '@/api/user'
import type { AssignmentExclusionDraft } from '@/types/assignmentExclusion'

export function useAssignmentExclusionCatalogs(draft: Ref<AssignmentExclusionDraft>) {
  const businessUnits = ref<BusinessUnitInfo[]>([])
  const roles = ref<RoleInfo[]>([])
  const users = ref<PlatformUserOption[]>([])
  const loading = ref(false)
  const loadingUsers = ref(false)
  const catalogError = ref(false)
  const userError = ref(false)
  const fixedRoles = ref<RoleInfo[]>([])
  const loadingRoles = ref(false)
  const roleError = ref(false)
  const units = computed(() => {
    const result: Array<BusinessUnitInfo & { code: string }> = []
    const append = (items: BusinessUnitInfo[]): void => {
      for (const item of items) { if (item.code) result.push({ ...item, code: item.code }); if (item.children) append(item.children) }
    }
    append(businessUnits.value)
    return result
  })
  const roleOptions = computed(() => draft.value.backup.buSource === 'FIXED_BU' ? fixedRoles.value : roles.value)

  async function load(): Promise<void> {
    loading.value = true
    const results = await Promise.allSettled([adminCenterApi.getBusinessUnitTree(), adminCenterApi.getBuBoundedRoles()])
    catalogError.value = results.some(result => result.status === 'rejected')
    if (results[0].status === 'fulfilled') businessUnits.value = results[0].value
    if (results[1].status === 'fulfilled') roles.value = results[1].value
    loading.value = false
  }
  let searchSequence = 0
  async function searchUsers(keyword: string): Promise<void> {
    const sequence = ++searchSequence
    loadingUsers.value = true
    try {
      const result = await userApi.searchUsers(keyword)
      if (sequence === searchSequence) { users.value = result.filter(user => !!user.username); userError.value = false }
    } catch {
      if (sequence === searchSequence) userError.value = true
    } finally { if (sequence === searchSequence) loadingUsers.value = false }
  }
  let roleSequence = 0
  watch([() => draft.value.backup.buSource, () => draft.value.backup.businessUnitCode, units], async () => {
    const sequence = ++roleSequence
    fixedRoles.value = []; roleError.value = false; loadingRoles.value = false
    if (draft.value.backup.buSource !== 'FIXED_BU' || !draft.value.backup.businessUnitCode) return
    const unit = units.value.find(item => item.code === draft.value.backup.businessUnitCode)
    if (!unit) { roleError.value = true; return }
    loadingRoles.value = true
    try {
      const result = await adminCenterApi.getBusinessUnitEligibleRoles(unit.id)
      if (sequence === roleSequence) fixedRoles.value = result
    } catch { if (sequence === roleSequence) roleError.value = true }
    finally { if (sequence === roleSequence) loadingRoles.value = false }
  })
  return { units, roleOptions, users, loading, loadingUsers, catalogError, userError, roleError, loadingRoles, load, searchUsers }
}
