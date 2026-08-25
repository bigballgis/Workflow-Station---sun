import { createI18n } from 'vue-i18n'
import en from './locales/en'
import zhCN from './locales/zh-CN'
import zhTW from './locales/zh-TW'

const STORAGE_KEY = 'ws_help_locale'
const SUPPORTED = ['en', 'zh-CN', 'zh-TW'] as const

export type HelpLocale = (typeof SUPPORTED)[number]

export function readStoredLocale(): HelpLocale {
  if (typeof localStorage === 'undefined') return 'en'
  const raw = localStorage.getItem(STORAGE_KEY)
  return SUPPORTED.includes(raw as HelpLocale) ? (raw as HelpLocale) : 'en'
}

export function persistLocale(locale: HelpLocale): void {
  localStorage.setItem(STORAGE_KEY, locale)
}

const i18n = createI18n({
  legacy: false,
  locale: readStoredLocale(),
  fallbackLocale: 'en',
  messages: {
    en,
    'zh-CN': zhCN,
    'zh-TW': zhTW,
  },
})

export default i18n
