import { ref, inject } from 'vue'

/**
 * Remote user search (Req 37.1) and department tree (Req 37.2) for the
 * sub-table add/edit dialog. The department tree reuses an injected shared
 * cache from FormRenderer when present to avoid duplicate fetches.
 *
 * Behaviour preserved verbatim from the original SFC.
 */
export function useSubTableDialogRelations() {
  // ─── User search state (Req 37.1) ────────────────────────────────────────
  const userSearchOptions = ref<Record<string, Array<{ id: string; name: string }>>>({})
  const userSearchLoading = ref<Record<string, boolean>>({})

  async function handleUserSearch(query: string, field: string) {
    if (query.length < 2) return
    userSearchLoading.value = { ...userSearchLoading.value, [field]: true }
    try {
      const { userApi } = await import('@/api/user')
      const results = await userApi.searchUsers(query)
      userSearchOptions.value = { ...userSearchOptions.value, [field]: results }
    } catch {
      userSearchOptions.value = { ...userSearchOptions.value, [field]: [] }
    } finally {
      userSearchLoading.value = { ...userSearchLoading.value, [field]: false }
    }
  }

  // ─── Department tree state (Req 37.2) ────────────────────────────────────
  const departmentTreeData = ref<any[]>([])
  const departmentLoading = ref(false)

  // Use injected shared cache from FormRenderer if available
  const sharedDepartmentData = inject<typeof departmentTreeData | undefined>('departmentTreeData', undefined)
  const sharedDepartmentLoading = inject<typeof departmentLoading | undefined>('departmentTreeLoading', undefined)

  async function fetchDepartmentTree() {
    if (sharedDepartmentData?.value && sharedDepartmentData.value.length > 0) {
      departmentTreeData.value = sharedDepartmentData.value
      return
    }
    if (departmentTreeData.value.length > 0) return
    departmentLoading.value = true
    if (sharedDepartmentLoading) sharedDepartmentLoading.value = true
    try {
      const api = (await import('@/api/request')).default
      const res = await api.get('/api/portal/departments/tree')
      const data = res.data?.data ?? res.data ?? []
      departmentTreeData.value = data
      if (sharedDepartmentData) sharedDepartmentData.value = data
    } catch (err) {
      console.warn('[SubTableAddDialog] Failed to fetch department tree:', err)
    } finally {
      departmentLoading.value = false
      if (sharedDepartmentLoading) sharedDepartmentLoading.value = false
    }
  }

  return {
    userSearchOptions,
    userSearchLoading,
    handleUserSearch,
    departmentTreeData,
    departmentLoading,
    fetchDepartmentTree,
  }
}
