import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import zhTW from './locales/zh-TW'
import en from './locales/en'

const i18n = createI18n({
  legacy: false, // Use Composition API mode (required for Vue 3 and v11+)
  locale: 'en', // Fixed to English
  fallbackLocale: 'en',
  messages: {
    'zh-CN': zhCN,
    'zh-TW': zhTW,
    'en': en
  },
  // Vue I18n v11+ configuration
  warnHtmlMessage: false, // Disable HTML message warnings in development
  missingWarn: false, // Disable missing key warnings (optional)
  fallbackWarn: false // Disable fallback warnings (optional)
})

export default i18n
