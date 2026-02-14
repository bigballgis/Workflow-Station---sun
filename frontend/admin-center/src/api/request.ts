import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { refreshToken as refreshAuthToken, REFRESH_TOKEN_KEY, TOKEN_KEY, clearAuth } from './auth'
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

const request: AxiosInstance = axios.create({
  baseURL: '/api/v1/admin',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    const userId = localStorage.getItem('userId') || 'system'
    config.headers['X-User-Id'] = userId
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response: AxiosResponse) => response.data,
  async (error) => {
    const originalRequest = error.config
    
    // Handle 401 errors with token refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // Queue the request while refreshing
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(token => {
          originalRequest.headers.Authorization = `Bearer ${token}`
          return request(originalRequest)
        }).catch(err => Promise.reject(err))
      }

      originalRequest._retry = true
      isRefreshing = true

      const storedRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
      
      if (storedRefreshToken) {
        try {
          const response = await refreshAuthToken(storedRefreshToken)
          const newToken = response.accessToken
          localStorage.setItem(TOKEN_KEY, newToken)
          
          processQueue(null, newToken)
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return request(originalRequest)
        } catch (refreshError) {
          processQueue(refreshError, null)
          clearAuth()
          window.location.href = '/login'
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      } else {
        clearAuth()
        window.location.href = '/login'
        return Promise.reject(error)
      }
    }

    const message = error.response?.data?.message || error.response?.data?.details 
      ? (typeof error.response?.data?.details === 'object' 
        ? Object.values(error.response.data.details).join('; ') 
        : error.response?.data?.details)
      : null
    
    if (error.response) {
      const { status, data } = error.response
      const errorMsg = data?.message || message
      
      switch (status) {
        case 400:
          ElMessage.error(errorMsg || i18n.global.t('api.invalidParams'))
          break
        case 403:
          ElMessage.error(errorMsg || i18n.global.t('api.noPermission'))
          break
        case 404:
          ElMessage.error(errorMsg || i18n.global.t('api.notFound'))
          break
        case 422:
          ElMessage.error(errorMsg || i18n.global.t('api.businessError'))
          break
        case 429:
          ElMessage.error(errorMsg || i18n.global.t('api.tooManyRequests'))
          break
        case 500:
          ElMessage.error(errorMsg || i18n.global.t('api.serverError'))
          break
        case 502:
          ElMessage.error(i18n.global.t('api.serviceUnavailable'))
          break
        case 503:
          ElMessage.error(i18n.global.t('api.serviceMaintenance'))
          break
        default:
          ElMessage.error(errorMsg || `${i18n.global.t('api.requestFailed')} (${status})`)
      }
    } else if (error.request) {
      ElMessage.error(i18n.global.t('api.networkError'))
    } else {
      ElMessage.error(i18n.global.t('api.configError'))
    }
    return Promise.reject(error)
  }
)

export default request

export const get = <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  request.get(url, config)

export const post = <T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> =>
  request.post(url, data, config)

export const put = <T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> =>
  request.put(url, data, config)

export const del = <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  request.delete(url, config)
