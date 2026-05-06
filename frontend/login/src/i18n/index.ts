import { createI18n } from 'vue-i18n'
import en from './locales/en'
import zhCN from './locales/zh-CN'

const i18n = createI18n({
  legacy: false,           // Composition API 模式
  locale: 'en',              // 默认英文
  fallbackLocale: 'en',
  messages: {
    en,
    'zh-CN': zhCN,
  },
})

export default i18n
