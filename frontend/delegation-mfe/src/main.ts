import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createI18n } from 'vue-i18n'
import App from './App.vue'
import en from './i18n/en'
import zhCN from './i18n/zh-CN'
import zhTW from './i18n/zh-TW'

const app = createApp(App)
app.use(createPinia())
app.use(ElementPlus)
app.use(createI18n({ legacy: false, locale: 'en', fallbackLocale: 'en', messages: { en, 'zh-CN': zhCN, 'zh-TW': zhTW } }))
app.mount('#app')
