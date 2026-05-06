/**
 * 登录业务逻辑 composable
 *
 * 封装登录流程：表单验证 → API 调用 → 错误处理 → OAuth 重定向。
 * 替代 App.vue 中内联的 onSubmit() 函数。
 */

import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { login, type LoginResult } from '@/api/auth'

export function useLogin(
  clientId: Ref<string>,
  redirectUri: Ref<string>,
  state: Ref<string>
) {
  const { t } = useI18n()
  const username = ref('')
  const password = ref('')
  const loading = ref(false)
  const error = ref('')

  async function onSubmit() {
    error.value = ''

    if (!clientId.value || !redirectUri.value) {
      error.value = t('login.error.missingParams')
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
        error.value = result.error
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

  return { username, password, loading, error, onSubmit }
}
