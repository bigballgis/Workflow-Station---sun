/**
 * DSP 免密登录 composable
 *
 * 流程：取浏览器侧 AMToken（mock 验证经 ?am_token= 注入；真实环境由 DSP/网关写入 cookie/header）
 *      → 调 /sso/passwordless 换 SSO code → OAuth 重定向（与账号口令登录一致）。
 *
 * 纯业务逻辑：不依赖 vue-i18n，返回 errorCode + errorDetails 交 UI 层翻译。
 */

import { ref, type Ref } from 'vue'
import { passwordlessLogin, type LoginResult } from '@/api/auth'
import { AppErrorCode } from '@/types/errors'

/** 从 URL ?am_token= 或 cookie AMToken 读取浏览器侧 AMToken。 */
function readAmToken(): string {
  const q = new URLSearchParams(window.location.search)
  const fromQuery = q.get('am_token')
  if (fromQuery) return fromQuery
  const match = document.cookie.match(/(?:^|;\s*)AMToken=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}

export function useDspLogin(
  clientId: Ref<string>,
  redirectUri: Ref<string>,
  state: Ref<string>,
  errorCode: Ref<AppErrorCode | null>,
  errorDetails: Ref<Record<string, unknown>>
) {
  const dspLoading = ref(false)

  async function onDspLogin() {
    errorCode.value = null
    errorDetails.value = {}

    if (!clientId.value || !redirectUri.value) {
      errorCode.value = AppErrorCode.LOGIN_MISSING_PARAMS
      return
    }

    dspLoading.value = true
    try {
      const result: LoginResult = await passwordlessLogin({
        clientId: clientId.value,
        redirectUri: redirectUri.value,
        state: state.value || undefined,
        amToken: readAmToken() || undefined,
      })

      if (!result.ok) {
        errorCode.value = result.error.code
        errorDetails.value = (result.error.details ?? {}) as Record<string, unknown>
        return
      }

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
