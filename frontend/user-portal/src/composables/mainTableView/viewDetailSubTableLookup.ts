import type { DialogColumn } from '@/components/subTableAddDialogHelpers/types'
import { enrichLookupColumnPropsFromSubFormRule } from '@/components/subTableAddDialogHelpers/lookup'
import { flattenSubFormRuleLayoutContainers } from '@/components/subTableAddDialogHelpers/subFormCanvasColumns'
import {
  subTableStoreKey,
  type SubTableStoreBindingLike,
} from '@/composables/tasks/subTableStore'

export type ViewDetailPeerForm = {
  formType?: string
  tableBindings?: Array<Record<string, unknown>>
  config?: unknown
}

function parseFormConfig(raw: unknown): Record<string, unknown> {
  if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
    return raw as Record<string, unknown>
  }
  if (typeof raw !== 'string' || raw.trim() === '') return {}
  try {
    const parsed: unknown = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>
    }
    return {}
  } catch {
    // FALLBACK(ux): a malformed peer form must not blank Views detail sub-tables.
    return {}
  }
}

function storeBindingFrom(binding: Record<string, unknown>): SubTableStoreBindingLike {
  return {
    designerTableName: typeof binding.tableName === 'string' ? binding.tableName : undefined,
    relationTableId: binding.relationTableId != null ? Number(binding.relationTableId) : undefined,
    relationTableName: typeof binding.relationTableName === 'string'
      ? binding.relationTableName
      : undefined,
  }
}

function fieldOf(item: unknown): string {
  if (!item || typeof item !== 'object' || Array.isArray(item)) return ''
  return String((item as { field?: unknown }).field ?? '').trim()
}

function ruleItemHasLookup(item: unknown): boolean {
  if (!item || typeof item !== 'object' || Array.isArray(item)) return false
  const r = item as { type?: unknown; props?: { lookupConfig?: unknown } }
  return r.type === 'lookup' || Boolean(r.props?.lookupConfig)
}

function peerPriority(formType?: string): number {
  const t = String(formType || '').toUpperCase()
  if (t === 'PROCESS') return 0
  if (t === 'TASK') return 1
  if (t === 'DETAIL') return 2
  return 3
}

function donorLookupByField(
  currentBinding: Record<string, unknown>,
  peerForms: ViewDetailPeerForm[] | undefined,
): Map<string, unknown> {
  const tableKey = subTableStoreKey(storeBindingFrom(currentBinding))
  const out = new Map<string, unknown>()
  if (!tableKey) return out
  const peers = [...(peerForms ?? [])].sort(
    (a, b) => peerPriority(a.formType) - peerPriority(b.formType),
  )
  for (const form of peers) {
    const subForms = (parseFormConfig(form.config).subForms || {}) as Record<string, { rule?: unknown[] }>
    for (const b of form.tableBindings || []) {
      if (String(b.bindingType || '') === 'PRIMARY') continue
      if (subTableStoreKey(storeBindingFrom(b)) !== tableKey) continue
      const design = subForms[Number(b.bindingId)] ?? subForms[String(b.bindingId)] ?? {}
      const flat = flattenSubFormRuleLayoutContainers(
        Array.isArray(design.rule) ? design.rule : [],
      )
      for (const item of flat) {
        const field = fieldOf(item)
        if (!field || !ruleItemHasLookup(item) || out.has(field)) continue
        out.set(field, item)
      }
    }
  }
  return out
}

/**
 * DETAIL sub-forms often re-bind the same table under a new bindingId and strip
 * lookup controls to `input` with no lookupConfig. Display lives on PROCESS/TASK
 * sub-forms for that table. Overlay by field name; never guess a display column.
 */
export function overlaySubFormLookupRule(
  currentBinding: Record<string, unknown>,
  currentRule: unknown[] | undefined,
  peerForms: ViewDetailPeerForm[] | undefined,
): unknown[] {
  const currentFlat = flattenSubFormRuleLayoutContainers(currentRule)
  const donors = donorLookupByField(currentBinding, peerForms)
  if (donors.size === 0) return currentFlat
  if (currentFlat.length === 0) return [...donors.values()]
  return currentFlat.map(item => {
    const field = fieldOf(item)
    if (!field || ruleItemHasLookup(item)) return item
    return donors.get(field) ?? item
  })
}

export function columnsWithSubFormLookup(
  columns: DialogColumn[],
  currentBinding: Record<string, unknown>,
  currentRule: unknown[] | undefined,
  peerForms: ViewDetailPeerForm[] | undefined,
): DialogColumn[] {
  const merged = overlaySubFormLookupRule(currentBinding, currentRule, peerForms)
  if (merged.length === 0) return columns
  return enrichLookupColumnPropsFromSubFormRule(columns, merged)
}

export function peerFormsFromFuContent(forms: unknown): ViewDetailPeerForm[] {
  if (!Array.isArray(forms)) return []
  return forms.map(raw => {
    const f = raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : {}
    return {
      formType: String(f.formType ?? ''),
      tableBindings: Array.isArray(f.tableBindings)
        ? (f.tableBindings as Array<Record<string, unknown>>)
        : [],
      config: f.data,
    }
  })
}
