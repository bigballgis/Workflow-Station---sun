import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import zhTW from './locales/zh-TW'
import en from './locales/en'

const isDev = import.meta.env.DEV

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
  missingWarn: isDev, // Enable in dev for missing key detection, disable in prod
  fallbackWarn: false // Keep disabled (too noisy)
})

export default i18n
