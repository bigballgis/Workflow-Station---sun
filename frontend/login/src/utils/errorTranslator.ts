/**
 * 错误码 → i18n key 映射器
 *
 * 纯工具函数，无 Vue/i18n 依赖。
 * 由 UI 层调用：t(errorTranslator(code), details)
 */
import { AppErrorCode } from '@/types/errors'

export function errorTranslator(code: AppErrorCode): string {
  switch (code) {
    case AppErrorCode.LOGIN_MISSING_PARAMS:
      return 'login.error.missingParams'
    case AppErrorCode.LOGIN_NETWORK_ERROR:
      return 'login.error.network'
    case AppErrorCode.LOGIN_INVALID_RESPONSE:
      return 'login.error.invalidResponse'
    case AppErrorCode.LOGIN_SERVER_ERROR:
      return 'login.error.serverError'
    case AppErrorCode.LOGIN_INVALID_CREDENTIALS:
      return 'login.error.invalidCredentials'
    case AppErrorCode.DSP_DISABLED:
      return 'login.error.dspDisabled'
    case AppErrorCode.DSP_NO_TOKEN:
      return 'login.error.dspNoToken'
    case AppErrorCode.DSP_AUTH_FAILED:
      return 'login.error.dspAuthFailed'
    case AppErrorCode.DSP_FAILED:
      return 'login.error.dspFailed'
    default:
      return 'login.error.invalidCredentials'
  }
}
