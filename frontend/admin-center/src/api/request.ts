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
    // 注意：只有当响应同时包含 code 和 message 字段，且 code 是错误代码时，才认为是错误响应
    // 正常的响应（如 PageResult）可能也有 code 字段，但通常没有 message 字段
    if (data && typeof data === 'object' && 'code' in data && 'message' in data) {
      const errorCode = data.code
      
      // 只有当 code 明确表示错误时才处理（403, 404, 500 等错误代码）
      // 忽略成功代码和正常响应（如 PageResult 可能包含 code 字段但不是错误）
      const isErrorCode = (
        errorCode === 403 || errorCode === '403' || errorCode === 'PERMISSION_DENIED' ||
        errorCode === 404 || errorCode === '404' || errorCode === 'NOT_FOUND' ||
        errorCode === 500 || errorCode === '500' || errorCode === 'INTERNAL_ERROR'
      )
      
      if (isErrorCode) {
        // 如果是错误代码（403, 404, 500 等），将其作为错误处理
        if (errorCode === 403 || errorCode === '403' || errorCode === 'PERMISSION_DENIED') {
          const message = data.message || '权限不足'
          console.warn('[Request Interceptor] Permission denied:', {
            code: errorCode,
            message: message,
            path: response.config?.url,
            status: response.status,
            fullResponse: data
          })
          ElMessage.error(message)
          // 延迟跳转到 403 页面，避免与当前导航冲突
          setTimeout(() => {
            if (typeof window !== 'undefined' && window.location.pathname !== '/403') {
              window.location.href = '/403'
            }
          }, 1500)
          // 创建一个可识别的错误对象
          const permissionError = {
            name: 'PermissionDenied',
            httpError: false,
            httpStatus: response.status,
            httpStatusText: response.statusText,
            code: errorCode,
            message: message,
            response: response,
            isHandled: true, // 标记为已处理
            // 添加 toString 方法，避免在控制台显示为 [object Object]
            toString: () => `PermissionDenied: ${message}`
          }
          // 使用 setTimeout 确保错误被正确处理，避免未捕获的 Promise 错误
          setTimeout(() => {
            console.warn('[Request Interceptor] Permission denied error (handled):', permissionError)
          }, 0)
          return Promise.reject(permissionError)
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

    // Handle 403 Forbidden errors
    if (error.response?.status === 403) {
      const message = error.response?.data?.message || '权限不足'
      ElMessage.error(message)
      // 延迟跳转到 403 页面
      setTimeout(() => {
        if (typeof window !== 'undefined' && window.location.pathname !== '/403') {
          window.location.href = '/403'
        }
      }, 1500)
      return Promise.reject({
        ...error,
        name: 'PermissionDenied',
        httpError: true,
        httpStatus: 403,
        httpStatusText: 'Forbidden',
        code: 403,
        message: message
      })
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
