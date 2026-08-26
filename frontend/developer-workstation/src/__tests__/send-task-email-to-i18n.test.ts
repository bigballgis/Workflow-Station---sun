import { describe, it, expect } from 'vitest'
import { createI18n } from 'vue-i18n'
import en from '@/i18n/locales/en'
import zhCN from '@/i18n/locales/zh-CN'
import zhTW from '@/i18n/locales/zh-TW'

const locales: Array<[string, typeof en]> = [
  ['en', en],
  ['zh-CN', zhCN],
  ['zh-TW', zhTW]
]

function translate(locale: string, messages: typeof en, key: string): string {
  const i18n = createI18n({
    legacy: false,
    locale,
    messages: { [locale]: messages }
  })
  return String(i18n.global.t(key))
}

describe('send task email To / Cc / Bcc i18n literals', () => {
  it.each(locales)('%s shows ${assigneeEmail} in To placeholder and hint', (locale, messages) => {
    const placeholder = translate(locale, messages, 'properties.emailToPlaceholder')
    const hint = translate(locale, messages, 'properties.emailToHint')
    expect(placeholder).toContain('${assigneeEmail}')
    expect(placeholder).not.toMatch(/\$'assigneeEmail/)
    expect(hint).toContain('${assigneeEmail}')
    expect(hint).not.toMatch(/\$'assigneeEmail/)
  })

  it.each(locales)('%s shows ${variable} in Cc and Bcc placeholders', (locale, messages) => {
    const cc = translate(locale, messages, 'properties.emailCcPlaceholder')
    const bcc = translate(locale, messages, 'properties.emailBccPlaceholder')
    expect(cc).toContain('${variable}')
    expect(bcc).toContain('${variable}')
    expect(cc).not.toMatch(/\$'variable/)
    expect(bcc).not.toMatch(/\$'variable/)
  })
})
