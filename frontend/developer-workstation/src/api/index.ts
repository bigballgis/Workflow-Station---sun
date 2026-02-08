import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { refreshToken as refreshAuthToken, REFRESH_TOKEN_KEY, TOKEN_KEY, clearAuth, getUser } from './auth'
import i18n from '@/i18n'

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
    
    // Add X-User-Id request header for backend permission check
    const user = getUser()
    if (user && user.userId) {
      config.headers['X-User-Id'] = user.userId
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
          // 403 may indicate not logged in or insufficient permissions
          const token = localStorage.getItem(TOKEN_KEY)
          if (!token) {
            // No token, clear auth and redirect to login page
            clearAuth()
            router.push('/login')
            ElMessage.warning(i18n.global.t('api.pleaseLogin'))
          } else {
            ElMessage.error(i18n.global.t('api.noPermission'))
          }
          break
        case 429:
          ElMessage.warning(i18n.global.t('api.tooManyRequests'))
          break
        default:
          ElMessage.error(response.data?.message || i18n.global.t('api.requestFailed'))
      }
    } else {
      ElMessage.error(i18n.global.t('api.networkError'))
    }
    return Promise.reject(error)
  }
)

export default api
