import { request as portalRequest } from '@/api/request'
import type { ListColumnOption } from '@platform-shared/list/columnMeta'

interface SearchUserItem {
  id: string
  displayName?: string
  fullName?: string
  username: string
  employeeId?: string
}

/**
 * People-picker source for USER list filters. Keyword matches admin-center user search
 * (name / username / staff id). The option value is {@code sys_users.id}; the label shows
 * name and staff id so two people with the same display name stay distinguishable.
 */
export async function searchListFilterUsers(query: string): Promise<ListColumnOption[]> {
  const res = await portalRequest.get<{ data?: SearchUserItem[] }>('/tasks/users/search', {
    params: { keyword: query },
  })
  const users = (res as { data?: unknown })?.data
  if (!Array.isArray(users)) {
    throw new Error('user search did not return a user list')
  }
  return users.map((raw) => {
    const user = raw as SearchUserItem
    if (!user?.id || !user.username) {
      throw new Error('user search returned an entry without id/username')
    }
    return {
      value: user.id,
      label: userFilterLabel(user),
    }
  })
}

function userFilterLabel(user: SearchUserItem): string {
  const name = user.displayName || user.fullName || user.username
  const staffId = user.employeeId || user.username
  if (staffId && staffId !== name) {
    return `${name} (${staffId})`
  }
  return name
}
