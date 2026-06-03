// MFE i18n compatibility layer
// In user-portal, this exports the i18n instance.
// In MFE mode, the actual i18n is created in App.vue self-bootstrap.
// This module provides locale messages for reference.

import en from './en'
import zhCN from './zh-CN'
import zhTW from './zh-TW'

export const messages = { en, 'zh-CN': zhCN, 'zh-TW': zhTW }
export { en, zhCN, zhTW }

// Default export for compatibility with user-portal code
export default {
  global: {
    t: (key: string) => key,
    locale: { value: 'en' }
  }
}
