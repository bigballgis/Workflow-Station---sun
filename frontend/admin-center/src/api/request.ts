import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
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
  (response: AxiosResponse) => {
    const data = response.data
    
    // 检查响应数据中是否包含错误代码（即使 HTTP 状态码是 200）
    if (data && typeof data === 'object' && 'code' in data) {
      const errorCode = data.code
      
      // 如果是错误代码（403, 404, 500 等），将其作为错误处理
      if (errorCode === 403 || errorCode === '403' || errorCode === 'PERMISSION_DENIED') {
        const message = data.message || '权限不足'
        ElMessage.error(message)
        return Promise.reject({
          name: 'PermissionDenied',
          httpError: false,
          httpStatus: response.status,
          httpStatusText: response.statusText,
          code: errorCode,
          message: message,
          response: response
        })
      }
      
      // 其他错误代码也类似处理
      if (errorCode === 404 || errorCode === '404' || errorCode === 'NOT_FOUND') {
        const message = data.message || '资源不存在'
        ElMessage.error(message)
        return Promise.reject({
          name: 'NotFound',
          httpError: false,
          httpStatus: response.status,
          httpStatusText: response.statusText,
          code: errorCode,
          message: message,
          response: response
        })
      }
    }
    
    return response.data
  },
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

    // Handle network errors (Failed to fetch)
    if (!error.response && error.message) {
      const networkMessage = error.message.includes('Failed to fetch') 
        ? '无法连接到服务器，请检查后端服务是否运行' 
        : error.message
      ElMessage.error(networkMessage)
      return Promise.reject({
        ...error,
        name: 'NetworkError',
        httpError: true,
        httpStatus: 0,
        httpStatusText: error.message,
        message: networkMessage
      })
    }
    
    const message = error.response?.data?.message || error.message || '请求失败'
    ElMessage.error(message)
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
