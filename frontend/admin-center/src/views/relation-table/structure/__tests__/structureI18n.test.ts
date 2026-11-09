import { describe, expect, it } from 'vitest'
import en from '@/i18n/locales/en'
import zhCN from '@/i18n/locales/zh-CN'
import zhTW from '@/i18n/locales/zh-TW'
import listPage from '../index.vue?raw'
import versionDialog from '../components/VersionDialog.vue?raw'
import compareDialog from '../components/VersionCompareDialog.vue?raw'
import accessDialog from '../components/AccessConfigDialog.vue?raw'
import accessConfig from '@/composables/modules/useRelationTableAccessConfig.ts?raw'

const locales = { en, 'zh-CN': zhCN, 'zh-TW': zhTW } as Record<string, unknown>

const pages: Record<string, string> = {
  'index.vue': listPage,
  'VersionDialog.vue': versionDialog,
  'VersionCompareDialog.vue': compareDialog,
  'AccessConfigDialog.vue': accessDialog,
}

const keysUsedIn = (source: string) => [...source.matchAll(/\bt\('([\w.]+)'/g)].map(match => match[1])

const message = (messages: unknown, key: string): unknown =>
  key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown> | undefined)?.[part], messages)

describe('Relation Table structure page: text comes from the three locales', () => {
  it.each(Object.entries({ ...pages, 'useRelationTableAccessConfig.ts': accessConfig }))(
    '%s: every message it uses exists in en, zh-CN and zh-TW',
    (_file, source) => {
      const keys = keysUsedIn(source)
      expect(keys.length).toBeGreaterThan(0)
      for (const key of keys) {
        for (const [locale, messages] of Object.entries(locales)) {
          const text = message(messages, key)
          expect(typeof text === 'string' && text.length > 0, `${locale} is missing ${key}`).toBe(true)
        }
      }
    },
  )

  it.each(Object.entries(pages))('%s: no English written into the template', (_file, source) => {
    const template = source.split('<script')[0]
    expect(template).not.toMatch(/\s(label|placeholder|title|description|empty-text)="[A-Za-z]/)
    const text = template
      .replace(/<!--[\s\S]*?-->/g, ' ')
      .replace(/<(?:[^>"']|"[^"]*"|'[^']*')*>/g, ' ') // tags, including quoted attributes holding `=>`
      .replace(/{{[\s\S]*?}}/g, ' ')
    expect(text.match(/[A-Za-z]{2,}/g)).toBeNull()
  })
})
