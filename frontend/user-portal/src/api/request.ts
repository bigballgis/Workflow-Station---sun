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
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    
    if (res.success === false) {
      // Only show error message if it's not a 403 permission error on login page
      const isLoginPage = window.location.pathname === '/login'
      if (!isLoginPage || res.code !== 403) {
        ElMessage.error(res.message || '请求失败')
      }
      const error = new Error(res.message || '请求失败') as any
      error.code = res.code
      error.httpStatus = response.status
      error.httpStatusText = response.statusText
      return Promise.reject(error)
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
    
    console.error('响应错误:', error)
    
    if (error.response) {
      const { status, data } = error.response
      
      switch (status) {
        case 403:
          ElMessage.error('没有权限访问')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error(data?.message || '服务器内部错误')
          break
        default:
          ElMessage.error(data?.message || '请求失败')
      }
    } else if (error.request) {
      ElMessage.error('网络错误，请检查网络连接')
    } else {
      ElMessage.error('请求配置错误')
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
