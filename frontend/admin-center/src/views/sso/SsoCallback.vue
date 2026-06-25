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