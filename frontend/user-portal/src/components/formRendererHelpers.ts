/**
 * Shared helpers for FormRenderer — extracted so they can be imported by both
 * the Vue component (which uses <script setup>) and unit/property tests.
 */

/** Coerce designer native binding id lists (number[]) into Set for `.has()` lookups. */
export function asNumberSet(
  src: Set<number> | ReadonlySet<number> | Iterable<number> | null | undefined,
): Set<number> {
  if (src == null) return new Set()
  if (src instanceof Set) return new Set(src)
  return new Set([...src].map(Number).filter(Number.isFinite))
}

/**
 * Sub-table portal display strategy. Designed in developer-workstation's
 * FormDesigner (Sub-Table property panel → "Portal Display") and consumed
 * by user-portal's FormRenderer to decide rendering per page (To Do vs My Request).
 */
export type SubTableAssigneeTodoMode = 'formBelowTable' | 'tableOnly'
export type SubTableInitiatorRequestMode = 'mirrorTodo' | 'summaryWithLinkFormModal' | 'tableOnly'
export type SubTableFormSourceType = 'subForm' | 'linkForm' | 'formId'

export interface SubTablePortalViews {
  assigneeTodo: SubTableAssigneeTodoMode
  assigneeTodoFormSource?: {
    type: SubTableFormSourceType
    formId?: number | string | null
    /**
     * When `type='linkForm'`, identifies WHICH Link Form column on the sub-table's
     * list view should drive the inline form-below-table. Matched against the
     * `componentId` of `dw_link_form_components`. Unset → runtime falls back to the
     * first Link Form column it finds on the binding (legacy behavior).
     */
    linkFormColumnId?: number | string | null
  }
  initiatorRequest: SubTableInitiatorRequestMode
}

/**
 * View context driving how FormRenderer resolves portalViews on subTable nodes.
 * - `assigneeTodo`: To Do detail page (办理人待办)
 * - `initiatorRequest`: My Request / process instance detail (发起人我的申请)
 */
export type PortalViewContext = 'assigneeTodo' | 'initiatorRequest'

export interface FormField {
  key: string
  label: string
  type: string
  required?: boolean
  placeholder?: string
  span?: number
  options?: Array<{ label: string; value: string | number }>
  multiple?: boolean
  filterable?: boolean
  maxLength?: number
  min?: number
  max?: number
  step?: number
  precision?: number
  rows?: number
  activeText?: string
  inactiveText?: string
  cascaderProps?: object
  currency?: string
  alertTitle?: string
  alertType?: 'success' | 'warning' | 'info' | 'error'
  userOptions?: Array<{ id: string; name: string }>
  buOptions?: Array<{ id: string; name: string; code?: string }>
  rules?: Array<Record<string, unknown>>
  defaultValue?: string | number | boolean | null
  tabName?: string
  uploadUrl?: string
  uploadAccept?: string
  uploadLimit?: number
  _bindingId?: number  // set when type === 'subTable'
  /** Designer-driven portal display strategy; only present when type === 'subTable'. */
  portalViews?: SubTablePortalViews
  children?: FormField[] // set for layout containers such as card
  /** Nested el-tabs inside a tab pane (type === 'tabs'). */
  tabs?: FormTab[]
  /** Nested el-collapse panels (type === 'collapse'). */
  collapsePanels?: FormCollapsePanel[]
  /** fcTitle size hint (type === 'title'). */
  titleSize?: string
  /** Static HTML block (type === 'html'). */
  htmlContent?: string
  /** Per-field read-only from designer rule props.readonly / disabled. */
  readonly?: boolean
  /** Designer "Hide" — form-create rule.hidden; field omitted from layout when true. */
  hidden?: boolean
  /** Row layout gutter (type === 'row'). */
  gutter?: number
}

/** True when a form-create rule marks the field read-only (designer Props → readonly or disabled). */
export function isFormCreateRuleReadonly(rule: unknown): boolean {
  if (!rule || typeof rule !== 'object') return false
  const r = rule as Record<string, unknown>
  const props = (r.props as Record<string, unknown> | undefined) || {}
  return (
    r.disabled === true ||
    r.readonly === true ||
    props.disabled === true ||
    props.readonly === true
  )
}

/** Designer "Hide" toggle — form-create `rule.hidden` / `_hidden` / `display: false`. */
export function isFormCreateRuleHidden(rule: unknown): boolean {
  if (!rule || typeof rule !== 'object') return false
  const r = rule as Record<string, unknown>
  const props = (r.props as Record<string, unknown> | undefined) || {}
  return (
    r.hidden === true ||
    r._hidden === true ||
    r.display === false ||
    r._display === false ||
    props.hidden === true ||
    props.hide === true
  )
}

export function isFormFieldReadonly(field: FormField, formReadonly = false): boolean {
  return formReadonly || field.readonly === true
}

/**
 * Default portalViews applied when a subTable rule node carries no `props.portalViews`
 * (i.e. legacy forms designed before this feature). Keeps current runtime behavior:
 * just render the sub-table, no nested form-below, no Details modal forced.
 */
export const DEFAULT_PORTAL_VIEWS: SubTablePortalViews = Object.freeze({
  assigneeTodo: 'tableOnly',
  assigneeTodoFormSource: { type: 'subForm', formId: null },
  initiatorRequest: 'mirrorTodo'
})

/**
 * Resolve the effective display mode at a given view context.
 * - In My Request, `mirrorTodo` falls through to the assigneeTodo mode.
 * - Missing portalViews falls back to DEFAULT_PORTAL_VIEWS ("tableOnly" everywhere).
 *
 * Accepts `Partial<SubTablePortalViews>` so callers can pass binding-level fragments
 * (loaded from form configJson) without normalizing first; missing properties fall
 * through to DEFAULT_PORTAL_VIEWS values.
 */
export function resolveSubTableDisplayMode(
  portalViews: Partial<SubTablePortalViews> | undefined | null,
  context: PortalViewContext
): SubTableAssigneeTodoMode | 'summaryWithLinkFormModal' {
  const pv = portalViews && typeof portalViews === 'object' ? portalViews : DEFAULT_PORTAL_VIEWS
  if (context === 'assigneeTodo') {
    return pv.assigneeTodo === 'tableOnly' ? 'tableOnly' : 'formBelowTable'
  }
  // initiatorRequest
  if (pv.initiatorRequest === 'summaryWithLinkFormModal') return 'summaryWithLinkFormModal'
  if (pv.initiatorRequest === 'tableOnly') return 'tableOnly'
  // mirrorTodo (default) → fall through to assigneeTodo
  return pv.assigneeTodo === 'tableOnly' ? 'tableOnly' : 'formBelowTable'
}

export interface FormTab {
  name: string
  label: string
  fields: FormField[]
}

export interface FormCollapsePanel {
  name: string
  label: string
  fields: FormField[]
}

const LAYOUT_ONLY_FIELD_KEY_PREFIXES = ['__subTable_', '__layout_']

function isDataBoundFormFieldKey(key: string): boolean {
  if (!key || key.startsWith('__')) return false
  return !LAYOUT_ONLY_FIELD_KEY_PREFIXES.some(p => key.startsWith(p))
}

/**
 * Collect data-bound field keys from parsed {@link FormField} trees, recursing into
 * {@code elCard} children (and tab panes). Used by MI isolation and My Request hydration
 * so scalars inside Case Info cards (e.g. {@code case_number}, {@code legal_hold}) are not dropped.
 */
export function collectLeafFormFieldKeys(
  fields: FormField[],
  tabs?: FormTab[],
): string[] {
  const keys = new Set<string>()
  const walk = (arr?: FormField[]) => {
    if (!Array.isArray(arr)) return
    for (const f of arr) {
      if (f.type === 'tabs' && Array.isArray(f.tabs)) {
        for (const tab of f.tabs) walk(tab.fields)
        continue
      }
      if (f.type === 'collapse' && Array.isArray(f.collapsePanels)) {
        for (const panel of f.collapsePanels) walk(panel.fields)
        continue
      }
      if ((f.type === 'card' || f.type === 'row' || f.type === 'col') && Array.isArray(f.children)) {
        walk(f.children)
        continue
      }
      if (isDisplayOnlyLayoutField(f)) continue
      if (f.type === 'subTable') continue
      const key = f.key != null ? String(f.key) : ''
      if (isDataBoundFormFieldKey(key)) keys.add(key)
    }
  }
  walk(fields)
  for (const tab of tabs || []) {
    walk(tab.fields)
  }
  return Array.from(keys)
}

/** Flatten parsed layout trees into leaf {@link FormField} entries for init/rules. */
export function flattenLeafFormFields(items?: FormField[]): FormField[] {
  if (!Array.isArray(items) || items.length === 0) return []
  const flatten = (arr: FormField[]): FormField[] =>
    arr.flatMap(field => {
      if (field.type === 'tabs' && Array.isArray(field.tabs)) {
        return field.tabs.flatMap(tab => flatten(tab.fields))
      }
      if (field.type === 'collapse' && Array.isArray(field.collapsePanels)) {
        return field.collapsePanels.flatMap(panel => flatten(panel.fields))
      }
      if (
        (field.type === 'card' || field.type === 'row' || field.type === 'col')
        && Array.isArray(field.children)
      ) {
        return flatten(field.children)
      }
      if (field.children?.length) return flatten(field.children)
      return [field]
    })
  return flatten(items)
}

/** All data-bound leaf fields across before-tabs, tab panes, and after-tabs segments. */
export function flattenAllFormFieldSegments(
  fields?: FormField[],
  tabs?: FormTab[],
  fieldsAfterTabs?: FormField[],
): FormField[] {
  const merged: FormField[] = []
  merged.push(...flattenLeafFormFields(fields))
  for (const tab of tabs || []) {
    merged.push(...flattenLeafFormFields(tab.fields))
  }
  merged.push(...flattenLeafFormFields(fieldsAfterTabs))
  return merged
}

/**
 * Recursively extract FormField entries from a form-create rule array.
 * Handles subTable placeholder entries (type === 'subTable') before delegating
 * regular field items to the provided converter.
 *
 * @param items - Array of form-create rule items
 * @param converter - Function that converts a regular rule item to a FormField (or null to skip)
 */
export function getRuleChildren(item: Record<string, unknown>): Record<string, unknown>[] {
  const props = item.props as Record<string, unknown> | undefined
  const sources = [
    item.children,
    props?.children,
    props?.list,
    props?.items,
    props?.fields,
  ]
  return (sources.find(children => Array.isArray(children)) as Record<string, unknown>[]) || []
}

export function isRowRule(item: Record<string, unknown>): boolean {
  const type = String(item.type ?? '')
  return type === 'fcRow' || type === 'row' || type === 'el-row'
}

export function isColRule(item: Record<string, unknown>): boolean {
  const type = String(item.type ?? '')
  return type === 'fcCol' || type === 'col' || type === 'el-col'
}

export function getColSpan(item: Record<string, unknown>): number {
  const col = item.col as Record<string, unknown> | undefined
  const props = item.props as Record<string, unknown> | undefined
  if (typeof col?.span === 'number') return col.span
  if (typeof props?.span === 'number') return props.span
  return 24
}

export function getRowGutter(item: Record<string, unknown>): number {
  const props = item.props as Record<string, unknown> | undefined
  const gutter = props?.gutter
  return typeof gutter === 'number' && Number.isFinite(gutter) ? gutter : 20
}

const AUXILIARY_LAYOUT_TYPE_MAP: Record<string, FormField['type']> = {
  fcTitle: 'title',
  title: 'title',
  text: 'staticText',
  html: 'html',
  divider: 'divider',
  elDivider: 'divider',
  alert: 'alert',
  fcAlert: 'alert',
  tag: 'tag',
  elTag: 'tag',
  button: 'button',
  fcButton: 'button',
  space: 'space',
  fcSpace: 'space',
  image: 'image',
  fcImage: 'image',
}

const DISPLAY_ONLY_LAYOUT_TYPES = new Set<string>([
  'title',
  'staticText',
  'html',
  'divider',
  'alert',
  'tag',
  'button',
  'space',
  'image',
])

export function isDisplayOnlyLayoutField(field: FormField): boolean {
  return DISPLAY_ONLY_LAYOUT_TYPES.has(field.type)
}

export function isTabsRule(item: Record<string, unknown>): boolean {
  const type = String(item.type ?? '')
  return type === 'el-tabs' || type === 'elTabs' || type === 'tabs'
}

export function isTabPaneRule(item: Record<string, unknown>): boolean {
  const type = String(item.type ?? '')
  return type === 'el-tab-pane' || type === 'elTabPane' || type === 'tabPane' || type === 'TabPane'
}

/** Find the first tabs container rule in a top-level rule array. */
export function findTabsRule(rules: Record<string, unknown>[]): Record<string, unknown> | null {
  return rules.find(rule => isTabsRule(rule)) ?? null
}

export function isAuxiliaryLayoutRule(item: Record<string, unknown>): boolean {
  const type = String(item.type ?? '')
  return type in AUXILIARY_LAYOUT_TYPE_MAP
}

/** Convert designer auxiliary nodes (Title, Text, Divider, …) into display-only FormFields. */
export function convertAuxiliaryLayoutField(
  item: Record<string, unknown>,
  index: number,
): FormField | null {
  const rawType = String(item.type ?? '')
  const dragTag = String(item._fc_drag_tag ?? '')
  if (rawType === 'div' && dragTag === 'space') {
    const style = item.style as Record<string, unknown> | undefined
    const rawHeight = style?.height
    let step = 16
    if (typeof rawHeight === 'number' && Number.isFinite(rawHeight)) {
      step = rawHeight
    } else if (typeof rawHeight === 'string') {
      const parsed = parseInt(rawHeight, 10)
      if (Number.isFinite(parsed)) step = parsed
    }
    return {
      key: getLayoutKey(item, index, 'space'),
      label: '',
      type: 'space',
      span: 24,
      step,
    }
  }
  const mapped = AUXILIARY_LAYOUT_TYPE_MAP[rawType]
  if (!mapped) return null
  const props = item.props as Record<string, unknown> | undefined
  const field: FormField = {
    key: getLayoutKey(item, index, mapped),
    label: String(item.title ?? props?.title ?? props?.content ?? props?.value ?? props?.label ?? ''),
    type: mapped,
    span: getRuleSpan(item) || 24,
  }
  if (mapped === 'title') {
    field.titleSize = String(props?.size ?? props?.type ?? 'default')
  }
  if (mapped === 'alert') {
    field.alertTitle = field.label || String(props?.title ?? 'Alert')
    const alertType = props?.type
    if (alertType === 'success' || alertType === 'warning' || alertType === 'info' || alertType === 'error') {
      field.alertType = alertType
    } else {
      field.alertType = 'info'
    }
  }
  if (mapped === 'html') {
    field.htmlContent = String(props?.html ?? props?.content ?? field.label)
    field.label = ''
  }
  if (mapped === 'staticText') {
    field.label = String(props?.content ?? props?.value ?? item.title ?? field.label)
  }
  if (mapped === 'space') {
    field.step = typeof props?.height === 'number' ? props.height : 16
  }
  if (mapped === 'image') {
    field.defaultValue = String(props?.src ?? props?.url ?? '')
  }
  if (mapped === 'button') {
    field.label = String(item.title ?? props?.label ?? props?.text ?? 'Button')
  }
  if (mapped === 'tag') {
    field.label = String(item.title ?? props?.label ?? props?.text ?? field.label)
  }
  return field
}

function isLayoutContainerRule(item: Record<string, unknown>): boolean {
  return isCardRule(item) || isRowRule(item) || isColRule(item) || isTabsRule(item) || isCollapseRule(item) || isAuxiliaryLayoutRule(item)
}

function buildRowFormField(
  item: Record<string, unknown>,
  index: number,
  extractItems: (items: Record<string, unknown>[]) => FormField[],
): FormField {
  return {
    key: getLayoutKey(item, index, 'row'),
    label: '',
    type: 'row',
    span: 24,
    gutter: getRowGutter(item),
    children: extractRowColumnFields(item, extractItems),
  }
}

/** Map fcRow children (fcCol) into FormField columns preserving designer column layout. */
export function extractRowColumnFields(
  rowItem: Record<string, unknown>,
  extractItems: (items: Record<string, unknown>[]) => FormField[],
): FormField[] {
  const columns: FormField[] = []
  getRuleChildren(rowItem).forEach((colItem, index) => {
    if (isColRule(colItem)) {
      columns.push({
        key: getLayoutKey(colItem, index, 'col'),
        label: '',
        type: 'col',
        span: getColSpan(colItem),
        children: extractItems(getRuleChildren(colItem)),
      })
    } else if (isRowRule(colItem)) {
      columns.push(buildRowFormField(colItem, index, extractItems))
    } else {
      columns.push(...extractItems([colItem]))
    }
  })
  return columns
}

export function extractFieldsRecursive(
  items: Record<string, unknown>[],
  converter: (item: Record<string, unknown>) => FormField | null = () => null
): FormField[] {
  const fields: FormField[] = []
  for (let index = 0; index < items.length; index++) {
    const item = items[index]
    if (item.field && isFormCreateRuleHidden(item)) {
      continue
    }
    const props = item.props as Record<string, unknown> | undefined
    const bindingId = item._bindingId ?? props?._bindingId
    if (item.type === 'subTable' && bindingId != null) {
      if (isFormCreateRuleHidden(item)) {
        continue
      }
      const rawPv = props?.portalViews as Partial<SubTablePortalViews> | undefined
      const hasWidgetPortalViews =
        rawPv != null && typeof rawPv === 'object' && Object.keys(rawPv).length > 0
      fields.push({
        key: `__subTable_${bindingId}`,
        label: '',
        type: 'subTable',
        _bindingId: Number(bindingId),
        ...(hasWidgetPortalViews ? { portalViews: normalizePortalViews(rawPv) } : {}),
        span: 24
      })
      continue
    }
    if (isCardRule(item)) {
      fields.push({
        key: getLayoutKey(item, index, 'card'),
        label: getLayoutLabel(item),
        type: 'card',
        span: getRuleSpan(item),
        children: extractFieldsRecursive(getRuleChildren(item), converter),
      })
      continue
    }
    if (isRowRule(item)) {
      fields.push(buildRowFormField(item, index, (children) => extractFieldsRecursive(children, converter)))
      continue
    }
    if (isColRule(item)) {
      fields.push({
        key: getLayoutKey(item, index, 'col'),
        label: '',
        type: 'col',
        span: getColSpan(item),
        children: extractFieldsRecursive(getRuleChildren(item), converter),
      })
      continue
    }
    if (isTabsRule(item)) {
      const nestedTabs = extractTabsFromTabsRule(item, (children) => extractFieldsRecursive(children, converter))
      if (nestedTabs.length > 0) {
        fields.push({
          key: getLayoutKey(item, index, 'tabs'),
          label: '',
          type: 'tabs',
          span: 24,
          tabs: nestedTabs,
        })
      }
      continue
    }
    if (isCollapseRule(item)) {
      const collapsePanels = extractCollapsePanelsFromRule(item, (children) => extractFieldsRecursive(children, converter))
      if (collapsePanels.length > 0) {
        fields.push({
          key: getLayoutKey(item, index, 'collapse'),
          label: '',
          type: 'collapse',
          span: 24,
          collapsePanels,
        })
      }
      continue
    }
    const auxiliaryField = convertAuxiliaryLayoutField(item, index)
    if (auxiliaryField) {
      fields.push(auxiliaryField)
      continue
    }
    if (item.field) {
      const field = converter(item)
      if (field) fields.push(field)
    }
    if (!isLayoutContainerRule(item) && getRuleChildren(item).length > 0) {
      fields.push(...extractFieldsRecursive(getRuleChildren(item), converter))
    }
  }
  return fields
}

function isCardRule(item: Record<string, unknown>): boolean {
  const type = String(item.type ?? '')
  return type === 'el-card' || type === 'elCard' || type === 'card'
}

export { isCardRule }

export function isCollapseRule(item: Record<string, unknown>): boolean {
  const type = String(item.type ?? '')
  return type === 'el-collapse' || type === 'elCollapse' || type === 'collapse'
}

export function isCollapsePanelRule(item: Record<string, unknown>): boolean {
  const type = String(item.type ?? '')
  return type === 'el-collapse-item' || type === 'elCollapseItem' || type === 'collapseItem' || type === 'CollapseItem'
}

function getCollapsePanelLabel(props: Record<string, unknown>, index: number): string {
  const raw = props.title ?? props.label ?? props.name
  return raw != null && String(raw).trim() !== '' ? String(raw) : `Panel ${index + 1}`
}

/** Extract collapse panels from an `elCollapse` / `el-collapse` rule node. */
export function extractCollapsePanelsFromRule(
  collapseRule: Record<string, unknown> | null | undefined,
  extractFields: (items: Record<string, unknown>[]) => FormField[],
): FormCollapsePanel[] {
  if (!collapseRule || !Array.isArray(collapseRule.children)) return []
  const panels: FormCollapsePanel[] = []
  const usedNames = new Set<string>()
  for (const panelRule of collapseRule.children as Record<string, unknown>[]) {
    if (!isCollapsePanelRule(panelRule)) continue
    const props = (panelRule.props as Record<string, unknown> | undefined) || {}
    const rawName = props.name
    let panelName =
      rawName === undefined || rawName === null || rawName === ''
        ? `collapse_${panels.length}`
        : String(rawName)
    let uniqueName = panelName
    let dup = 0
    while (usedNames.has(uniqueName)) {
      uniqueName = `${panelName}__${++dup}`
    }
    panelName = uniqueName
    usedNames.add(panelName)
    panels.push({
      name: panelName,
      label: getCollapsePanelLabel(props, panels.length),
      fields: extractFields(getRuleChildren(panelRule)),
    })
  }
  return panels
}

export function getLayoutKey(item: Record<string, unknown>, index: number, fallback: string): string {
  return String(item.field || item.name || item.id || `__layout_${fallback}_${index}`)
}

export function getLayoutLabel(item: Record<string, unknown>): string {
  const props = item.props as Record<string, unknown> | undefined
  return String(item.title || props?.header || props?.title || '')
}

function getRuleSpan(item: Record<string, unknown>): number {
  const col = item.col as Record<string, unknown> | undefined
  return typeof col?.span === 'number' ? col.span : 24
}

/**
 * Coerce arbitrary `props.portalViews` into a typed object with safe defaults.
 * Missing or malformed input falls back to DEFAULT_PORTAL_VIEWS (tableOnly + mirrorTodo)
 * so legacy forms preserve current behavior.
 */
export function normalizePortalViews(input: Partial<SubTablePortalViews> | undefined | null): SubTablePortalViews {
  if (!input || typeof input !== 'object') {
    return { ...DEFAULT_PORTAL_VIEWS, assigneeTodoFormSource: { ...DEFAULT_PORTAL_VIEWS.assigneeTodoFormSource! } }
  }
  const assigneeTodo: SubTableAssigneeTodoMode =
    input.assigneeTodo === 'formBelowTable' ? 'formBelowTable' : 'tableOnly'
  let initiatorRequest: SubTableInitiatorRequestMode
  if (input.initiatorRequest === 'summaryWithLinkFormModal') {
    initiatorRequest = 'summaryWithLinkFormModal'
  } else if (input.initiatorRequest === 'tableOnly') {
    initiatorRequest = 'tableOnly'
  } else {
    initiatorRequest = 'mirrorTodo'
  }
  const srcType: SubTableFormSourceType =
    input.assigneeTodoFormSource?.type === 'linkForm'
      ? 'linkForm'
      : input.assigneeTodoFormSource?.type === 'formId'
        ? 'formId'
        : 'subForm'
  const formId = input.assigneeTodoFormSource?.formId ?? null
  // Preserve the designer's Link Form column pick so runtime resolution can target
  // a specific column instead of falling back to the first match.
  const linkFormColumnId = input.assigneeTodoFormSource?.linkFormColumnId ?? null
  return {
    assigneeTodo,
    assigneeTodoFormSource: { type: srcType, formId, linkFormColumnId },
    initiatorRequest
  }
}

/**
 * Form-create injects a full default {@code props.portalViews} on every subTable widget (see
 * developer-workstation {@code main.ts} rule). That object must not count as an intentional canvas
 * override — otherwise it always wins merge over {@code configJson.subTablePortalViews[bindingId]}
 * (designers often configure display only on the binding bar, especially inside nested sub-forms).
 *
 * Partial widgets (e.g. only {@code assigneeTodo: 'tableOnly'}) are treated as explicit overrides.
 */
function isImplicitFactorySubTablePortalViews(raw: Partial<SubTablePortalViews> | undefined | null): boolean {
  if (!raw || typeof raw !== 'object') return false
  if (!('assigneeTodo' in raw) || !('initiatorRequest' in raw) || !('assigneeTodoFormSource' in raw)) return false
  if (raw.assigneeTodo !== 'tableOnly') return false
  if (raw.initiatorRequest !== 'mirrorTodo') return false
  const fs = raw.assigneeTodoFormSource
  if (!fs || typeof fs !== 'object') return false
  if (fs.type !== 'subForm') return false
  const fid = fs.formId as unknown
  if (fid != null && fid !== '') return false
  const lid = fs.linkFormColumnId as unknown
  if (lid != null && lid !== '') return false
  return true
}

/**
 * Merge canvas `rule.props.portalViews` with `configJson.subTablePortalViews[bindingId]` the same way
 * developer-workstation {@code FormDesigner.mergePortalViewsForPreview} does — so Portal runtime matches
 * what designers see after editing only the sub-table binding bar (画布节点仍留着默认 tableOnly 时不再覆盖绑定上的 form below)。
 */
export function mergeSubTablePortalViewsForRuntime(
  widgetPv: Partial<SubTablePortalViews> | undefined,
  bindingPvRaw: Partial<SubTablePortalViews> | Record<string, unknown> | null | undefined
): SubTablePortalViews {
  const base = normalizePortalViews(bindingPvRaw as Partial<SubTablePortalViews> | undefined)
  if (!widgetPv || typeof widgetPv !== 'object') {
    return base
  }
  if (isImplicitFactorySubTablePortalViews(widgetPv)) {
    return base
  }

  const ov = widgetPv
  const assigneeTodo: SubTableAssigneeTodoMode =
    ov.assigneeTodo === 'formBelowTable'
      ? 'formBelowTable'
      : ov.assigneeTodo === 'tableOnly'
        ? 'tableOnly'
        : base.assigneeTodo

  let initiatorRequest = base.initiatorRequest
  if (ov.initiatorRequest === 'summaryWithLinkFormModal') {
    initiatorRequest = 'summaryWithLinkFormModal'
  } else if (ov.initiatorRequest === 'tableOnly') {
    initiatorRequest = 'tableOnly'
  } else if (ov.initiatorRequest === 'mirrorTodo') {
    initiatorRequest = 'mirrorTodo'
  }

  const bSrc = base.assigneeTodoFormSource
  const oSrc = ov.assigneeTodoFormSource && typeof ov.assigneeTodoFormSource === 'object' ? ov.assigneeTodoFormSource : null
  const mergedType: SubTableFormSourceType =
    oSrc?.type === 'linkForm'
      ? 'linkForm'
      : oSrc?.type === 'formId'
        ? 'formId'
        : bSrc.type

  return normalizePortalViews({
    assigneeTodo,
    initiatorRequest,
    assigneeTodoFormSource: {
      type: mergedType,
      formId: (oSrc?.formId ?? bSrc.formId) ?? null,
      linkFormColumnId: (oSrc?.linkFormColumnId ?? bSrc.linkFormColumnId) ?? null
    }
  })
}

/**
 * Parse a JSON form config string and return the tabs array with their fields.
 * Handles subTable placeholder entries inside tab panes.
 *
 * @param configStr - JSON string of the form config (e.g. { rule: [...] })
 * @returns Array of FormTab objects, or empty array if no tabs found
 */
/**
 * Split top-level form-create rules into segments before / inside / after `el-tabs`,
 * preserving designer canvas order (Preview renders siblings outside tab panes too).
 */
export function splitRulesAroundTabs(rules: Record<string, unknown>[]): {
  beforeTabs: Record<string, unknown>[]
  tabsRule: Record<string, unknown> | null
  afterTabs: Record<string, unknown>[]
} {
  const tabsIndex = rules.findIndex(rule => isTabsRule(rule))
  if (tabsIndex < 0) {
    return { beforeTabs: rules, tabsRule: null, afterTabs: [] }
  }
  return {
    beforeTabs: rules.slice(0, tabsIndex),
    tabsRule: rules[tabsIndex] ?? null,
    afterTabs: rules.slice(tabsIndex + 1),
  }
}

/** Extract {@link FormTab} entries from an `el-tabs` rule node. */
export function extractTabsFromTabsRule(
  tabsRule: Record<string, unknown> | null | undefined,
  extractFields: (items: Record<string, unknown>[]) => FormField[],
): FormTab[] {
  if (!tabsRule || !Array.isArray(tabsRule.children)) return []
  const tabs: FormTab[] = []
  const usedNames = new Set<string>()
  for (const tabPane of tabsRule.children as Record<string, unknown>[]) {
    if (!isTabPaneRule(tabPane)) continue
    const props = tabPane.props as Record<string, unknown> | undefined
    if (!props) continue
    const rawName = props.name
    let tabName =
      rawName === undefined || rawName === null || rawName === ''
        ? `tab_${tabs.length}`
        : String(rawName)
    let uniqueName = tabName
    let dup = 0
    while (usedNames.has(uniqueName)) {
      uniqueName = `${tabName}__${++dup}`
    }
    tabName = uniqueName
    usedNames.add(tabName)
    const tabLabel =
      props.label != null && String(props.label).trim() !== ''
        ? String(props.label)
        : `Tab ${tabs.length + 1}`
    tabs.push({
      name: tabName,
      label: tabLabel,
      fields: extractFields(getRuleChildren(tabPane)),
    })
  }
  return tabs
}

export function parseFormRulesLayout(
  rules: Record<string, unknown>[],
  extractFields: (items: Record<string, unknown>[]) => FormField[],
): { fields: FormField[]; tabs: FormTab[]; fieldsAfterTabs: FormField[] } {
  const { beforeTabs, tabsRule, afterTabs } = splitRulesAroundTabs(rules)
  const tabs = extractTabsFromTabsRule(tabsRule, extractFields)
  if (tabs.length === 0) {
    return { fields: extractFields(rules), tabs: [], fieldsAfterTabs: [] }
  }
  return {
    fields: extractFields(beforeTabs),
    tabs,
    fieldsAfterTabs: extractFields(afterTabs),
  }
}

export function parseFormConfigToTabs(configStr: string): FormTab[] {
  try {
    const config = typeof configStr === 'string' ? JSON.parse(configStr) : configStr
    let rules: Record<string, unknown>[] | null = null
    if (config.rule && Array.isArray(config.rule)) {
      rules = config.rule
    } else if (Array.isArray(config)) {
      rules = config
    }
    if (!rules) return []

    const tabsRule = findTabsRule(rules)
    return extractTabsFromTabsRule(tabsRule, (items) => extractFieldsRecursive(items))
  } catch {
    return []
  }
}

/** Minimal binding shape for link-form target / placement helpers. */
export interface SubTableBindingLinkRef {
  bindingId: number
  columns?: Array<{ type?: string; props?: Record<string, unknown> }>
  subMode?: string
}

/**
 * Designer configJson often keys subForms/subListViews with legacy short ids (66, 103) while
 * dw_form_table_bindings.id uses 50066 / 50103 — resolve both when looking up schema or bindings.
 */
export function legacyBindingIdAliases(bindingId: number | string): number[] {
  const id = Number(bindingId)
  if (!Number.isFinite(id)) return []
  const out: number[] = [id]
  const push = (n: number) => {
    if (Number.isFinite(n) && n > 0 && !out.includes(n)) out.push(n)
  }
  if (id >= 50000) push(id - 50000)
  const mod = id % 1000
  if (mod > 0) push(mod)
  return out
}

/** Collect `_bindingId` values from placed `subTable` nodes in parsed form field trees. */
export function collectPlacedSubTableBindingIds(
  fields: FormField[],
  tabs?: FormTab[],
  fieldsAfterTabs?: FormField[],
): Set<number> {
  const ids = new Set<number>()
  const walk = (arr?: FormField[]) => {
    if (!Array.isArray(arr)) return
    for (const f of arr) {
      if (f.type === 'subTable' && f._bindingId != null) {
        ids.add(Number(f._bindingId))
      }
      if (f.type === 'tabs' && Array.isArray(f.tabs)) {
        for (const tab of f.tabs) walk(tab.fields)
      }
      if (f.type === 'collapse' && Array.isArray(f.collapsePanels)) {
        for (const panel of f.collapsePanels) walk(panel.fields)
      }
      if ((f.type === 'card' || f.type === 'row' || f.type === 'col') && Array.isArray(f.children)) {
        walk(f.children)
      }
    }
  }
  walk(fields)
  for (const tab of tabs || []) walk(tab.fields)
  walk(fieldsAfterTabs)
  return ids
}

/** Collect every placed `subTable` FormField node from a parsed layout tree. */
export function collectSubTableFieldsFromLayout(
  fields: FormField[],
  tabs?: FormTab[],
  fieldsAfterTabs?: FormField[],
): FormField[] {
  const out: FormField[] = []
  const walk = (arr?: FormField[]) => {
    if (!Array.isArray(arr)) return
    for (const f of arr) {
      if (f.type === 'subTable' && f._bindingId != null) {
        out.push(f)
      }
      if (f.type === 'tabs' && Array.isArray(f.tabs)) {
        for (const tab of f.tabs) walk(tab.fields)
      }
      if (f.type === 'collapse' && Array.isArray(f.collapsePanels)) {
        for (const panel of f.collapsePanels) walk(panel.fields)
      }
      if ((f.type === 'card' || f.type === 'row' || f.type === 'col') && Array.isArray(f.children)) {
        walk(f.children)
      }
    }
  }
  walk(fields)
  for (const tab of tabs || []) walk(tab.fields)
  walk(fieldsAfterTabs)
  return out
}

export interface FormLayoutBuckets {
  fields: FormField[]
  tabs: FormTab[]
  fieldsAfterTabs: FormField[]
}

/**
 * Re-attach FU-canvas `subTable` widgets dropped when task-form configJson overwrites layout.
 */
export function mergeMissingSubTableFieldsIntoLayout(
  layout: FormLayoutBuckets,
  sourceSubTables: FormField[],
  bindingIds?: Set<number> | Iterable<number> | null,
): void {
  const allowedIds = bindingIds != null ? asNumberSet(bindingIds) : null
  const placed = collectPlacedSubTableBindingIds(layout.fields, layout.tabs, layout.fieldsAfterTabs)
  for (const st of sourceSubTables) {
    if (st._bindingId == null) continue
    const bid = Number(st._bindingId)
    if (!Number.isFinite(bid)) continue
    if (allowedIds && !allowedIds.has(bid)) continue
    if (placed.has(bid)) continue
    layout.fieldsAfterTabs.push({ ...st })
    placed.add(bid)
  }
}

/**
 * Last-resort: ensure every live sub-table binding has a canvas `subTable` widget so FormRenderer mounts it.
 */
export function ensureSubTableBindingsOnFormLayout(
  layout: FormLayoutBuckets,
  bindings: Array<{ bindingId: number; portalViews?: Partial<SubTablePortalViews> | null }>,
  formConfig?: Record<string, unknown> | null,
): void {
  const placed = collectPlacedSubTableBindingIds(layout.fields, layout.tabs, layout.fieldsAfterTabs)
  const portalViewsMap = (formConfig?.subTablePortalViews ?? {}) as Record<string, unknown>
  for (const b of bindings) {
    const bid = Number(b.bindingId)
    if (!Number.isFinite(bid) || placed.has(bid)) continue
    const pvRaw = b.portalViews ?? portalViewsMap[bid] ?? portalViewsMap[String(bid)]
    layout.fieldsAfterTabs.push({
      key: `__subTable_${bid}`,
      label: '',
      type: 'subTable',
      _bindingId: bid,
      ...(pvRaw && typeof pvRaw === 'object'
        ? { portalViews: normalizePortalViews(pvRaw as Partial<SubTablePortalViews>) }
        : {}),
      span: 24,
    })
    placed.add(bid)
  }
}

/** Remove placed `subTable` widgets (e.g. before rendering the same bindings via a dedicated section). */
export function removeSubTableFieldsByBindingIds(
  layout: FormLayoutBuckets,
  bindingIds: Set<number> | Iterable<number>,
): void {
  const stripIds = asNumberSet(bindingIds)
  if (stripIds.size === 0) return
  const strip = (fields: FormField[]): FormField[] => {
    const out: FormField[] = []
    for (const f of fields) {
      if (f.type === 'subTable' && f._bindingId != null && stripIds.has(Number(f._bindingId))) {
        continue
      }
      const next: FormField = { ...f }
      if (next.type === 'tabs' && Array.isArray(next.tabs)) {
        next.tabs = next.tabs.map(tab => ({ ...tab, fields: strip(tab.fields) }))
      }
      if (next.type === 'collapse' && Array.isArray(next.collapsePanels)) {
        next.collapsePanels = next.collapsePanels.map(panel => ({
          ...panel,
          fields: strip(panel.fields),
        }))
      }
      if ((next.type === 'card' || next.type === 'row' || next.type === 'col') && Array.isArray(next.children)) {
        next.children = strip(next.children)
      }
      out.push(next)
    }
    return out
  }
  layout.fields = strip(layout.fields)
  layout.tabs = layout.tabs.map(tab => ({ ...tab, fields: strip(tab.fields) }))
  layout.fieldsAfterTabs = strip(layout.fieldsAfterTabs)
}

/**
 * Walk form-create `rule` and collect every `subTable` node's `_bindingId`
 * (canvas placement — designer explicitly dragged the widget).
 */
export function collectRuleBindingIds(rules: unknown[]): Set<number> {
  const ids = new Set<number>()
  const walk = (items: unknown[]) => {
    if (!Array.isArray(items)) return
    for (const r of items) {
      if (!r || typeof r !== 'object') continue
      const item = r as Record<string, unknown>
      if (item.type === 'subTable') {
        const props = item.props as Record<string, unknown> | undefined
        const id = item._bindingId ?? props?._bindingId
        if (id != null) ids.add(Number(id))
      }
      if (Array.isArray(item.children)) walk(item.children)
    }
  }
  walk(rules)
  return ids
}

function normalizeSubTableBindingName(name?: string): string {
  return String(name || '').trim().toLowerCase().replace(/\s+/g, '')
}

/** Collect binding ids referenced by `linkForm` columns (Link Form modal / inline targets). */
export function collectLinkFormTargetBindingIds(
  bindings: SubTableBindingLinkRef[]
): Set<number> {
  const targets = new Set<number>()
  const nameToId = new Map<string, number>()
  for (const b of bindings) {
    const binding = b as SubTableBindingLinkRef & { tableName?: string; physicalTableName?: string }
    if (binding.tableName) {
      nameToId.set(normalizeSubTableBindingName(binding.tableName), b.bindingId)
    }
    if (binding.physicalTableName) {
      nameToId.set(normalizeSubTableBindingName(binding.physicalTableName), b.bindingId)
    }
  }
  for (const b of bindings) {
    for (const col of b.columns || []) {
      const colAny = col as {
        type?: string
        field?: string
        props?: Record<string, unknown>
        boundSubTableBindingId?: unknown
        boundSubTableName?: string
      }
      const isLinkCol =
        col?.type === 'linkForm'
        || (typeof colAny.field === 'string' && colAny.field.startsWith('linkForm:'))
      if (!isLinkCol) continue
      const raw = colAny.props?.boundSubTableBindingId ?? colAny.boundSubTableBindingId
      if (raw != null && raw !== '') {
        const n = Number(raw)
        if (Number.isFinite(n)) targets.add(n)
      }
      const name = colAny.props?.boundSubTableName ?? colAny.boundSubTableName
      if (name != null && String(name).trim()) {
        const id = nameToId.get(normalizeSubTableBindingName(String(name)))
        if (id != null) targets.add(id)
      }
    }
  }
  return targets
}

/** Designer stores Link Form columns in {@code configJson.subListViews} — API columns may omit them. */
export function collectLinkFormTargetBindingIdsFromSubListViews(
  formConfig: Record<string, unknown> | null | undefined
): Set<number> {
  const targets = new Set<number>()
  const stv = formConfig?.subListViews as Record<string, { columns?: unknown[] }> | undefined
  if (!stv || typeof stv !== 'object') return targets
  for (const entry of Object.values(stv)) {
    if (!entry || typeof entry !== 'object') continue
    const cols = (entry as { columns?: unknown[] }).columns
    if (!Array.isArray(cols)) continue
    for (const col of cols) {
      if (!col || typeof col !== 'object') continue
      const c = col as Record<string, unknown>
      const isLink =
        c.columnType === 'linkForm'
        || c.type === 'linkForm'
        || (typeof c.fieldName === 'string' && String(c.fieldName).startsWith('linkForm:'))
      if (!isLink) continue
      const raw =
        c.boundSubTableBindingId
        ?? (c.props as Record<string, unknown> | undefined)?.boundSubTableBindingId
      if (raw != null && raw !== '') {
        const n = Number(raw)
        if (Number.isFinite(n)) targets.add(n)
      }
    }
  }
  return targets
}

/** Union of runtime binding columns + designer {@code subListViews} link-form metadata. */
export function collectAllLinkFormTargetBindingIds(
  bindings: SubTableBindingLinkRef[],
  formConfig?: Record<string, unknown> | null
): Set<number> {
  const merged = collectLinkFormTargetBindingIds(bindings)
  for (const id of collectLinkFormTargetBindingIdsFromSubListViews(formConfig)) {
    merged.add(id)
  }
  return merged
}

/**
 * Same closure as developer-workstation FormDesigner preview / process start:
 * placed sub-tables plus link-form targets reachable from them.
 */
export function computeNeededSubTableBindingIds(
  placed: Set<number> | Iterable<number>,
  allBindings: SubTableBindingLinkRef[]
): Set<number> {
  const needed = asNumberSet(placed)
  let changed = true
  while (changed) {
    changed = false
    for (const b of allBindings) {
      if (!needed.has(b.bindingId)) continue
      for (const col of b.columns || []) {
        if (col?.type !== 'linkForm') continue
        const raw = col.props?.boundSubTableBindingId
        if (raw == null || raw === '') continue
        const n = Number(raw)
        if (Number.isFinite(n) && !needed.has(n)) {
          needed.add(n)
          changed = true
        }
      }
    }
  }
  return needed
}

/**
 * My Request / initiatorRequest: suppress duplicate standalone tables for bindings that
 * exist only for Link Form modals — even when a stale canvas {@code subTable} node remains in rule JSON.
 */
export function shouldSuppressStandaloneSubTableInInitiatorRequest(
  bindingId: number,
  bindings: SubTableBindingLinkRef[],
  portalViews?: Partial<SubTablePortalViews> | null,
  nativeBindingIds?: ReadonlySet<number> | Iterable<number> | null,
  formConfig?: Record<string, unknown> | null
): boolean {
  const id = Number(bindingId)
  const binding = bindings.find(b => Number(b.bindingId) === id)
  if (String(binding?.subMode || '').toUpperCase() === 'FORM_ONLY') return true
  const nativeIds = nativeBindingIds != null ? asNumberSet(nativeBindingIds) : null
  if (nativeIds && nativeIds.size > 0 && !nativeIds.has(id)) return true
  // Canvas-placed sub-tables (native tableBindings) must render even when the binding is also
  // referenced as a Link Form target (e.g. self-ref linkForm column in subListViews).
  if (nativeIds && nativeIds.size > 0 && nativeIds.has(id)) return false
  if (!collectAllLinkFormTargetBindingIds(bindings, formConfig).has(id)) return false
  const pv = normalizePortalViews(
    portalViews ?? (binding?.portalViews as Partial<SubTablePortalViews> | undefined)
  )
  return pv.initiatorRequest !== 'tableOnly'
}

/** @deprecated Prefer {@link shouldSuppressStandaloneSubTableInInitiatorRequest}. */
export function isLinkOnlyStandaloneSubTableBinding(
  bindingId: number,
  formRule: unknown[],
  bindings: SubTableBindingLinkRef[]
): boolean {
  const binding = bindings.find(b => Number(b.bindingId) === Number(bindingId))
  if (shouldSuppressStandaloneSubTableInInitiatorRequest(bindingId, bindings, binding?.portalViews)) {
    const placed = collectRuleBindingIds(formRule)
    return !placed.has(Number(bindingId))
  }
  return false
}

/**
 * Remove {@code subTable} FormFields that must not render as standalone tables on My Request.
 * Recurses into card children.
 */
export function filterLinkOnlyStandaloneSubTableFields(
  fields: FormField[],
  bindings: SubTableBindingLinkRef[],
  _formRule: unknown[],
  nativeBindingIds?: ReadonlySet<number> | null,
  formConfig?: Record<string, unknown> | null
): FormField[] {
  return fields
    .map(field => {
      if (field.type === 'tabs' && Array.isArray(field.tabs)) {
        return {
          ...field,
          tabs: field.tabs.map(tab => ({
            ...tab,
            fields: filterLinkOnlyStandaloneSubTableFields(
              tab.fields,
              bindings,
              _formRule,
              nativeBindingIds,
              formConfig
            ),
          })),
        }
      }
      if (field.type === 'collapse' && Array.isArray(field.collapsePanels)) {
        return {
          ...field,
          collapsePanels: field.collapsePanels.map(panel => ({
            ...panel,
            fields: filterLinkOnlyStandaloneSubTableFields(
              panel.fields,
              bindings,
              _formRule,
              nativeBindingIds,
              formConfig
            ),
          })),
        }
      }
      if ((field.type === 'card' || field.type === 'row' || field.type === 'col') && Array.isArray(field.children)) {
        return {
          ...field,
          children: filterLinkOnlyStandaloneSubTableFields(
            field.children,
            bindings,
            _formRule,
            nativeBindingIds,
            formConfig
          )
        }
      }
      return field
    })
    .filter(field => {
      if (field.type !== 'subTable' || field._bindingId == null) return true
      const binding = bindings.find(b => Number(b.bindingId) === Number(field._bindingId))
      const merged = mergeSubTablePortalViewsForRuntime(
        field.portalViews,
        binding?.portalViews as Partial<SubTablePortalViews> | undefined
      )
      return !shouldSuppressStandaloneSubTableInInitiatorRequest(
        field._bindingId,
        bindings,
        merged,
        nativeBindingIds,
        formConfig
      )
    })
}

// ---------------------------------------------------------------------------
// Business Logic Config types — configJson 完整结构与子类型
// 所有新增字段均为可选（optional），确保旧版 configJson 向后兼容
// ---------------------------------------------------------------------------

/** configJson 完整结构 */
export interface FormBusinessLogicConfig {
  rule: any[]  // FormCreateRule (form-create rule objects)
  options: Record<string, any>
  subForms: Record<string, SubFormConfig>
  // 业务逻辑扩展（所有字段可选，向后兼容旧版 configJson）
  formulas?: FormulaRule[]
  linkages?: LinkageRule[]
  crossFieldRules?: CrossFieldRule[]
  summaryRules?: SummaryRule[]
  subTableValidation?: Record<string, SubTableValidationConfig>
}

export interface FormulaRule {
  targetField: string
  expression: string       // mathjs 安全表达式
  dependsOn: string[]
}

export interface LinkageRule {
  sourceField: string
  targetField: string
  linkageType: 'option-filtering' | 'value-auto-fill' | 'field-state-change'
  filterConfig?: {
    filterField: string
    filterOperator: 'equals' | 'contains' | 'in'
    filterSource: '$source'
  }
  valueMapping?: Record<string, any>
  stateConfig?: {
    condition: ConditionExpression
    disabled?: boolean
    required?: boolean
  }
}

export interface ConditionExpression {
  field: string
  operator: 'equals' | 'not-equals' | 'contains' | 'greater-than' | 'less-than' | 'is-empty' | 'is-not-empty'
  value?: any
  logic?: 'AND' | 'OR'
  children?: ConditionExpression[]
}

export interface CrossFieldRule {
  fields: string[]
  operator: 'greater-than' | 'less-than' | 'equals' | 'not-equals' | 'date-after' | 'date-before'
  message: string
  targetField: string
}

export interface SummaryRule {
  sourceBindingId: number
  sourceColumn: string
  targetField: string
  aggregation: 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'
}

export interface SubTableValidationConfig {
  minRows?: number
  maxRows?: number
  columnRules?: Record<string, ValidationRule[]>
}

export interface SubFormConfig {
  rule: any[]  // FormCreateRule
  options?: Record<string, any>
  rowFormulas?: RowFormulaRule[]
}

export interface RowFormulaRule {
  targetColumn: string
  expression: string       // mathjs 安全表达式
  dependsOn: string[]
}

export interface ValidationRule {
  type: 'required' | 'pattern' | 'number' | 'email' | 'phone' | 'custom'
  pattern?: string
  min?: number
  max?: number
  minLength?: number
  maxLength?: number
  message: string
}
