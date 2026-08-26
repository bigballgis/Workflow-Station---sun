import DOMPurify from 'dompurify'

export const EMAIL_BODY_FONT =
  "Arial, Helvetica, 'Microsoft YaHei', sans-serif"

export type EmailBodyEditorMode = 'visual' | 'html'

const EMAIL_BODY_PURIFY = {
  ADD_TAGS: ['thead', 'tbody', 'tfoot', 'col', 'colgroup'],
  ADD_ATTR: [
    'style',
    'align',
    'valign',
    'width',
    'height',
    'cellpadding',
    'cellspacing',
    'border',
    'role',
    'bgcolor',
  ],
  ALLOW_DATA_ATTR: false,
} as const

/** True when leaving HTML source for wangEditor (may simplify markup). */
export function isSwitchToVisual(
  from: EmailBodyEditorMode,
  to: EmailBodyEditorMode,
): boolean {
  return from === 'html' && to === 'visual'
}

export function sanitizeEmailBodyHtml(html: string): string {
  return DOMPurify.sanitize(html || '', EMAIL_BODY_PURIFY)
}

/** Design-time iframe document: email-safe shell, placeholders left as ${token}. */
export function wrapEmailPreviewDocument(bodyHtml: string): string {
  const inner = sanitizeEmailBodyHtml(bodyHtml)
  return (
    '<!DOCTYPE html><html style="height:100%"><head><meta charset="utf-8"></head>' +
    '<body style="margin:0;padding:16px;height:100%;box-sizing:border-box;background:#ffffff;">' +
    '<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" ' +
    'style="width:100%;height:100%;background:#ffffff;">' +
    '<tr><td style="font-family:' +
    EMAIL_BODY_FONT +
    ';font-size:14px;line-height:1.5;color:#333333;padding:16px;">' +
    inner +
    '</td></tr></table></body></html>'
  )
}

export function insertAtCursor(
  value: string,
  token: string,
  start: number,
  end: number,
): string {
  const from = Math.max(0, Math.min(start, value.length))
  const to = Math.max(from, Math.min(end, value.length))
  return value.slice(0, from) + token + value.slice(to)
}
