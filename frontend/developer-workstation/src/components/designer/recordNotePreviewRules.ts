/**
 * Record Note in form-create-rendered previews.
 *
 * The `recordNote` type resolves to the designer canvas placeholder (RecordNotePlaceholderWidget
 * — a dashed drop target reading "active in User Portal after deploy"), which is right on the
 * canvas and wrong in Preview. Preview surfaces that render a rule through one form-create
 * instance (the sub-table Add/Edit dialog, Link Form modal) cannot lift the node out of the rule
 * the way FormPreviewItems does, so they retype it to a preview-only component instead.
 *
 * Registered as `recordNotePreview` in main.ts.
 */
export const RECORD_NOTE_PREVIEW_TYPE = 'recordNotePreview'

/**
 * Retype every `recordNote` node in place so form-create renders RecordNotePreview.
 * Config travels as `props.config` because the preview component takes one `config` object,
 * mirroring the portal's RecordNoteField.
 */
export function retypeRecordNoteRulesForPreview(rules: any[]): void {
  const walk = (list: any[]) => {
    for (const rule of list || []) {
      if (!rule || typeof rule !== 'object') continue
      if (rule.type === 'recordNote') {
        rule.type = RECORD_NOTE_PREVIEW_TYPE
        rule.props = { config: { ...(rule.props ?? {}) } }
      }
      if (Array.isArray(rule.children)) walk(rule.children)
    }
  }
  walk(rules)
}
