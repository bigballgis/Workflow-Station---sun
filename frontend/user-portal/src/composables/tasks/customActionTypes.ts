import type { FormField, FormTab, PortalViewContext } from '@/components/formRendererHelpers'

/**
 * Prepared FORM_POPUP rendering context — built by the host view from the
 * popup's target form content (configJson + tableBindings + cachedContentForms).
 * Mirrors what FormRenderer needs to render the popup at parity with the
 * Designer Form Preview (subTable widgets, Link Form targets, portalViews).
 */
export interface PreparedFormPopupContext {
  fields: FormField[]
  tabs: FormTab[]
  subTableBindings: any[]
  linkedSubTableBindings?: any[] | null
  nativeSubTableBindingIds: number[]
  formConfig: Record<string, unknown>
  viewContext?: PortalViewContext
}
