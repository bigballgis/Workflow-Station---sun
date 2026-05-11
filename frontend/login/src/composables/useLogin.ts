/**
 * 登录业务逻辑 composable
 *
 * 封装登录流程：表单验证 → API 调用 → 错误处理 → OAuth 重定向。
 * 替代 App.vue 中内联的 onSubmit() 函数。
 *
 * 纯业务逻辑 composable：不依赖 vue-i18n。
 * 返回 errorCode + errorDetails，由 UI 层通过 errorTranslator + t() 翻译。
 */

import { ref, type Ref } from 'vue'
import { login, type LoginResult } from '@/api/auth'
import { AppErrorCode } from '@/types/errors'

export function useLogin(
  clientId: Ref<string>,
  redirectUri: Ref<string>,
  state: Ref<string>
) {
  const username = ref('')
  const password = ref('')
  const loading = ref(false)
  const errorCode = ref<AppErrorCode | null>(null)
  const errorDetails = ref<Record<string, unknown>>({})

  async function onSubmit() {
    errorCode.value = null
    errorDetails.value = {}

    if (!clientId.value || !redirectUri.value) {
      errorCode.value = AppErrorCode.LOGIN_MISSING_PARAMS
      return
    }

    loading.value = true
    try {
      const result: LoginResult = await login({
        username: username.value,
        password: password.value,
        clientId: clientId.value,
        redirectUri: redirectUri.value,
        state: state.value || undefined,
      })

      if (!result.ok) {
        errorCode.value = result.error.code
        errorDetails.value = (result.error.details ?? {}) as Record<string, unknown>
        return
      }

      // OAuth 重定向
      const { data } = result
      const u = new URL(data.redirectUri)
      u.searchParams.set('code', data.authorizationCode)
      if (data.state) u.searchParams.set('state', data.state)
      window.location.href = u.toString()
    } finally {
      loading.value = false
    }
  }

  return { username, password, loading, errorCode, errorDetails, onSubmit }
}
