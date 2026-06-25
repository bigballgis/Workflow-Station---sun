/**
 * DSP 免密登录 composable
 *
 * 流程（对齐源项目）：
 *   1) 取浏览器侧 AMToken：优先已存在（URL ?am_token= / cookie AMToken）；
 *      否则在配置了 VITE_DSP_AUTHENTICATE_URL 时，主动 POST dspAuthenticate 获取（带 client 头 + credentials）。
 *   2) 用 AMToken 调后端 /sso/passwordless 换 SSO code；
 *   3) OAuth 重定向（与账号口令登录一致）。
 *
 * 纯业务逻辑：不依赖 vue-i18n，返回 errorCode + errorDetails 交 UI 层翻译。
 */
import { ref, type Ref } from 'vue'
import { passwordlessLogin, type LoginResult } from '@/api/auth'
import { acquireAmToken, isDspAuthenticateConfigured, readExistingAmToken, readQueryAmToken } from '@/api/dsp'
import { AppErrorCode } from '@/types/errors'
interface DspLoginOptions {
  failureCode?: AppErrorCode
}
export function useDspLogin(
  clientId: Ref<string>,
  redirectUri: Ref<string>,
  state: Ref<string>,
  errorCode: Ref<AppErrorCode | null>,
  errorDetails: Ref<Record<string, unknown>>
) {
  const dspLoading = ref(false)
  async function onDspLogin(options: DspLoginOptions = {}) {
    errorCode.value = null
    errorDetails.value = {}
    if (!clientId.value || !redirectUri.value) {
      errorCode.value = AppErrorCode.LOGIN_MISSING_PARAMS
      return
    }
    dspLoading.value = true
    try {
      // 1) 取 AMToken：显式 URL 注入优先；UAT/生产配置了 authenticate 时主动刷新，避免复用过期 cookie。
      let amToken = readQueryAmToken()
      if (!amToken && isDspAuthenticateConfigured()) {
        const acquired = await acquireAmToken()
        if (!acquired.ok) {
          errorCode.value = options.failureCode ?? acquired.code ?? AppErrorCode.DSP_AUTH_FAILED
          errorDetails.value = acquired.detail ? { detail: acquired.detail } : {}
          return
        }
        amToken = acquired.token ?? ''
      }
      if (!amToken) amToken = readExistingAmToken()
      // 2) 用 AMToken 换 SSO code（未取到则传 undefined，留给网关/后端从 header 注入的场景）。
      const result: LoginResult = await passwordlessLogin({
        clientId: clientId.value,
        redirectUri: redirectUri.value,
        state: state.value || undefined,
        amToken: amToken || undefined,
      })
      if (!result.ok) {
        errorCode.value = options.failureCode ?? result.error.code
        errorDetails.value = (result.error.details ?? {}) as Record<string, unknown>
        return
      }
      // 3) 重定向回子系统回调，附带授权码。
      const { data } = result
      const u = new URL(data.redirectUri)
      u.searchParams.set('code', data.authorizationCode)
      if (data.state) u.searchParams.set('state', data.state)
      window.location.href = u.toString()
    } finally {
      dspLoading.value = false
    }
  }
  return { dspLoading, onDspLogin }
}