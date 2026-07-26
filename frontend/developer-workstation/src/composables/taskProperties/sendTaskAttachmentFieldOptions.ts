import type { FormDefinition, TableBinding, TableDefinition } from '@/api/functionUnit'
import type { RelationTableDTO } from '@/api/relationTable'
import {
  buildLookupCatalogGroups,
  flattenLookupCatalogItems,
} from '@/utils/mainTableViewLookupCatalog'
import { collectUploadRulesFromTree } from '@/utils/formDesigner'
import type { AttachmentFieldOption, EmailAttachmentRef } from './useSendTaskEmailAttachments'
import { attachmentOptionValue } from './useSendTaskEmailAttachments'

function isFileDataType(dataType: unknown): boolean {
  return String(dataType || '').toUpperCase() === 'FILE'
}

function pushUnique(
  options: AttachmentFieldOption[],
  seen: Set<string>,
  ref: EmailAttachmentRef,
  label: string,
  group: string,
): void {
  const value = attachmentOptionValue(ref)
  if (!value || seen.has(value)) return
  seen.add(value)
  options.push({ value, label, group, ref })
}

/**
 * Prefer PROCESS form SUB binding per table — email runtime stores rows under that binding id.
 */
export function pickRepresentativeSubBindings(forms: FormDefinition[]): TableBinding[] {
  const byTable = new Map<number, { binding: TableBinding; isProcess: boolean }>()
  for (const form of forms || []) {
    const isProcess = form.formType === 'PROCESS'
    for (const b of form.tableBindings || []) {
      if (b.bindingType !== 'SUB' || b.id == null) continue
      const existing = byTable.get(b.tableId)
      if (!existing || (!existing.isProcess && isProcess)) {
        byTable.set(b.tableId, { binding: b, isProcess })
      }
    }
  }
  return Array.from(byTable.values()).map(v => v.binding)
}

/**
 * Build Send Email attachment field options:
 * - MAIN table FILE columns / MAIN form Upload widgets
 * - SUB table FILE columns / SUB form Upload widgets (PROCESS-preferred binding)
 * - FILE columns on Relation Tables targeted by form Lookup widgets
 */
export function buildSendTaskAttachmentFieldOptions(
  tables: TableDefinition[],
  forms: FormDefinition[],
  relationTables: RelationTableDTO[],
): AttachmentFieldOption[] {
  const options: AttachmentFieldOption[] = []
  const seen = new Set<string>()
  appendMainOptions(options, seen, tables, forms)
  appendSubOptions(options, seen, tables, forms)
  appendLookupOptions(options, seen, forms, relationTables)
  return options
}

function appendMainOptions(
  options: AttachmentFieldOption[],
  seen: Set<string>,
  tables: TableDefinition[],
  forms: FormDefinition[],
): void {
  const main = tables.find(t => String(t.tableType || '').toUpperCase() === 'MAIN')
  const mainGroup = main
    ? (main.tableDisplayName || main.tableName || 'Main')
    : 'Main'
  const mainFieldNames = new Set(
    (main?.fieldDefinitions || []).map(f => String(f.fieldName || '').trim()).filter(Boolean),
  )
  const mainFieldLabel = new Map(
    (main?.fieldDefinitions || []).map(f => [
      f.fieldName,
      f.displayName || f.fieldName,
    ]),
  )

  for (const f of main?.fieldDefinitions || []) {
    if (!isFileDataType(f.dataType)) continue
    pushUnique(
      options,
      seen,
      { source: 'main', fieldName: f.fieldName },
      `${f.displayName || f.fieldName} (${f.fieldName})`,
      mainGroup,
    )
  }

  for (const form of forms || []) {
    const rules = (form.configJson as { rule?: unknown } | undefined)?.rule
    const uploadRules = collectUploadRulesFromTree(Array.isArray(rules) ? rules : [])
    for (const rule of uploadRules) {
      const fieldName = String(rule.field || '').trim()
      if (!fieldName || !mainFieldNames.has(fieldName)) continue
      const display = mainFieldLabel.get(fieldName) || fieldName
      pushUnique(
        options,
        seen,
        { source: 'main', fieldName },
        `${display} (${fieldName})`,
        mainGroup,
      )
    }
  }
}

function appendSubOptions(
  options: AttachmentFieldOption[],
  seen: Set<string>,
  tables: TableDefinition[],
  forms: FormDefinition[],
): void {
  const tableById = new Map(tables.map(t => [t.id, t]))
  const subBindings = pickRepresentativeSubBindings(forms)

  for (const binding of subBindings) {
    if (binding.id == null) continue
    const tbl = tableById.get(binding.tableId)
    if (!tbl) continue
    const group = `Sub: ${tbl.tableDisplayName || tbl.tableName || binding.tableName || binding.tableId}`
    const fieldNames = new Set(
      (tbl.fieldDefinitions || []).map(f => String(f.fieldName || '').trim()).filter(Boolean),
    )
    const fieldLabel = new Map(
      (tbl.fieldDefinitions || []).map(f => [f.fieldName, f.displayName || f.fieldName]),
    )

    for (const f of tbl.fieldDefinitions || []) {
      if (!isFileDataType(f.dataType)) continue
      pushUnique(
        options,
        seen,
        { source: 'sub', bindingId: binding.id, fieldName: f.fieldName },
        `${f.displayName || f.fieldName} (${f.fieldName})`,
        group,
      )
    }

    for (const form of forms || []) {
      const rules = (form.configJson as { rule?: unknown } | undefined)?.rule
      const uploadRules = collectUploadRulesFromTree(Array.isArray(rules) ? rules : [])
      for (const rule of uploadRules) {
        const fieldName = String(rule.field || '').trim()
        if (!fieldName || !fieldNames.has(fieldName)) continue
        const display = fieldLabel.get(fieldName) || fieldName
        pushUnique(
          options,
          seen,
          { source: 'sub', bindingId: binding.id, fieldName },
          `${display} (${fieldName})`,
          group,
        )
      }
    }
  }
}

function appendLookupOptions(
  options: AttachmentFieldOption[],
  seen: Set<string>,
  forms: FormDefinition[],
  relationTables: RelationTableDTO[],
): void {
  const groups = buildLookupCatalogGroups(forms, relationTables)
  const lookupItems = flattenLookupCatalogItems(groups).filter(i => isFileDataType(i.dataType))
  for (const item of lookupItems) {
    if (!item.lookupSourceField || !item.lookupDisplayField) continue
    const ref: EmailAttachmentRef = {
      source: 'lookup',
      lookupField: item.lookupSourceField,
      targetField: item.lookupDisplayField,
      ...(item.lookupTableId != null ? { tableId: item.lookupTableId } : {}),
    }
    const groupLabel = item.lookupTableName
      ? `${item.lookupSourceField} → ${item.lookupTableName}`
      : item.lookupSourceField
    pushUnique(
      options,
      seen,
      ref,
      item.displayName || `${item.lookupSourceField}.${item.lookupDisplayField}`,
      groupLabel,
    )
  }
}
