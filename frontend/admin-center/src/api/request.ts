import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { isAbortError } from './isAbortError'
import { notifyError } from '@/utils/notify'
import { ApiError, httpCodeToErrorCode } from '@/types/errors'
import { USER_ID_KEY, USERNAME_KEY, clearAuth } from './auth'
import i18n from '@/i18n'
import { pickHttpErrorBodyMessage } from '@/utils/httpErrorMessage'
import { redirectToUnifiedLogin, setSsoReturnPath } from '@/utils/sso'
import { installSessionRefresh, wasRetriedAfterSessionRefresh } from '@platform-shared/auth/sessionRefresh'

const request: AxiosInstance = axios.create({
  baseURL: '/api/v1/admin',
  timeout: 30000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
})
installSessionRefresh(request, config => axios.request(config))

request.interceptors.request.use(
  (config) => {
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
    if (isAbortError(error)) {
      return Promise.reject(error)
    }

    // installSessionRefresh already tried a refresh; reaching here un-retried means the session is gone.
    if (error.response?.status === 401 && !wasRetriedAfterSessionRefresh(error.config)) {
      clearAuth()
      setSsoReturnPath(window.location.pathname + window.location.search)
      redirectToUnifiedLogin('admin', { autoSso: true })
      return Promise.reject(error)
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
          userMsg = errorMsg || i18n.global.t('api.serviceUnavailable')
          break
        case 503: {
          // An Admin API error says what is unavailable (e.g. the LDAP sync lock store); a proxy's own 503 page does not.
          const apiErr = (data as Record<string, unknown> | undefined)?.error as Record<string, unknown> | undefined
          userMsg = typeof apiErr?.message === 'string' && apiErr.message
            ? apiErr.message
            : i18n.global.t('api.serviceMaintenance')
          break
        }
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
