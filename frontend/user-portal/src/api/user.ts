import type { AxiosRequestConfig } from 'axios'
import { request as portalRequest } from './request'

/** 用户搜索结果 */
export interface UserOption {
  id: string
  name: string
  username: string
}

/** Portal 用户目录摘要（POST /tasks/users/summaries 响应项） */
interface UserSummaryPayload {
  id: string
  username: string
  name: string
}

// 用户API —— 一律走 user-portal 后端。浏览器不直连 admin-center：admin-center 不认 Portal 的会话 cookie，
// 同源残留的其他端（过期）cookie 会让请求 401，而 Portal 的续期救不回别的端的会话。
export const userApi = {
  // 搜索用户（用于转办、委托等场景）- 通过 user-portal 后端代理
  searchUsers: async (keyword: string): Promise<UserOption[]> => {
    try {
      interface SearchUserItem {
        id: string
        displayName?: string
        fullName?: string
        username: string
      }
      const res = await portalRequest.get<{ data?: SearchUserItem[] }>('/tasks/users/search', { params: { keyword } })
      const data = (res as { data?: SearchUserItem[] })?.data ?? (res as unknown)
      const users: SearchUserItem[] = Array.isArray(data) ? data : []
      return users.map((u: SearchUserItem) => ({
        id: u.id,
        name: u.displayName || u.fullName || u.username,
        username: u.username
      }))
    } catch {
      return []
    }
  },

  /**
   * 按用户 ID 解析显示名（与任务详情 assigneeName 数据源一致，供子表等只读场景使用）
   */
  getUserSummary: async (userId: string): Promise<UserOption | null> => {
    const id = String(userId || '').trim()
    if (!id) return null
    try {
      const res = await portalRequest.post<{ data?: UserSummaryPayload[] }>(
        '/tasks/users/summaries',
        { userIds: [id] },
        { skipGlobalErrorHandler: true } as AxiosRequestConfig
      )
      const list = (res as { data?: UserSummaryPayload[] })?.data
      const u = Array.isArray(list) ? list.find(item => item.id === id) : undefined
      return u ? { id: u.id, name: u.name, username: u.username } : null
    } catch {
      return null
    }
  },
}
