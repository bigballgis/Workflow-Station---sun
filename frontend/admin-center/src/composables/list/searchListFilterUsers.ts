import { userApi } from '@/api/user'
import type { ListColumnOption } from '@platform-shared/list/columnMeta'

/**
 * People-picker source for USER list filters. Uses the existing user search card API
 * (GET /users keyword) so the dialog does not invent a second search path.
 */
export async function searchListFilterUsers(query: string): Promise<ListColumnOption[]> {
  const needle = query.trim().toLowerCase()
  if (!needle) {
    return []
  }
  const result = await userApi.list({ keyword: query, page: 0, size: 20 })
  if (!Array.isArray(result.content)) {
    throw new Error('user search did not return a user list')
  }
  const options: ListColumnOption[] = []
  for (const user of result.content) {
    if (!user?.id || !user.username) {
      throw new Error('user search returned an entry without id/username')
    }
    const haystacks = [user.fullName, user.employeeId]
    if (!haystacks.some((value) => (value ?? '').toLowerCase().includes(needle))) {
      continue
    }
    const name = user.fullName || user.username
    const staffId = user.employeeId || user.username
    options.push({
      value: user.id,
      label: staffId && staffId !== name ? `${name} (${staffId})` : name,
    })
  }
  return options
}
