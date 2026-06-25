<template>
  <div class="sso-callback">
    Completing sign-in…
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { exchangeSsoCode, saveTokens, saveUser, USER_ID_KEY, USERNAME_KEY } from '@/api/auth'
import { consumeSsoReturnPath, redirectToUnifiedLogin } from '@/utils/sso'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

// Cross-origin SSO return targets, keyed by a fixed `state` marker. The Superset
// author gate (a different origin/port) bounces unauthenticated users to login
// with state=superset-author; after the cookie is set we jump back there.
// Values are a HARDCODED allowlist (never derived from untrusted input) so this
// cannot be abused as an open redirect.
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
  try {
    const resp = await exchangeSsoCode(code, typeof route.query.state === 'string' ? route.query.state : undefined)
    saveTokens(resp.accessToken, resp.refreshToken)
    saveUser(resp.user)
    localStorage.setItem(USER_ID_KEY, resp.user.userId)
    localStorage.setItem(USERNAME_KEY, resp.user.username || resp.user.displayName || resp.user.userId)
    // If this login was initiated by an external gate (e.g. Superset author UI),
    // the cookie is now set on localhost — jump back to that origin.
    const externalReturn = typeof route.query.state === 'string'
      ? SSO_EXTERNAL_RETURNS[route.query.state]
      : undefined
    if (externalReturn) {
      window.location.href = externalReturn
      return
    }
    const dest = consumeSsoReturnPath('/dashboard')
    await router.replace(dest)
  } catch {
    ElMessage.error('Login failed')
    redirectToUnifiedLogin('admin')
  }
})
</script>

<style scoped>
.sso-callback {
  padding: 2rem;
  text-align: center;
}
</style>
