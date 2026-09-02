import { createI18n } from 'vue-i18n'
import en from './locales/en'
import zhCN from './locales/zh-CN'
import zhTW from './locales/zh-TW'

const STORAGE_KEY = 'ws_help_locale'
const SUPPORTED = ['en', 'zh-CN', 'zh-TW'] as const

export type HelpLocale = (typeof SUPPORTED)[number]

export function isHelpLocale(value: string): value is HelpLocale {
  return (SUPPORTED as readonly string[]).includes(value)
}

export function readStoredLocale(): HelpLocale {
  if (typeof localStorage === 'undefined') return 'en'
  const raw = localStorage.getItem(STORAGE_KEY)
  // FALLBACK(ux): missing or unknown stored locale → English so the picker still works
  if (raw && isHelpLocale(raw)) return raw
  return 'en'
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
