<template>
  <div class="sso-callback">
    Completing sign-in…
  </div>
</template>

<script setup lang="ts">
import axios from 'axios'
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearAuth, exchangeSsoCode, saveTokens, saveUser, USER_ID_KEY, USERNAME_KEY } from '@/api/auth'
import { consumeSsoReturnPath, redirectToUnifiedLogin } from '@/utils/sso'
import { canAccessRoute } from '@/utils/permission'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

function isAdminAccessDenied(error: unknown) {
  if (!axios.isAxiosError(error)) return false
  const status = error.response?.status
  const responseError = error.response?.data?.error
  return status === 403 || (status === 400 && responseError === 'You do not have access to Admin Center')
}

// Cross-origin SSO return targets, keyed by a fixed `state` marker. The Superset
// author gate (a different origin/port) bounces unauthenticated users to login
// with state=superset-author; after the cookie is set we jump back there.
// Values are a HARDCODED allowlist (never derived from untrusted input) so this
// cannot be abused as an open redirect.
// AP login bridge URL from RUNTIME config (per-environment; empty in prod -> no return).
// Mirrors AdminLayout: window.__APP_CONFIG__, falling back to the dev default under vite dev.
function apBridgeReturn(): string {
  const rt = window.__APP_CONFIG__?.AP_BRIDGE_URL
  if (rt && !rt.includes('${')) return rt
  return import.meta.env.DEV ? 'http://localhost:8085/__ap/bridge' : ''
}

const SSO_EXTERNAL_RETURNS: Record<string, string> = {
  'superset-author': import.meta.env.VITE_SUPERSET_AUTHOR_URL || 'http://localhost:8087/',
  // Activepieces gateway bounces unauthenticated users to login with state=ap-bridge;
  // after the cookie is set, jump back to the AP login bridge.
  'ap-bridge': apBridgeReturn(),
}

onMounted(async () => {
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  if (!code) {
    ElMessage.error('Missing authorization code')
    redirectToUnifiedLogin('admin')
    return
  }

  let resp
  try {
    resp = await exchangeSsoCode(code, typeof route.query.state === 'string' ? route.query.state : undefined)
  } catch (error) {
    if (isAdminAccessDenied(error)) {
      clearAuth(false)
      await router.replace('/403')
      return
    }
    ElMessage.error('Login failed')
    redirectToUnifiedLogin('admin')
    return
  }

  saveTokens(resp.accessToken, resp.refreshToken)
  saveUser(resp.user)
  localStorage.setItem(USER_ID_KEY, resp.user.userId)
  localStorage.setItem(USERNAME_KEY, resp.user.username || resp.user.displayName || resp.user.userId)

  // If this login was initiated by an external gate (e.g. Superset author UI),
  // the cookie is now set on localhost — jump back to that origin (skip the admin
  // dashboard/permission check; such a user may not have admin access).
  const externalReturn = typeof route.query.state === 'string'
    ? SSO_EXTERNAL_RETURNS[route.query.state]
    : undefined
  if (externalReturn) {
    window.location.href = externalReturn
    return
  }

  const dest = consumeSsoReturnPath('/dashboard')
  const resolvedDest = router.resolve(dest)
  try {
    await router.replace(canAccessRoute(resolvedDest.path) ? dest : '/403')
  } catch {
    await router.replace('/403')
  }
})
</script>

<style scoped>
.sso-callback {
  padding: 2rem;
  text-align: center;
}
</style>