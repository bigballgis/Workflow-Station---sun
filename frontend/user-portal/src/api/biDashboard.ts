import axios, { AxiosRequestConfig } from 'axios'
import { TOKEN_KEY } from './auth'

export interface UserDashboardResponse {
  dashboardId: string
  dashboardTitle: string
  description: string
  embedId: string
  layoutMode: 'SINGLE' | 'MULTI' | 'WIDGET'
  displayOrder: number
  isDefault: boolean
}

export interface GuestTokenResponse {
  token: string
  dashboardEmbedId: string
}

export interface GuestTokenRequest {
  dashboardId: string
}

/**
 * Dedicated axios instance for admin-center BI APIs.
 * Routes through /api/admin-center/ which proxies to admin-center backend at /api/v1/admin/.
 */
const adminCenterService = axios.create({
  baseURL: '/api/admin-center',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

adminCenterService.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  let userId = localStorage.getItem('userId')
  if (!userId) {
    const userStr = localStorage.getItem('user')
    if (userStr) {
      try {
        const user = JSON.parse(userStr)
        userId = user.userId || user.id
      } catch (e) {
        // ignore
      }
    }
  }
  config.headers['X-User-Id'] = userId || 'user_1'
  return config
})

adminCenterService.interceptors.response.use(
  (response) => response.data,
  (error) => Promise.reject(error)
)

export const biDashboardApi = {
  getUserDashboards: (userId: string) =>
    adminCenterService.get<any, UserDashboardResponse[]>(`/bi/assignments/user/${userId}`),

  getGuestToken: (data: GuestTokenRequest) =>
    adminCenterService.post<any, GuestTokenResponse>('/bi/guest-token', data),
}
