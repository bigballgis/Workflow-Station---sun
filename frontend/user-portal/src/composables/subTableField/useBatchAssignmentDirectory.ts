import { onBeforeUnmount, reactive, ref } from 'vue'
import type { AxiosRequestConfig } from 'axios'
import { permissionApi, type BusinessUnit } from '@/api/permission'
import { request } from '@/api/request'
import type { UserOption } from '@/api/user'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'

type RoleOption = { label: string; value: string }
type Translate = (key: string) => string

function requireArray(value: unknown): unknown[] {
  if (Array.isArray(value)) return value
  if (value && typeof value === 'object' && 'data' in value && Array.isArray(value.data)) return value.data
  throw new Error('common.operationFailed')
}

/** Batch assignment must distinguish a failed directory lookup from a valid empty result. */
export function useBatchAssignmentDirectory(t: Translate) {
  const error = ref('')
  const businessUnits = ref<BusinessUnit[]>([])
  const userOptions = reactive<Record<string, UserOption[]>>({})
  const userLoading = reactive<Record<string, boolean>>({})
  const buCodes = new Map<string, string>()
  const latestSearch = new Map<string, number>()
  const searchTimers = new Map<string, ReturnType<typeof setTimeout>>()
  let disposed = false

  function report(cause: unknown) {
    error.value = cause instanceof Error && cause.message === 'common.operationFailed'
      ? t(cause.message) : resolveUserFacingHttpMessage(cause, t)
  }
  async function loadBusinessUnits() {
    const result = requireArray(await permissionApi.getBusinessUnitsTree({ skipGlobalErrorHandler: true }))
    const index = (items: unknown[]): BusinessUnit[] => items.map(item => {
      if (!item || typeof item !== 'object' || !('id' in item) || !('name' in item)
        || typeof item.id !== 'string' || typeof item.name !== 'string') throw new Error('common.operationFailed')
      if ('code' in item && typeof item.code === 'string' && item.code.trim()) buCodes.set(item.id, item.code)
      const children = 'children' in item && item.children != null ? index(requireArray(item.children)) : undefined
      return { id: item.id, name: item.name, ...(children ? { children } : {}) }
    })
    businessUnits.value = index(result)
    error.value = ''
  }
  function requireBusinessUnitCode(id: string): string {
    const code = buCodes.get(id)
    if (!code) throw new Error('common.operationFailed')
    return code
  }
  function requireBusinessUnitId(code: string): string {
    const matches = [...buCodes].filter(([, value]) => value === code)
    if (matches.length !== 1) throw new Error('common.operationFailed')
    return matches[0]![0]
  }
  async function loadRoles(businessUnitId?: string): Promise<RoleOption[]> {
    const result = requireArray(await (businessUnitId
      ? permissionApi.getBusinessUnitRoles(businessUnitId, { skipGlobalErrorHandler: true })
      : permissionApi.getAvailableRoles()))
    const roles = result.map(role => {
      if (!role || typeof role !== 'object' || !('code' in role) || typeof role.code !== 'string' || !role.code.trim()) throw new Error('common.operationFailed')
      return { value: role.code, label: 'name' in role && typeof role.name === 'string' ? role.name : role.code }
    })
    error.value = ''
    return roles
  }
  function searchUsers(key: string, query: string) {
    const sequence = (latestSearch.get(key) ?? 0) + 1
    latestSearch.set(key, sequence)
    clearTimeout(searchTimers.get(key))
    userLoading[key] = true
    searchTimers.set(key, setTimeout(async () => {
      try {
        // The legacy userApi.searchUsers turns transport errors into []; batch targets must not use that path.
        const config: AxiosRequestConfig & { skipGlobalErrorHandler: boolean } = { params: { keyword: query }, skipGlobalErrorHandler: true }
        const result = requireArray(await request.get<unknown>('/tasks/users/search', config))
        const users = result.map(user => {
          if (!user || typeof user !== 'object' || !('id' in user) || !('username' in user)
            || typeof user.id !== 'string' || typeof user.username !== 'string') throw new Error('common.operationFailed')
          const name = 'displayName' in user && typeof user.displayName === 'string' && user.displayName
            ? user.displayName : 'fullName' in user && typeof user.fullName === 'string' && user.fullName ? user.fullName : user.username
          return { id: user.id, username: user.username, name }
        })
        if (!disposed && sequence === latestSearch.get(key)) { userOptions[key] = users; error.value = '' }
      } catch (cause) {
        if (!disposed && sequence === latestSearch.get(key)) { userOptions[key] = []; report(cause) }
      } finally { if (!disposed && sequence === latestSearch.get(key)) userLoading[key] = false }
    }, 300))
  }
  onBeforeUnmount(() => { disposed = true; searchTimers.forEach(clearTimeout) })
  return { error, businessUnits, userOptions, userLoading, report, loadBusinessUnits, requireBusinessUnitCode, requireBusinessUnitId, loadRoles, searchUsers }
}
