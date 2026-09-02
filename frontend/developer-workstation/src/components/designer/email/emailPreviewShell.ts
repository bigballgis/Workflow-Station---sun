import DOMPurify from 'dompurify'

export const EMAIL_BODY_FONT =
  "Arial, Helvetica, 'Microsoft YaHei', sans-serif"

export type EmailBodyEditorMode = 'visual' | 'html'

const EMAIL_BODY_EDITOR_MODE_KEY_PREFIX = 'dw-email-body-editor-mode'

export function parseEmailBodyEditorMode(value: unknown): EmailBodyEditorMode {
  return value === 'html' ? 'html' : 'visual'
}

export function emailBodyEditorModeStorageKey(
  functionUnitId: number,
  templateId: number,
): string {
  return `${EMAIL_BODY_EDITOR_MODE_KEY_PREFIX}:${functionUnitId}:${templateId}`
}

function isPersistedTemplateId(templateId: number): boolean {
  return Number.isInteger(templateId) && templateId > 0
}

/** Last mode written on successful Save. Missing or unreadable storage → Visual. */
export function readSavedEmailBodyEditorMode(
  functionUnitId: number,
  templateId: number,
): EmailBodyEditorMode {
  if (!isPersistedTemplateId(templateId)) return 'visual'
  try {
    return parseEmailBodyEditorMode(
      localStorage.getItem(emailBodyEditorModeStorageKey(functionUnitId, templateId)),
    )
  } catch {
    // FALLBACK(ux): storage unavailable — open Visual until the next Save
    return 'visual'
  }
}

/** Persist only after template Save succeeds. */
export function writeSavedEmailBodyEditorMode(
  functionUnitId: number,
  templateId: number,
  mode: EmailBodyEditorMode,
): void {
  if (!isPersistedTemplateId(templateId)) return
  try {
    localStorage.setItem(
      emailBodyEditorModeStorageKey(functionUnitId, templateId),
      parseEmailBodyEditorMode(mode),
    )
  } catch {
    // FALLBACK(ux): storage unavailable — next open defaults to Visual
  }
}

const EMAIL_BODY_PURIFY = {
  ADD_TAGS: ['thead', 'tbody', 'tfoot', 'col', 'colgroup', 'style'],
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
  /** Keep leading <style> in the body fragment; otherwise the parser moves it to <head> and it is dropped. */
  FORCE_BODY: true,
} as const

/**
 * wangEditor does not fire onChange when HTML is first parsed into Visual.
 * Preview reads the same model as the HTML pane, so the parent must replace
 * that model with getHtml() after the Visual editor is created.
 */
export function htmlFromVisualEditor(
  editor: { getHtml: () => string } | null | undefined,
): string | null {
  if (editor == null || typeof editor.getHtml !== 'function') return null
  return editor.getHtml()
}

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
