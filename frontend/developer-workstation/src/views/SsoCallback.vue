<template>
  <div class="sso-callback">Completing sign-in…</div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { exchangeSsoCode, saveTokens, saveUser, USER_ID_KEY } from '@/api/auth'
import { consumeSsoReturnPath, redirectToUnifiedLogin } from '@/utils/sso'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

onMounted(async () => {
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  if (!code) {
    redirectToUnifiedLogin('developer-workstation')
    return
  }
  try {
    const resp = await exchangeSsoCode(code, typeof route.query.state === 'string' ? route.query.state : undefined)
    saveTokens(resp.accessToken, resp.refreshToken)
    saveUser(resp.user)
    localStorage.setItem(USER_ID_KEY, resp.user.userId)
    await router.replace(consumeSsoReturnPath('/'))
  } catch {
    ElMessage.error('Sign-in failed')
    redirectToUnifiedLogin('developer-workstation')
  }
})
</script>

<style scoped>
.sso-callback {
  padding: 2rem;
  text-align: center;
}
</style>
