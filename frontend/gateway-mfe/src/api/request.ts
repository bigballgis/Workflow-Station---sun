import axios, { AxiosInstance, AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

const USER_ID_KEY = 'userId'
const USERNAME_KEY = 'username'

const service: AxiosInstance = axios.create({
  baseURL: '/api/v1/admin',
  timeout: 30000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
})

service.interceptors.request.use(
  (config) => {
    const userId = localStorage.getItem(USER_ID_KEY)
    if (userId) config.headers['X-User-Id'] = userId
    const username = localStorage.getItem(USERNAME_KEY) || userId
    if (username) config.headers['X-Username'] = username
    if (!config.headers['X-Tenant-Id']) {
      config.headers['X-Tenant-Id'] = localStorage.getItem('tenantId') || 'default'
    }
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const msg = error.response?.data?.error?.message || error.response?.data?.message || error.message
    ElMessage.error(msg || 'Request failed')
    return Promise.reject(error)
  }
)

export default service

export const get = <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  service.get(url, config)
export const post = <T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> =>
  service.post(url, data, config)
export const put = <T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> =>
  service.put(url, data, config)
export const del = <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  service.delete(url, config)
