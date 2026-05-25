/**
 * Shared helpers for FormRenderer — extracted so they can be imported by both
 * the Vue component (which uses <script setup>) and unit/property tests.
 */

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

/**
 * Recursively extract FormField entries from a form-create rule array.
 * Handles subTable placeholder entries (type === 'subTable') before delegating
 * regular field items to the provided converter.
 *
 * @param items - Array of form-create rule items
 * @param converter - Function that converts a regular rule item to a FormField (or null to skip)
 */
export function extractFieldsRecursive(
  items: Record<string, unknown>[],
  converter: (item: Record<string, unknown>) => FormField | null = () => null
): FormField[] {
  const fields: FormField[] = []
  items.forEach((item, index) => {
    const props = item.props as Record<string, unknown> | undefined
    const bindingId = item._bindingId ?? props?._bindingId
    if (item.type === 'subTable' && bindingId != null) {
      const rawPv = props?.portalViews as Partial<SubTablePortalViews> | undefined
      // Only attach widget-level portalViews when the designer saved something on the
      // canvas node. If we always normalize missing props to DEFAULT_PORTAL_VIEWS,
      // FormRenderer.subTableMode() never falls through to binding.portalViews
      // (configJson.subTablePortalViews[bindingId]) — "form below table" configured only
      // on the sub-table binding tab would never show in To Do.
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
    } else if (isCardRule(item)) {
      fields.push({
        key: getLayoutKey(item, index, 'card'),
        label: getLayoutLabel(item),
        type: 'card',
        span: getRuleSpan(item),
        children: Array.isArray(item.children)
          ? extractFieldsRecursive(item.children as Record<string, unknown>[], converter)
          : []
      })
    } else if (item.field) {
      const field = converter(item)
      if (field) fields.push(field)
    }
    if (!isCardRule(item) && item.children && Array.isArray(item.children)) {
      fields.push(...extractFieldsRecursive(item.children as Record<string, unknown>[], converter))
    }
  })
  return fields
}

function isCardRule(item: Record<string, unknown>): boolean {
  return item.type === 'el-card' || item.type === 'elCard' || item.type === 'card'
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

function getLayoutKey(item: Record<string, unknown>, index: number, fallback: string): string {
  return String(item.field || item.name || item.id || `__layout_${fallback}_${index}`)
}

function getLayoutLabel(item: Record<string, unknown>): string {
  const props = item.props as Record<string, unknown> | undefined
  return String(item.title || props?.header || props?.title || '')
}

function getRuleSpan(item: Record<string, unknown>): number {
  const col = item.col as Record<string, unknown> | undefined
  return typeof col?.span === 'number' ? col.span : 24
}

/**
 * Parse a JSON form config string and return the tabs array with their fields.
 * Handles subTable placeholder entries inside tab panes.
 *
 * @param configStr - JSON string of the form config (e.g. { rule: [...] })
 * @returns Array of FormTab objects, or empty array if no tabs found
 */
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

    const tabsRule = rules.find((r: Record<string, unknown>) => r.type === 'el-tabs')
    if (!tabsRule || !Array.isArray(tabsRule.children)) return []

    const tabs: FormTab[] = []
    for (const tabPane of tabsRule.children) {
      if (tabPane.type === 'el-tab-pane' && tabPane.props) {
        const tabName = tabPane.props.name || `tab_${tabs.length}`
        const tabLabel = tabPane.props.label || `Tab ${tabs.length + 1}`
        const tabFields: FormField[] = []
        if (tabPane.children && Array.isArray(tabPane.children)) {
          tabFields.push(...extractFieldsRecursive(tabPane.children))
        }
        tabs.push({ name: tabName, label: tabLabel, fields: tabFields })
      }
    }
    return tabs
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
  placed: Set<number>,
  allBindings: SubTableBindingLinkRef[]
): Set<number> {
  const needed = new Set<number>(placed)
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
  nativeBindingIds?: ReadonlySet<number> | null,
  formConfig?: Record<string, unknown> | null
): boolean {
  const id = Number(bindingId)
  const binding = bindings.find(b => Number(b.bindingId) === id)
  if (String(binding?.subMode || '').toUpperCase() === 'FORM_ONLY') return true
  if (nativeBindingIds && nativeBindingIds.size > 0 && !nativeBindingIds.has(id)) return true
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
      if (field.type === 'card' && Array.isArray(field.children)) {
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
