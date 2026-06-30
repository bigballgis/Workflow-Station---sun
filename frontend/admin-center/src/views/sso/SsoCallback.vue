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
import { launchActivepieces } from '@/api/ap'
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
// (AP uses a dedicated state=ap-bridge branch below — it needs an async server-side
//  mint, not a static URL.)
const SSO_EXTERNAL_RETURNS: Record<string, string> = {
  'superset-author': import.meta.env.VITE_SUPERSET_AUTHOR_URL || 'http://localhost:8087/',
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

  const state = typeof route.query.state === 'string' ? route.query.state : undefined

  // AP login bridge: the platform cookie is now set on THIS (admin) origin. Mint the
  // cross-domain launch URL here (server-side /launch), then jump to the AP domain
  // carrying the one-time nonce — the AP domain needs no platform cookie. Cannot reuse
  // a static URL: without a freshly minted nonce the AP bridge has nothing to exchange.
  if (state === 'ap-bridge') {
    try {
      const bridgeUrl = await launchActivepieces()
      if (bridgeUrl) {
        window.location.href = bridgeUrl
        return
      }
    } catch {
      // 错误提示已由 request 拦截器统一弹出；落空则继续走 admin dashboard。
    }
  }

  // If this login was initiated by an external gate (e.g. Superset author UI),
  // the cookie is now set on this origin — jump back to that origin (skip the admin
  // dashboard/permission check; such a user may not have admin access).
  const externalReturn = state ? SSO_EXTERNAL_RETURNS[state] : undefined
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