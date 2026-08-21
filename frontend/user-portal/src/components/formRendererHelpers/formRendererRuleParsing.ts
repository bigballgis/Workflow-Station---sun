/**
 * form-create rule → {@link FormField} layout parsing: container/auxiliary
 * predicates, recursive field extraction, tabs/collapse/row-column splitting,
 * and the top-level configJson parsers.
 */

import type { FormField, FormCollapsePanel, FormTab } from './formRendererTypes'
import { applyDesignerHideFlagToFormField } from './formRendererFieldUtils'

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
    const props = item.props as Record<string, unknown> | undefined
    const bindingId = item._bindingId ?? props?._bindingId
    if (item.type === 'miAssignment') {
      const marker: FormField = {
        key: getLayoutKey(item, index, 'miAssignment'),
        label: '',
        type: 'miAssignment',
        span: 24,
      }
      applyDesignerHideFlagToFormField(marker, item)
      // The container owns the assignee / BU / role rules — keep them NESTED here.
      // The dialog's layout pass reads marker.children to decide the block's
      // membership and to drop them together when the designer's Hide toggle is on;
      // hoisting them alongside the marker left `hidden` on the marker alone and
      // leaked an undesigned Assignee row into the dialog. Legacy forms have no
      // children here and are unaffected.
      const children = item.children
      if (Array.isArray(children) && children.length > 0) {
        marker.children = extractFieldsRecursive(children as Record<string, unknown>[], converter)
      }
      fields.push(marker)
      continue
    }
    if (item.type === 'recordNote') {
      const scope = props?.scope === 'TABLE' ? 'TABLE' : 'RECORD'
      const recordNoteField: FormField = {
        key: `__recordNote_${scope.toLowerCase()}`,
        label: '',
        type: 'recordNote',
        span: 24,
        _recordNote: {
          scope,
          panelTitle: typeof props?.panelTitle === 'string' ? props.panelTitle : undefined,
          allowAttachment: props?.allowAttachment !== false,
          maxFileSizeMb: Number(props?.maxFileSizeMb) || 10,
          allowEditOwn: props?.allowEditOwn !== false,
          // Delete is opt-in (see RecordNoteField): only an explicit true enables it.
          allowDelete: props?.allowDelete === true,
          pageSize: Number(props?.pageSize) || 5,
        },
      }
      applyDesignerHideFlagToFormField(recordNoteField, item)
      fields.push(recordNoteField)
      continue
    }
    if (item.type === 'subTable' && bindingId != null) {
      const subTableField: FormField = {
        key: `__subTable_${bindingId}`,
        label: '',
        type: 'subTable',
        _bindingId: Number(bindingId),
        // 逐操作权限：仅在显式为 false 时下发（undefined 由 SubTableField 回退到 editable）
        ...(props?.allowAdd === false ? { allowAdd: false } : {}),
        ...(props?.allowEdit === false ? { allowEdit: false } : {}),
        ...(props?.allowDelete === false ? { allowDelete: false } : {}),
        // Summary presentation designed on the canvas.
        ...(props?.compactCells === true ? { compactCells: true } : {}),
        span: 24,
      }
      applyDesignerHideFlagToFormField(subTableField, item)
      fields.push(subTableField)
      continue
    }
    // Inline Form: the bound SUB table's form laid out in place (no grid, no dialog).
    // Needs its own branch — it carries no `field`, so the `if (item.field)` fallthrough
    // below would silently drop it (exactly how the linkForm drag widget died at runtime).
    if (item.type === 'inlineSubForm' && bindingId != null) {
      const inlineSubFormField: FormField = {
        key: `__inlineSubForm_${bindingId}`,
        label: '',
        type: 'inlineSubForm',
        _bindingId: Number(bindingId),
        span: 24,
      }
      applyDesignerHideFlagToFormField(inlineSubFormField, item)
      fields.push(inlineSubFormField)
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
      if (field) {
        applyDesignerHideFlagToFormField(field, item)
        fields.push(field)
      }
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
