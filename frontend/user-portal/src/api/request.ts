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

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: '/api/portal',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    
    // 添加用户ID头 - 从存储的用户对象中获取
    let userId = localStorage.getItem('userId')
    if (!userId) {
      // 尝试从 user 对象中获取
      const userStr = localStorage.getItem('user')
      if (userStr) {
        try {
          const user = JSON.parse(userStr)
          userId = user.userId || user.id
        } catch (e) {
          console.error('Failed to parse user from localStorage:', e)
        }
      }
    }
    config.headers['X-User-Id'] = userId || 'user_1'
    
    return config
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    
    if (res.success === false) {
      ElMessage.error(res.message || i18n.global.t('api.requestFailed'))
      return Promise.reject(new Error(res.message || i18n.global.t('api.requestFailed')))
    }
    
    return res
  },
  async (error) => {
    const originalRequest = error.config
    
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(token => {
          originalRequest.headers.Authorization = `Bearer ${token}`
          return service(originalRequest)
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
          return service(originalRequest)
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
    
    console.error('Response error:', error)
    
    if (error.response) {
      const { status, data } = error.response
      const errorMsg = data?.message || (data?.details 
        ? (typeof data.details === 'object' 
          ? Object.values(data.details).join('; ') 
          : data.details)
        : null)
      
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

// 封装请求方法
export const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config)
  },
  
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config)
  },
  
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config)
  },
  
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config)
  }
}

export default service
