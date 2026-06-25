/**
 * SSO URL 参数解析 composable
 *
 * 从 window.location.search 提取 OAuth 参数。
 * 替代 App.vue 中内联的 URLSearchParams 逻辑。
 */
import { ref } from 'vue'
export interface SsoParams {
  clientId: string
  redirectUri: string
  state: string
  autoSso: boolean
}
export function useSsoParams() {
  const clientId = ref('')
  const redirectUri = ref('')
  const state = ref('')
  const autoSso = ref(false)
  const missingParams = ref(false)
  function parseParams() {
    const q = new URLSearchParams(window.location.search)
    clientId.value = q.get('client_id') || ''
    redirectUri.value = q.get('redirect_uri') || ''
    state.value = q.get('state') || ''
    autoSso.value = q.get('auto_sso') === '1'
    missingParams.value = !clientId.value || !redirectUri.value
  }
  parseParams()
  return { clientId, redirectUri, state, autoSso, missingParams }
}