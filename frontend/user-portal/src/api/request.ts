import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { clearAuth } from './auth'
import i18n from '@/i18n'
import { pickHttpErrorBodyMessage } from '@/utils/httpErrorMessage'
import { redirectToUnifiedLogin, setSsoReturnPath } from '@/utils/sso'
import { installSessionRefresh, wasRetriedAfterSessionRefresh } from '@platform-shared/auth/sessionRefresh'

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: '/api/portal',
  timeout: 600000, // 10 minutes - external workflows (e.g. AI invoice recognition) can take several minutes
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
})
installSessionRefresh(service, config => axios.request(config))

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    
    if (res.success === false) {
      const msg = pickHttpErrorBodyMessage(res) || i18n.global.t('api.requestFailed')
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    
    return res
  },
  async (error) => {
    const originalRequest = error.config

    // installSessionRefresh already tried a refresh; reaching here un-retried means the session is gone.
    if (error.response?.status === 401 && !wasRetriedAfterSessionRefresh(originalRequest)) {
      clearAuth()
      setSsoReturnPath(window.location.pathname + window.location.search)
      redirectToUnifiedLogin('portal', { autoSso: true })
      return Promise.reject(error)
    }

    console.error('Response error:', error)

    const skipGlobal = (originalRequest as { skipGlobalErrorHandler?: boolean } | undefined)?.skipGlobalErrorHandler
    if (skipGlobal) {
      return Promise.reject(error)
    }

    if (error.response) {
      const { status, data } = error.response
      const errorMsg = pickHttpErrorBodyMessage(data)
      if (errorMsg && error instanceof Error) {
        error.message = errorMsg
      }
      
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
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config)
  },
  
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config)
  },
  
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config)
  },
  
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config)
  }
}

export default service
