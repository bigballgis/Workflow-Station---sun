import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { notifyError, notifyWarning } from '@/utils/notify'
import { ApiError, httpCodeToErrorCode } from '@/types/errors'
import { refreshToken as refreshAuthToken, REFRESH_TOKEN_KEY, TOKEN_KEY, USER_ID_KEY, USERNAME_KEY, clearAuth } from './auth'
import i18n from '@/i18n'
import { pickHttpErrorBodyMessage } from '@/utils/httpErrorMessage'
import { redirectToUnifiedLogin, setSsoReturnPath } from '@/utils/sso'

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
    const userId = localStorage.getItem(USER_ID_KEY)
    if (userId) {
      config.headers['X-User-Id'] = userId
    }
    const username = localStorage.getItem(USERNAME_KEY) || userId
    if (username) {
      config.headers['X-Username'] = username
    }
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
          if (response.refreshToken) {
            localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken)
          }
          
          processQueue(null, newToken)
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return request(originalRequest)
        } catch (refreshError) {
          processQueue(refreshError, null)
          clearAuth()
          setSsoReturnPath(window.location.pathname + window.location.search)
          redirectToUnifiedLogin('admin')
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      } else {
        clearAuth()
        notifyWarning(i18n.global.t('api.unauthorized'))
        setSsoReturnPath(window.location.pathname + window.location.search)
        redirectToUnifiedLogin('admin')
        return Promise.reject(new ApiError(httpCodeToErrorCode(401), 401, undefined))
      }
    }

    if (error.response) {
      const { status, data } = error.response
      const errorMsg = pickHttpErrorBodyMessage(data)
      const code = httpCodeToErrorCode(status)
      let userMsg: string
      
      switch (status) {
        case 400:
          userMsg = errorMsg || i18n.global.t('api.invalidParams')
          break
        case 403:
          userMsg = errorMsg || i18n.global.t('api.noPermission')
          break
        case 404:
          userMsg = errorMsg || i18n.global.t('api.notFound')
          break
        case 409: {
          const apiErr = (data as Record<string, unknown>)?.error as Record<string, unknown> | undefined
          userMsg = (apiErr?.message || apiErr?.code || errorMsg || i18n.global.t('api.conflict')) as string
          break
        }
        case 422:
          userMsg = errorMsg || i18n.global.t('api.businessError')
          break
        case 429:
          userMsg = errorMsg || i18n.global.t('api.tooManyRequests')
          break
        case 500:
          userMsg = errorMsg || i18n.global.t('api.serverError')
          break
        case 502:
          userMsg = i18n.global.t('api.serviceUnavailable')
          break
        case 503:
          userMsg = i18n.global.t('api.serviceMaintenance')
          break
        default:
          userMsg = errorMsg || `${i18n.global.t('api.requestFailed')} (${status})`
      }
      notifyError(userMsg)
      return Promise.reject(new ApiError(code, status, errorMsg))
    } else if (error.request) {
      notifyError(i18n.global.t('api.networkError'))
      return Promise.reject(new ApiError('NETWORK_ERROR', 0))
    } else {
      notifyError(i18n.global.t('api.configError'))
      return Promise.reject(new ApiError('CONFIG_ERROR', 0))
    }
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
