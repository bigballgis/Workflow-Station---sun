/**
 * fc-designer built-in Preview (toolbar) — normalize validation before preview opens.
 */

import { getRuleChildren } from '@/utils/formDesigner'
import { ensureFormCreateRulesValidationDeep } from '@/utils/formCreateValidateRules'
import {
  mergeComponentEventsFromSavedRules,
  syncDesignerComponentEventsForFcPreview,
} from '@/utils/formCreatePreviewEvents'
import { seedFcDesignerPreviewComponentEventCache } from '@/utils/formCreateLookupComponentEvents'

export type DesignerPreviewRef = {
  openPreview?: () => void
  getRule?: () => unknown[]
  setRule?: (rules: unknown[]) => void
  getOption?: () => Record<string, unknown>
  setOption?: (opt: Record<string, unknown>) => void
  __hermesPreviewCapture?: boolean
  activeRule?: Record<string, unknown> | null
  baseForm?: {
    api?: { formData?: () => Record<string, unknown> }
  }
  validateForm?: {
    api?: { formData?: () => Record<string, unknown> }
    value?: Record<string, unknown> | unknown[]
  }
  propsForm?: {
    api?: { formData?: () => Record<string, unknown> }
    value?: Record<string, unknown> | unknown[]
  }
}

function findRuleByField(rules: unknown[], field: string): Record<string, unknown> | null {
  for (const raw of rules || []) {
    if (!raw || typeof raw !== 'object') continue
    const rule = raw as Record<string, unknown>
    if (rule.field === field) return rule
    const children = getRuleChildren(rule)
    const nested = findRuleByField(children, field)
    if (nested) return nested
  }
  return null
}

function resolveFlushTargetRule(ref: DesignerPreviewRef): Record<string, unknown> | null {
  if (ref.activeRule && typeof ref.activeRule === 'object') {
    return ref.activeRule
  }
  const baseField = ref.baseForm?.api?.formData?.()?.field
  if (typeof baseField !== 'string' || !baseField || !ref.getRule) return null
  return findRuleByField(ref.getRule() || [], baseField)
}

export type FlushValidatePanelResult = {
  flushed: boolean
  field?: string
  validate?: unknown
  $required?: unknown
  source?: 'formData' | 'value' | 'none'
}

export type FlushPropsPanelResult = {
  flushed: boolean
  field?: string
  readonly?: boolean
  source?: 'propsForm' | 'baseForm' | 'none'
}

function readDesignerPanelFormData(
  api?: { formData?: () => Record<string, unknown> },
  fallbackValue?: Record<string, unknown> | unknown[],
): Record<string, unknown> | null {
  if (typeof api?.formData === 'function') {
    try {
      const data = api.formData()
      if (data && typeof data === 'object' && !Array.isArray(data) && Object.keys(data).length > 0) {
        return data as Record<string, unknown>
      }
    } catch {
      /* ignore designer panel read errors */
    }
  }
  if (fallbackValue && typeof fallbackValue === 'object' && !Array.isArray(fallbackValue)) {
    return fallbackValue as Record<string, unknown>
  }
  return null
}

/** fc-designer stores some props as `formCreateProps>readonly` in the props panel formData. */
function extractReadonlyFromDesignerPanel(panel: Record<string, unknown> | null): boolean | undefined {
  if (!panel) return undefined
  if ('readonly' in panel && typeof panel.readonly === 'boolean') return panel.readonly
  if ('formCreateProps>readonly' in panel && typeof panel['formCreateProps>readonly'] === 'boolean') {
    return panel['formCreateProps>readonly'] as boolean
  }
  if ('props>readonly' in panel && typeof panel['props>readonly'] === 'boolean') {
    return panel['props>readonly'] as boolean
  }
  const nestedProps = panel.props
  if (nestedProps && typeof nestedProps === 'object' && !Array.isArray(nestedProps)) {
    const ro = (nestedProps as Record<string, unknown>).readonly
    if (typeof ro === 'boolean') return ro
  }
  return undefined
}

function applyReadonlyFlushToActiveRule(
  activeRule: Record<string, unknown>,
  panelReadonly: boolean,
): boolean {
  const props = { ...((activeRule.props as Record<string, unknown> | undefined) || {}) }
  if (panelReadonly === false) {
    activeRule.readonly = false
    props.readonly = false
    delete props.disabled
    delete activeRule.disabled
    activeRule.props = props
    return true
  }
  if (panelReadonly === true) {
    activeRule.readonly = true
    props.readonly = true
    activeRule.props = props
    return true
  }
  return false
}

/**
 * fc-designer props panel can show Readonly OFF while rule.readonly (PK sync) stays true.
 * Flush panel state onto activeRule before getRule()/Save so persist + metadata sync respect designer intent.
 */
export function flushDesignerPropsPanelToActiveRule(
  ref: DesignerPreviewRef | null | undefined,
): FlushPropsPanelResult {
  commitDesignerPanelEditsBeforePreview()
  if (!ref || typeof ref !== 'object') {
    return { flushed: false, source: 'none' }
  }
  const activeRule = resolveFlushTargetRule(ref)
  if (!activeRule) {
    return { flushed: false, source: 'none' }
  }

  const propsPanel = readDesignerPanelFormData(ref.propsForm?.api, ref.propsForm?.value)
  let panelReadonly = extractReadonlyFromDesignerPanel(propsPanel)
  let source: FlushPropsPanelResult['source'] = propsPanel ? 'propsForm' : 'none'

  if (panelReadonly === undefined) {
    const basePanel = readDesignerPanelFormData(ref.baseForm?.api)
    panelReadonly = extractReadonlyFromDesignerPanel(basePanel)
    if (basePanel && panelReadonly !== undefined) {
      source = 'baseForm'
    }
  }

  if (panelReadonly === undefined) {
    return { flushed: false, field: String(activeRule.field ?? ''), source: 'none' }
  }

  const flushed = applyReadonlyFlushToActiveRule(activeRule, panelReadonly)
  return {
    flushed,
    field: String(activeRule.field ?? ''),
    readonly: panelReadonly,
    source,
  }
}

/** Blur focused config-panel control so fc-designer commits pending validate/base edits. */
export function commitDesignerPanelEditsBeforePreview(): void {
  const active = document.activeElement
  if (!(active instanceof HTMLElement)) return
  if (!active.closest('._fc-m-con, ._fc-r, ._fd-config, .form-editor-view')) return
  active.blur()
}

/** fc-designer validate panel uses blur emit — flush pending edits onto live activeRule before getJson/getRule. */
export function flushDesignerValidatePanelToActiveRule(
  ref: DesignerPreviewRef | null | undefined,
): FlushValidatePanelResult {
  commitDesignerPanelEditsBeforePreview()
  if (!ref || typeof ref !== 'object') {
    return { flushed: false, source: 'none' }
  }
  const activeRule = resolveFlushTargetRule(ref)
  if (!activeRule) {
    return { flushed: false, source: 'none' }
  }

  let panelData: Record<string, unknown> | null = null
  let source: FlushValidatePanelResult['source'] = 'none'
  const formData = ref.validateForm?.api?.formData
  if (typeof formData === 'function') {
    try {
      const data = formData()
      if (data && typeof data === 'object' && Object.keys(data).length > 0) {
        panelData = data
        source = 'formData'
      }
    } catch {
      /* ignore designer panel read errors */
    }
  }
  if (!panelData) {
    const rawValue = ref.validateForm?.value
    if (rawValue && typeof rawValue === 'object' && !Array.isArray(rawValue)) {
      panelData = rawValue as Record<string, unknown>
      source = 'value'
    }
  }
  if (!panelData) {
    return { flushed: false, field: String(activeRule.field ?? ''), source: 'none' }
  }

  let flushed = false
  if ('validate' in panelData) {
    const nextValidate = panelData.validate
    if (Array.isArray(nextValidate)) {
      if (nextValidate.length === 0) {
        delete activeRule.validate
      } else {
        activeRule.validate = [...nextValidate]
      }
    } else {
      activeRule.validate = nextValidate
    }
    flushed = true
  }
  if ('$required' in panelData) {
    activeRule.$required = panelData.$required
    flushed = true
  }

  return {
    flushed,
    field: String(activeRule.field ?? ''),
    validate: activeRule.validate,
    $required: activeRule.$required,
    source,
  }
}

/** Options merged into designer state for the built-in preview dialog. */
export function mergePreviewValidateFormOption(
  option: Record<string, unknown> | undefined,
  // Kept for call-site compatibility; the built-in submit/validate button is now hidden.
  _validateButtonText?: string,
): Record<string, unknown> {
  const base = option && typeof option === 'object' ? { ...option } : {}
  const form =
    base.form && typeof base.form === 'object'
      ? { ...(base.form as Record<string, unknown>) }
      : {}
  form.showMessage = true
  base.form = form
  base.validateOnSubmit = true
  // Ensure component on.blur/change receive inject.api in built-in Form mode preview.
  base.injectEvent = true
  // Preview is for layout/field inspection only — hide form-create's built-in bottom
  // submit/validate button (it ran api.submit() silently, looking like it did nothing).
  // Designer-placed Validate button components in the form rules are unaffected.
  base.submitBtn = false
  const prevReset =
    base.resetBtn && typeof base.resetBtn === 'object'
      ? (base.resetBtn as Record<string, unknown>)
      : {}
  base.resetBtn = { ...prevReset, show: false }
  base.onSubmit = base.onSubmit ?? (() => {
    /* preview-only validate; no submit action */
  })
  return base
}

export function snapshotDesignerFieldValidate(rules: unknown[]): Array<Record<string, unknown>> {
  const out: Array<Record<string, unknown>> = []

  function walk(items: unknown[]) {
    for (const raw of items || []) {
      if (!raw || typeof raw !== 'object') continue
      const rule = raw as Record<string, unknown>
      if (rule.field) {
        out.push({
          field: rule.field,
          type: rule.type,
          $required: rule.$required,
          validate: rule.validate,
        })
      }
      const children = getRuleChildren(rule)
      if (children.length) walk(children)
    }
  }

  walk(rules)
  return out
}

/**
 * Persist normalized validation onto the live designer (getRule() alone is a parsed copy).
 * Must run before fc-designer openPreview → getJson().
 *
 * Do NOT compile event handlers onto the canvas here — openPreview serializes via getJson()
 * and would drop Hermes closures. Event compilation runs on preview.rule AFTER openPreview
 * (see {@link patchFcDesignerPreviewEventHandlers}).
 */
export function prepareDesignerPreviewValidation(
  ref: DesignerPreviewRef | null | undefined,
  validateButtonText: string,
  savedRules?: unknown[] | null,
): {
  applied: boolean
  fieldRules: Array<Record<string, unknown>>
  flush: FlushValidatePanelResult
  validateOnSubmit?: unknown
} {
  const flush = flushDesignerValidatePanelToActiveRule(ref)
  logValidatePanelFlush('prepare', flush)
  if (!ref?.getRule || !ref?.setRule) {
    return { applied: false, fieldRules: [], flush }
  }
  const rules = ref.getRule() || []
  if (Array.isArray(savedRules) && savedRules.length > 0) {
    mergeComponentEventsFromSavedRules(rules, savedRules)
  }
  ensureFormCreateRulesValidationDeep(rules)
  ref.setRule(rules)
  const option = ref.getOption?.()
  if (option && ref.setOption) {
    ref.setOption(mergePreviewValidateFormOption(option, validateButtonText))
  }
  const fieldRules = snapshotDesignerFieldValidate(rules)
  return {
    applied: true,
    fieldRules,
    flush,
    validateOnSubmit: ref.getOption?.()?.validateOnSubmit,
  }
}

type FcPreviewBag = {
  state?: boolean
  rule?: unknown[]
  option?: Record<string, unknown>
}

/** FcDesigner setup returns `preview` via toRefs(data) — resolve on the public instance. */
export function resolveFcDesignerPreviewBag(
  ref: DesignerPreviewRef | null | undefined,
): FcPreviewBag | null {
  if (!ref || typeof ref !== 'object') return null
  const bag = ref as Record<string, unknown>
  let preview: unknown = bag.preview
  if (preview && typeof preview === 'object' && 'value' in (preview as object)) {
    const inner = (preview as { value: unknown }).value
    if (inner && typeof inner === 'object' && ('rule' in (inner as object) || 'state' in (inner as object))) {
      preview = inner
    }
  }
  if (!preview || typeof preview !== 'object') return null
  return preview as FcPreviewBag
}

/**
 * After fc-designer openPreview (getJson→parseJson), recompile `$FNX:` handlers with Hermes
 * wrappers so `$inject.value` is filled from args[0]. Native form-create inject omits `value`.
 */
export function patchFcDesignerPreviewEventHandlers(
  ref: DesignerPreviewRef | null | undefined,
  validateButtonText: string,
): boolean {
  const preview = resolveFcDesignerPreviewBag(ref)
  if (!preview || !Array.isArray(preview.rule) || preview.rule.length === 0) return false
  syncDesignerComponentEventsForFcPreview(preview.rule)
  seedFcDesignerPreviewComponentEventCache(preview.rule)
  if (preview.option && typeof preview.option === 'object') {
    Object.assign(preview.option, mergePreviewValidateFormOption(preview.option, validateButtonText))
  }
  return true
}

function logValidatePanelFlush(_source: string, _flush: FlushValidatePanelResult): void {
  /* no-op */
}

function logPreviewPrep(
  _source: string,
  _result: {
    applied: boolean
    fieldRules: Array<Record<string, unknown>>
    flush: FlushValidatePanelResult
    validateOnSubmit?: unknown
  },
): void {
  /* no-op */
}

function isFcDesignerPreviewToolbarButton(target: EventTarget | null): boolean {
  const btn = (target as HTMLElement | null)?.closest?.('button')
  return !!btn?.querySelector?.('.icon-preview')
}

function runPreviewToolbarPrep(
  source: string,
  getDesignerRef: () => DesignerPreviewRef | null | undefined,
  validateButtonText: string,
  getSavedRules?: () => unknown[] | null | undefined,
): void {
  const result = prepareDesignerPreviewValidation(
    getDesignerRef(),
    validateButtonText,
    getSavedRules?.() ?? null,
  )
  logPreviewPrep(`capture-${source}`, result)
}

/** Capture toolbar Preview mousedown/click — click runs after focus leaves canvas and may clear activeRule. */
export function installFcDesignerPreviewCapture(
  root: Element | null | undefined,
  getDesignerRef: () => DesignerPreviewRef | null | undefined,
  validateButtonText: string,
  getSavedRules?: () => unknown[] | null | undefined,
): void {
  if (!root || (root as DesignerPreviewRef).__hermesPreviewCapture) return
  const onPreviewToolbarEvent = (source: string) => (ev: Event) => {
    if (!isFcDesignerPreviewToolbarButton(ev.target)) return
    runPreviewToolbarPrep(source, getDesignerRef, validateButtonText, getSavedRules)
  }
  root.addEventListener('mousedown', onPreviewToolbarEvent('mousedown'), true)
  root.addEventListener('click', onPreviewToolbarEvent('click'), true)
  // fc-designer @click="openPreview" runs on the button (target). Capture is too early
  // (preview.rule not assigned yet); wrapping instance.openPreview does not replace the
  // template handler. Bubble on the designer root runs after openPreview, before Vue flush.
  root.addEventListener('click', (ev: Event) => {
    if (!isFcDesignerPreviewToolbarButton(ev.target)) return
    const ref = getDesignerRef()
    wrapFcDesignerOpenPreview(ref, validateButtonText, getSavedRules)
    patchFcDesignerPreviewEventHandlers(ref, validateButtonText)
  }, false)
  ;(root as DesignerPreviewRef).__hermesPreviewCapture = true
}

export function installPreviewValidationDomProbe(): void {
  /* no-op */
}

export function wrapFcDesignerOpenPreview(
  ref: DesignerPreviewRef | null | undefined,
  validateButtonText: string,
  getSavedRules?: () => unknown[] | null | undefined,
): void {
  if (!ref || typeof ref !== 'object') return
  const bag = ref as Record<string, unknown>
  if (bag.__hermesOpenPreviewWrapped) return
  const original = ref.openPreview
  if (typeof original !== 'function') return

  ref.openPreview = function hermesWrappedOpenPreview(this: unknown, ...args: unknown[]) {
    prepareDesignerPreviewValidation(ref, validateButtonText, getSavedRules?.() ?? null)
    const result = (original as (...a: unknown[]) => unknown).apply(this, args)
    // Must run AFTER getJson→parseJson assigns preview.rule (same sync turn, before ViewForm mount).
    patchFcDesignerPreviewEventHandlers(ref, validateButtonText)
    return result
  }
  bag.__hermesOpenPreviewWrapped = true
}

export function patchDesignerOpenPreview(
  ref: DesignerPreviewRef | null | undefined,
  validateButtonText: string,
  getSavedRules?: () => unknown[] | null | undefined,
): void {
  if (!ref) return
  const result = prepareDesignerPreviewValidation(ref, validateButtonText, getSavedRules?.() ?? null)
  logPreviewPrep('patch-call', result)
}

export function patchDesignerOpenPreviewAll(
  refs: Array<DesignerPreviewRef | null | undefined>,
  validateButtonText: string,
  getSavedRules?: () => unknown[] | null | undefined,
): void {
  for (const ref of refs) patchDesignerOpenPreview(ref, validateButtonText, getSavedRules)
}

export function syncDesignerRulesValidationToCanvas(
  ref: DesignerPreviewRef | null | undefined,
): boolean {
  flushDesignerValidatePanelToActiveRule(ref)
  if (!ref?.getRule || !ref?.setRule) return false
  const rules = ref.getRule() || []
  ensureFormCreateRulesValidationDeep(rules)
  ref.setRule(rules)
  return true
}
