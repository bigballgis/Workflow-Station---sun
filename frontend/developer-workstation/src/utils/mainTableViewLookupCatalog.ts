import type { FormDefinition } from '@/api/functionUnit'
import type { RelationTableDTO } from '@/api/relationTable'
import {
  lookupDisplayFieldName,
  type MainTableFieldCatalogItem,
  type MainTableLookupCatalogGroup,
} from '@/api/mainTableView'

interface ParsedLookupConfig {
  tableId: number
  tableName?: string
  searchFields?: string[]
  displayFields?: string[]
  selectedDisplayField?: string
}

function walkRules(node: unknown, visit: (rule: Record<string, unknown>) => void): void {
  if (node == null) return
  if (Array.isArray(node)) {
    for (const child of node) walkRules(child, visit)
    return
  }
  if (typeof node !== 'object') return
  const obj = node as Record<string, unknown>
  visit(obj)
  for (const value of Object.values(obj)) {
    if (value && typeof value === 'object') walkRules(value, visit)
  }
}

function parseLookupConfig(raw: unknown): ParsedLookupConfig | null {
  if (raw == null) return null
  let cfg: Record<string, unknown>
  if (typeof raw === 'string') {
    try {
      cfg = JSON.parse(raw) as Record<string, unknown>
    } catch {
      return null
    }
  } else if (typeof raw === 'object' && !Array.isArray(raw)) {
    cfg = raw as Record<string, unknown>
  } else {
    return null
  }
  const tableId = Number(cfg.tableId)
  if (!Number.isFinite(tableId)) return null
  return {
    tableId,
    tableName: typeof cfg.tableName === 'string' ? cfg.tableName : undefined,
    searchFields: Array.isArray(cfg.searchFields) ? cfg.searchFields.map(String) : undefined,
    displayFields: Array.isArray(cfg.displayFields) ? cfg.displayFields.map(String) : undefined,
    selectedDisplayField:
      typeof cfg.selectedDisplayField === 'string'
        ? cfg.selectedDisplayField
        : typeof cfg.displayField === 'string'
          ? cfg.displayField
          : undefined,
  }
}

/**
 * Scan form designer rules for lookup widgets bound to the view's owning table context.
 * Returns one group per source field (e.g. t → sys_users attributes).
 */
export function buildLookupCatalogGroups(
  forms: FormDefinition[],
  relationTables: RelationTableDTO[],
): MainTableLookupCatalogGroup[] {
  const bySource = new Map<string, {
    sourceField: string
    tableId: number
    tableName: string
    sourceLabel: string
  }>()

  for (const form of forms || []) {
    const cfg = form.configJson || {}
    const rules = (cfg as { rule?: unknown }).rule
    walkRules(rules, (rule) => {
      if (String(rule.type || '').toLowerCase() !== 'lookup') return
      const field = typeof rule.field === 'string' ? rule.field.trim() : ''
      if (!field) return
      const props = (rule.props && typeof rule.props === 'object')
        ? rule.props as Record<string, unknown>
        : {}
      const parsed = parseLookupConfig(props.lookupConfig)
      if (!parsed) return
      if (bySource.has(field)) return
      const rt = relationTables.find(t => t.id === parsed.tableId)
      bySource.set(field, {
        sourceField: field,
        tableId: parsed.tableId,
        tableName: rt?.tableName || parsed.tableName || String(parsed.tableId),
        sourceLabel: typeof rule.title === 'string' && rule.title.trim()
          ? rule.title.trim()
          : field,
      })
    })
  }

  const groups: MainTableLookupCatalogGroup[] = []
  for (const entry of bySource.values()) {
    const rt = relationTables.find(t => t.id === entry.tableId)
    const targetFields = (rt?.fieldDefinitions || []).map(f => ({
      fieldName: f.fieldName,
      displayName: f.displayName || f.fieldName,
      dataType: f.dataType || 'VARCHAR',
      systemField: false,
      columnType: 'lookup_display' as const,
      lookupSourceField: entry.sourceField,
      lookupDisplayField: f.fieldName,
      lookupTableId: entry.tableId,
      lookupTableName: entry.tableName,
    }))
    if (!targetFields.length) continue
    groups.push({
      sourceField: entry.sourceField,
      sourceLabel: entry.sourceLabel,
      tableId: entry.tableId,
      tableName: entry.tableName,
      fields: targetFields,
    })
  }
  return groups
}

/** Flatten lookup groups into catalog items with synthetic field names for the picker. */
export function flattenLookupCatalogItems(
  groups: MainTableLookupCatalogGroup[],
): MainTableFieldCatalogItem[] {
  const out: MainTableFieldCatalogItem[] = []
  for (const g of groups) {
    for (const f of g.fields) {
      const displayField = f.lookupDisplayField || f.fieldName
      out.push({
        ...f,
        fieldName: lookupDisplayFieldName(g.sourceField, displayField),
        displayName: `${g.sourceLabel}.${f.displayName || displayField}`,
        columnType: 'lookup_display',
        lookupSourceField: g.sourceField,
        lookupDisplayField: displayField,
        lookupTableId: g.tableId,
        lookupTableName: g.tableName,
      })
    }
  }
  return out
}
