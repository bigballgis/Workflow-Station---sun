import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createI18n } from 'vue-i18n'
import AppShell from './AppShell.vue'
import router from './router'
import en from './i18n/en'
import zhCN from './i18n/zh-CN'
import zhTW from './i18n/zh-TW'

const i18n = createI18n({
  legacy: false,
  locale: localStorage.getItem('locale') || 'en',
  fallbackLocale: 'en',
  messages: { en, 'zh-CN': zhCN, 'zh-TW': zhTW }
})

const app = createApp(AppShell)
app.use(createPinia())
app.use(ElementPlus)
app.use(i18n)
app.use(router)
app.mount('#app')
