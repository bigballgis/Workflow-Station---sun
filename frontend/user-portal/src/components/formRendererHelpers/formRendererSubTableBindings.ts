/**
 * Sub-table binding bookkeeping for FormRenderer: legacy id aliasing, collecting
 * placed `subTable` nodes, link-form target resolution, layout merge/strip, and the
 * My Request standalone-suppression filter (MI/FK-PK hot path).
 */

import { writeSubTableRows } from '@/composables/tasks/subTableStore'
import type {
  FormField,
  FormLayoutBuckets,
  FormTab,
  SubTableBindingLinkRef,
} from './formRendererTypes'
import { asNumberSet } from './formRendererFieldUtils'

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

/**
 * Field/rule types that occupy a SUB binding on a form layout: the `subTable` grid and the
 * `inlineSubForm` in-place form. Both must count as "placed", or the binding is filtered out
 * of `subTableBindings` upstream and `resolveBinding()` returns undefined — the widget then
 * renders nothing, with no error. See docs/design/inline-sub-form-component.md (constraint 2).
 */
const SUB_TABLE_BINDING_NODE_TYPES = new Set(['subTable', 'inlineSubForm'])

function isSubTableBindingNode(type: unknown): boolean {
  return SUB_TABLE_BINDING_NODE_TYPES.has(String(type))
}

/** Collect `_bindingId` values from placed sub-table-backed nodes in parsed form field trees. */
export function collectPlacedSubTableBindingIds(
  fields: FormField[],
  tabs?: FormTab[],
  fieldsAfterTabs?: FormField[],
): Set<number> {
  const ids = new Set<number>()
  const walk = (arr?: FormField[]) => {
    if (!Array.isArray(arr)) return
    for (const f of arr) {
      if (isSubTableBindingNode(f.type) && f._bindingId != null) {
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
      if (isSubTableBindingNode(f.type) && f._bindingId != null) {
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

/**
 * Write a nested sub-table's rows into a parent row's `__subTables__` map, keyed by both
 * binding id and table name — the same convention Link Form persistence (`saveLinkedFormData`)
 * uses, so `pullNestedRowsForBindingFromParentRows` resolves the slice on reload. `sources`
 * are merged in order (later wins) so the freshest local model overrides the stale parent row.
 */
/**
 * @param binding 必须带上 `physicalTableName`（以及关联表的 `relationTableName`/`relationTableId`）。
 *   {@link subTableStoreKey} 的取值顺序是 `physicalTableName ?? tableName`，而 `tableName` 在很多
 *   binding 上是**展示名**：漏传 physical 时 `ATM Correspondence` 会算出 `dw:atm correspondence`，
 *   与读取端用的 `dw:atm_correspondence` 分叉成两个 key —— 同一张表出现两份切片，
 *   编辑/删除写进没人读的那一份（2026-09-03 实测：库里两个 key 各存 4 行和 6 行）。
 */
export function mergeNestedSubTableRowsIntoSto(
  sources: Array<Record<string, unknown> | null | undefined>,
  binding: {
    bindingId: number | string
    tableName?: string
    physicalTableName?: string
    relationTableName?: string | null
    relationTableId?: number | null
  },
  rows: unknown[],
): Record<string, unknown> {
  const sto: Record<string, unknown> = {}
  for (const src of sources) {
    const raw = src?.__subTables__
    if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
      Object.assign(sto, raw as Record<string, unknown>)
    }
  }
  // 规范 key：一张表一个 key，避免再产生 bindingId / 表名两份副本。
  writeSubTableRows(sto, binding, rows)
  return sto
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
 * Bindings placed *inside another binding's form design* (nested sub-table, rendered by
 * SubTableInlineForm → PortalFormFields) count as placed — no standalone duplicate.
 */
export function ensureSubTableBindingsOnFormLayout(
  layout: FormLayoutBuckets,
  bindings: Array<{ bindingId: number; formFields?: FormField[] }>,
  _formConfig?: Record<string, unknown> | null,
): void {
  const placed = collectPlacedSubTableBindingIds(layout.fields, layout.tabs, layout.fieldsAfterTabs)
  for (const b of bindings) {
    if (Array.isArray(b.formFields) && b.formFields.length > 0) {
      for (const id of collectPlacedSubTableBindingIds(b.formFields)) placed.add(id)
    }
  }
  for (const b of bindings) {
    const bid = Number(b.bindingId)
    if (!Number.isFinite(bid) || placed.has(bid)) continue
    layout.fieldsAfterTabs.push({
      key: `__subTable_${bid}`,
      label: '',
      type: 'subTable',
      _bindingId: bid,
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
      if (isSubTableBindingNode(f.type) && f._bindingId != null && stripIds.has(Number(f._bindingId))) {
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
      if (isSubTableBindingNode(item.type)) {
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
 * placed sub-tables plus link-form targets reachable from them, plus nested
 * `subTable` widgets placed inside a needed binding's own form design
 * (sub-table-in-sub-table — rendered by SubTableInlineForm → PortalFormFields).
 */
export function computeNeededSubTableBindingIds(
  placed: Set<number> | Iterable<number>,
  allBindings: Array<SubTableBindingLinkRef & { formFields?: FormField[] }>
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
      if (Array.isArray(b.formFields) && b.formFields.length > 0) {
        for (const id of collectPlacedSubTableBindingIds(b.formFields)) {
          if (!needed.has(id)) {
            needed.add(id)
            changed = true
          }
        }
      }
    }
  }
  return needed
}

/**
 * Suppress a duplicate standalone table for a binding that exists only to back a
 * Link Form modal — including when a stale canvas {@code subTable} node for it is
 * still present in the rule JSON.
 */
export function shouldSuppressLinkOnlyStandaloneSubTable(
  bindingId: number,
  bindings: SubTableBindingLinkRef[],
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
  return collectAllLinkFormTargetBindingIds(bindings, formConfig).has(id)
}

/** @deprecated Prefer {@link shouldSuppressLinkOnlyStandaloneSubTable}. */
export function isLinkOnlyStandaloneSubTableBinding(
  bindingId: number,
  formRule: unknown[],
  bindings: SubTableBindingLinkRef[]
): boolean {
  if (shouldSuppressLinkOnlyStandaloneSubTable(bindingId, bindings)) {
    const placed = collectRuleBindingIds(formRule)
    return !placed.has(Number(bindingId))
  }
  return false
}

/**
 * Remove {@code subTable} FormFields that must not render as standalone tables on My Request.
 * Recurses into card children.
 *
 * {@code keepBindingIds}: widgets placed on the form currently being filtered (a Link Form
 * target's own canvas). Those nested tables must stay even when FORM_ONLY / link-only
 * would hide them on the parent page.
 */
export function filterLinkOnlyStandaloneSubTableFields(
  fields: FormField[],
  bindings: SubTableBindingLinkRef[],
  _formRule: unknown[],
  nativeBindingIds?: ReadonlySet<number> | null,
  formConfig?: Record<string, unknown> | null,
  keepBindingIds?: ReadonlySet<number> | null,
): FormField[] {
  const recurse = (next: FormField[]) => filterLinkOnlyStandaloneSubTableFields(
    next, bindings, _formRule, nativeBindingIds, formConfig, keepBindingIds,
  )
  return fields
    .map(field => {
      if (field.type === 'tabs' && Array.isArray(field.tabs)) {
        return {
          ...field,
          tabs: field.tabs.map(tab => ({
            ...tab,
            fields: recurse(tab.fields),
          })),
        }
      }
      if (field.type === 'collapse' && Array.isArray(field.collapsePanels)) {
        return {
          ...field,
          collapsePanels: field.collapsePanels.map(panel => ({
            ...panel,
            fields: recurse(panel.fields),
          })),
        }
      }
      if ((field.type === 'card' || field.type === 'row' || field.type === 'col') && Array.isArray(field.children)) {
        return {
          ...field,
          children: recurse(field.children),
        }
      }
      return field
    })
    .filter(field => {
      if (field.type !== 'subTable' || field._bindingId == null) return true
      if (keepBindingIds && keepBindingIds.has(Number(field._bindingId))) return true
      return !shouldSuppressLinkOnlyStandaloneSubTable(
        field._bindingId,
        bindings,
        nativeBindingIds,
        formConfig
      )
    })
}
