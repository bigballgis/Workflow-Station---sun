<template>
  <div class="remote-loader">
    <div v-if="loading" class="remote-loader__loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ t('mfe.loading') }}</span>
    </div>
    <div v-else-if="error" class="remote-loader__error">
      <el-result icon="warning" :title="t('mfe.unavailable')" :sub-title="error">
        <template #extra>
          <el-button type="primary" @click="retry">{{ t('mfe.retry') }}</el-button>
        </template>
      </el-result>
    </div>
    <div v-else ref="mountPoint" class="remote-loader__content"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loading } from '@element-plus/icons-vue'

const { t } = useI18n()

const props = defineProps<{
  remoteEntryUrl: string
  exposedModule: string
  moduleCode: string
}>()

const loading = ref(true)
const error = ref<string | null>(null)
const mountPoint = ref<HTMLElement | null>(null)
let cleanup: (() => void) | null = null
let _hostFederationModules: any = null

async function loadRemote() {
  loading.value = true
  error.value = null

  try {
    // 1. Inject script to populate module cache
    await loadScript(props.remoteEntryUrl)

    // 2. Dynamic import the remote entry
    const remoteModule = await import(/* @vite-ignore */ props.remoteEntryUrl)

    if (!remoteModule || typeof remoteModule.get !== 'function') {
      throw new Error('remoteEntry.js did not export a "get" function')
    }

    // 3. Get the exposed component (self-bootstrapping — MFE creates its own Vue app)
    const factory = await remoteModule.get(props.exposedModule)
    const Module = factory()

    // 4. Hide skeleton FIRST so mount div renders
    loading.value = false
    await new Promise(resolve => setTimeout(resolve, 0))

    if (mountPoint.value && Module) {
      const Component = Module.__esModule && Module.default ? Module.default : (Module.default || Module)
      const { createApp } = await import('vue')
      const app = createApp(Component)
      // MFE self-bootstraps: App.vue creates its own Vue app with pinia/ElementPlus/i18n
      app.mount(mountPoint.value)
      cleanup = () => app.unmount()
    }
  } catch (e: any) {
    console.warn(`[MFE] Failed to load "${props.moduleCode}":`, e.message)
    error.value = e.message || t('mfe.loadFailed')
    loading.value = false
  }
}

function loadScript(url: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[data-mfe="${props.moduleCode}"]`)
    if (existing) { resolve(); return }

    const script = document.createElement('script')
    script.type = 'module'
    script.src = url
    script.dataset.mfe = props.moduleCode
    script.onload = () => resolve()
    script.onerror = () => reject(new Error(`Failed to load script: ${url}`))

    const timeout = setTimeout(() => {
      script.onload = null; script.onerror = null; script.remove()
      reject(new Error(`Timeout loading script: ${url}`))
    }, 30000)

    script.onload = () => { clearTimeout(timeout); resolve() }
    document.head.appendChild(script)
  })
}

function retry() { cleanup?.(); _hostFederationModules = null; loadRemote() }

onMounted(() => {
  if (props.remoteEntryUrl) { loadRemote() }
  else { error.value = t('mfe.noRemoteUrl'); loading.value = false }
})

onBeforeUnmount(() => { cleanup?.() })

watch(() => props.remoteEntryUrl, () => { cleanup?.(); _hostFederationModules = null; loadRemote() })
</script>

<style scoped>
.remote-loader { min-height: 200px; display: flex; align-items: center; justify-content: center; }
.remote-loader__loading { display: flex; flex-direction: column; align-items: center; gap: 12px; color: var(--text-secondary); }
.remote-loader__loading .el-icon { font-size: 32px; }
.remote-loader__error { width: 100%; }
.remote-loader__content { width: 100%; }
</style>
