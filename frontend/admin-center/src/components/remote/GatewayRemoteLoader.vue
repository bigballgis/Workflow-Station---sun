<template>
  <div v-if="error" class="remote-loader-error">
    <el-result icon="warning" :title="t('gateway.gatewayMfeError')" :sub-title="error">
      <template #extra>
        <el-button type="primary" @click="loadRemote">{{ t('gateway.retry') || 'Retry' }}</el-button>
      </template>
    </el-result>
  </div>
  <div v-else-if="loading" class="remote-loader-loading">
    <el-skeleton :rows="8" animated />
  </div>
  <div v-else ref="mountPoint" class="remote-loader-mount"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

const { t } = useI18n()
const mountPoint = ref<HTMLElement | null>(null)
const loading = ref(true)
const error = ref('')

const MFE_URL = (import.meta as any).env?.VITE_GATEWAY_MFE_URL || '/mfe-gateway/assets/remoteEntry.js'

let appInstance: any = null

/** Extract the gateway sub-path from the host URL pathname.
 *  e.g., /admin/gateway/drift → drift
 */
function getSubPath(): string {
  const match = window.location.pathname.match(/\/gateway\/(\w+)/)
  return match ? match[1] : 'apis'
}

async function loadRemote() {
  loading.value = true
  error.value = ''

  try {
    // 1. Dynamic import — federation with shared:[] exports get/init, no window global
    console.log('[GatewayRemoteLoader] Importing:', MFE_URL)
    const remoteModule = await import(/* @vite-ignore */ MFE_URL)
    console.log('[GatewayRemoteLoader] Module loaded, keys:', Object.keys(remoteModule))
    
    if (!remoteModule || typeof remoteModule.get !== 'function') {
      throw new Error('remoteEntry.js did not export a "get" function')
    }

    // 2. Get the exposed component factory
    console.log('[GatewayRemoteLoader] Calling get("./App")...')
    const factory = await remoteModule.get('./App')
    console.log('[GatewayRemoteLoader] Factory type:', typeof factory)
    const Module = factory()
    console.log('[GatewayRemoteLoader] Module:', typeof Module, Module?.name || '(anonymous)')

    // 3. Mount into our div (hide skeleton FIRST so ref div renders)
    loading.value = false
    await nextTick()
    console.log('[GatewayRemoteLoader] mountPoint:', !!mountPoint.value, 'Module ready:', !!Module)
    if (mountPoint.value) {
      const { createApp } = await import('vue')
      const { createPinia } = await import('pinia')
      const { createI18n } = await import('vue-i18n')

      const en = (await import('@/i18n/locales/en')).default
      const zhCN = (await import('@/i18n/locales/zh-CN')).default
      const zhTW = (await import('@/i18n/locales/zh-TW')).default

      const i18n = createI18n({
        legacy: false,
        locale: localStorage.getItem('locale') || 'zh-CN',
        fallbackLocale: 'en',
        messages: { en, 'zh-CN': zhCN, 'zh-TW': zhTW }
      })

      const app = createApp(Module.default || Module)
      app.use(createPinia())
      app.use(i18n)
      appInstance = app
      app.mount(mountPoint.value)
      console.log('[GatewayRemoteLoader] App mounted. DOM children:', mountPoint.value.children.length)
      console.log('[GatewayRemoteLoader] Inner HTML preview:', mountPoint.value.innerHTML.substring(0, 200))

      // 4. Sync URL sub-path → MFE hash router (only if hash doesn't match)
      const subPath = getSubPath()
      const expectedHash = '#/' + subPath
      if (subPath && window.location.hash !== expectedHash) {
        window.location.hash = expectedHash
      }
    }
  } catch (e: any) {
    console.error('[GatewayRemoteLoader]', e)
    error.value = e.message || 'Unknown error'
    loading.value = false
  }
}

// Watch route path changes — same catch-all route, different params don't remount
import { watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

onMounted(() => {
  loadRemote()
})

// When navigating between /gateway/apis and /gateway/applications etc.,
// the component stays mounted — manually sync hash
watch(() => route.path, (newPath) => {
  const match = newPath.match(/\/gateway\/(\w+)/)
  if (match && appInstance) {
    const page = match[1]
    const expectedHash = '#/' + page
    if (window.location.hash !== expectedHash) {
      window.location.hash = expectedHash
    }
  }
})

onBeforeUnmount(() => {
  if (appInstance) {
    appInstance.unmount()
    appInstance = null
  }
})
</script>

<style scoped>
.remote-loader-loading {
  padding: 40px;
}
.remote-loader-error {
  padding: 40px;
}
.remote-loader-mount {
  min-height: 400px;
}
</style>
