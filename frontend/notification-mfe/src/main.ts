import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createI18n } from 'vue-i18n'
import App from './App.vue'
import en from './i18n/en'
import zhCN from './i18n/zh-CN'
import zhTW from './i18n/zh-TW'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'en',
  messages: { en, 'zh-CN': zhCN, 'zh-TW': zhTW }
})

const app = createApp(App)
app.use(createPinia())
app.use(ElementPlus)
app.use(i18n)
app.mount('#app')
