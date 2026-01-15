import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { refreshToken as refreshAuthToken, REFRESH_TOKEN_KEY, TOKEN_KEY, clearAuth } from './auth'

let isRefreshing = false
let failedQueue: Array<{ resolve: Function; reject: Function }> = []

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token)
    }
  })
  failedQueue = []
}

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

api.interceptors.request.use(
  config => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

api.interceptors.response.use(
  response => response.data,
  async error => {
    const originalRequest = error.config
    const { response } = error
    
    if (response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(token => {
          originalRequest.headers.Authorization = `Bearer ${token}`
          return api(originalRequest)
        }).catch(err => Promise.reject(err))
      }

      originalRequest._retry = true
      isRefreshing = true

      const storedRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
      
      if (storedRefreshToken) {
        try {
          const tokenResponse = await refreshAuthToken(storedRefreshToken)
          const newToken = tokenResponse.accessToken
          localStorage.setItem(TOKEN_KEY, newToken)
          
          processQueue(null, newToken)
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return api(originalRequest)
        } catch (refreshError) {
          processQueue(refreshError, null)
          clearAuth()
          router.push('/login')
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      } else {
        clearAuth()
        router.push('/login')
        return Promise.reject(error)
      }
    }

    if (response) {
      switch (response.status) {
        case 403:
          // 403 可能是未登录或权限不足
          const token = localStorage.getItem(TOKEN_KEY)
          if (!token) {
            // 没有 token，清除认证并重定向到登录页
            clearAuth()
            router.push('/login')
            ElMessage.warning('请先登录')
          } else {
            ElMessage.error('没有权限执行此操作')
          }
          break
        case 429:
          ElMessage.warning('请求过于频繁，请稍后重试')
          break
        default:
          ElMessage.error(response.data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default api
