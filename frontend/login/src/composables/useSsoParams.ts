/**
 * SSO URL 参数解析 composable
 *
 * 从 window.location.search 提取 OAuth 参数。
 * 替代 App.vue 中内联的 URLSearchParams 逻辑。
 */

import { ref, onMounted } from 'vue'

export interface SsoParams {
  clientId: string
  redirectUri: string
  state: string
}

export function useSsoParams() {
  const clientId = ref('')
  const redirectUri = ref('')
  const state = ref('')
  const missingParams = ref(false)

  onMounted(() => {
    const q = new URLSearchParams(window.location.search)
    clientId.value = q.get('client_id') || ''
    redirectUri.value = q.get('redirect_uri') || ''
    state.value = q.get('state') || ''
    missingParams.value = !clientId.value || !redirectUri.value
  })

  return { clientId, redirectUri, state, missingParams }
}
