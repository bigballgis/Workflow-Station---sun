/** Profile 页展示用户账户语言偏好（zh-CN / zh-TW / en），随当前界面 locale 切换文案。 */
export function languageLabelFor(code: string | undefined, loc: string): string {
  const c = (code || 'zh-CN').replace('_', '-')
  const en = loc.startsWith('en')
  const tw = loc === 'zh-TW'
  if (en) {
    const m: Record<string, string> = {
      'zh-CN': 'Simplified Chinese',
      'zh-TW': 'Traditional Chinese',
      en: 'English'
    }
    return m[c] || c
  }
  if (tw) {
    const m: Record<string, string> = {
      'zh-CN': '簡體中文',
      'zh-TW': '繁體中文',
      en: 'English'
    }
    return m[c] || c
  }
  const m: Record<string, string> = {
    'zh-CN': '简体中文',
    'zh-TW': '繁體中文',
    en: 'English'
  }
  return m[c] || c
}
