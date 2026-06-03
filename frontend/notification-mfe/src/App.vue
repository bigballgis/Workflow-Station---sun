<template>
  <div id="notification-mfe-shell">
    <div id="notification-mfe-app"></div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, createApp, h } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import { createI18n } from 'vue-i18n'
import en from '@/i18n/en'
import zhCN from '@/i18n/zh-CN'
import zhTW from '@/i18n/zh-TW'
import NotificationsPage from './NotificationsPage.vue'

onMounted(() => {
  const i18n = createI18n({
    legacy: false,
    locale: localStorage.getItem('locale') || 'en',
    fallbackLocale: 'en',
    messages: { en, 'zh-CN': zhCN, 'zh-TW': zhTW }
  })

  const app = createApp({
    render: () => h(NotificationsPage)
  })
  for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }
  app.use(createPinia())
  app.use(ElementPlus)
  app.use(i18n)
  app.mount('#notification-mfe-app')
  ;(window as any).__mfe_notification_ready__ = true
})
</script>
