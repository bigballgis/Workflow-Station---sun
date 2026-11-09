<template>
  <div class="sso-callback">
    {{ denied ? t('login.noWorkstationAccess') : 'Completing sign-in…' }}
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { exchangeSsoCode, saveTokens, saveUser, USER_ID_KEY } from '@/api/auth'
import { consumeSsoReturnPath, redirectToUnifiedLogin } from '@/utils/sso'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
/** Signed in, but not admitted to the workstation (platform access model §6.1): say so instead of looping to sign-in. */
const denied = ref(false)

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
  } catch (e: unknown) {
    if ((e as { response?: { status?: number } })?.response?.status === 403) {
      denied.value = true
      return
    }
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
