<template>
  <div id="gateway-mfe-shell">
    <div id="gateway-mfe-app"></div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, createApp, ref, h } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { createI18n } from 'vue-i18n'
import enMessages from '@/i18n/en'
import zhCNMessages from '@/i18n/zh-CN'
import zhTWMessages from '@/i18n/zh-TW'
import ApisPage from '@/pages/apis/index.vue'
import ApplicationsPage from '@/pages/applications/index.vue'
import ReleasesPage from '@/pages/releases/index.vue'
import AuditPage from '@/pages/audit/index.vue'
import DriftPage from '@/pages/drift/index.vue'
import MonitoringPage from '@/pages/monitoring/index.vue'

const PAGE_MAP: Record<string, any> = {
  apis: ApisPage,
  applications: ApplicationsPage,
  releases: ReleasesPage,
  audit: AuditPage,
  drift: DriftPage,
  monitoring: MonitoringPage,
}

function getPage(): string {
  const hash = window.location.hash.replace('#/', '')
  return PAGE_MAP[hash] ? hash : 'apis'
}

const RootPage = {
  setup() {
    const page = ref(PAGE_MAP[getPage()] || ApisPage)

    function onHashChange() {
      const p = getPage()
      page.value = PAGE_MAP[p] || ApisPage
    }

    onMounted(() => window.addEventListener('hashchange', onHashChange))

    return () => h(page.value)
  }
}

onMounted(() => {
  console.log('[GatewayMFE] Self-bootstrapping full app...')
  const el = document.getElementById('gateway-mfe-app')
  if (el) {
    const locale = localStorage.getItem('locale') || 'en'
    const i18n = createI18n({
      legacy: false,
      locale,
      fallbackLocale: 'en',
      messages: {
        en: enMessages,
        'zh-CN': zhCNMessages,
        'zh-TW': zhTWMessages,
      }
    })

    const app = createApp(RootPage)
    app.use(createPinia())
    app.use(ElementPlus)
    app.use(i18n)
    app.mount(el)
    console.log('[GatewayMFE] Full app bootstrapped successfully')
  }
})
</script>

<style lang="scss">
#gateway-mfe-app {
  --el-color-primary: #DB0011;
  --el-color-primary-light-3: #E34D5A;
  --el-color-primary-light-5: #F099A1;
  --el-color-primary-light-7: #F7C4CA;
  --el-color-primary-light-8: #FAD9DD;
  --el-color-primary-light-9: #FCECEE;
  --el-color-primary-dark-2: #8B0000;

  .page-container,
  .gateway-page {
    padding: 20px;
  }

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    h2 {
      font-size: 20px;
      font-weight: 600;
      color: #303133;
      margin: 0;
    }
  }

  .search-card {
    margin-bottom: 20px;

    .search-form {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: center;
    }
  }

  .table-card {
    .pagination-container {
      margin-top: 20px;
      display: flex;
      justify-content: flex-end;
    }
  }

  .el-pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }

  .filter-bar {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    align-items: center;
  }

  .el-dialog {
    border-radius: 8px;
  }
}
</style>
