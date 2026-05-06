<template>
  <div class="sso-callback">Completing sign-in…</div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { exchangeSsoCode, saveTokens, saveUser, USER_ID_KEY, USERNAME_KEY } from '@/api/auth'
import { consumeSsoReturnPath, redirectToUnifiedLogin } from '@/utils/sso'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

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
