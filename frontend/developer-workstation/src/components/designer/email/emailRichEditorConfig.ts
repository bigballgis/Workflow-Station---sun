/**
 * Curated wangEditor toolbar for email body — fewer items than default,
 * avoids wide font/line-height dropdowns and non-email features (video, emoji, code).
 */
export const EMAIL_RICH_TOOLBAR_KEYS = [
  'headerSelect',
  'blockquote',
  '|',
  'bold',
  'underline',
  'italic',
  'through',
  'color',
  'bgColor',
  '|',
  'bulletedList',
  'numberedList',
  'justifyLeft',
  'justifyCenter',
  'justifyRight',
  '|',
  'insertLink',
  'insertImage',
  'uploadImage',
  'insertTable',
  '|',
  'undo',
  'redo',
  'fullScreen',
] as const

export function buildEmailRichToolbarConfig() {
  return {
    toolbarKeys: [...EMAIL_RICH_TOOLBAR_KEYS],
  }
}

export function buildEmailRichEditorConfig(placeholder: string) {
  return {
    placeholder,
    MENU_CONF: {},
  }
}
