import type { TableDefinition } from '@/api/functionUnit'
import {
  lookupDisplayFieldName,
  type MainTableFieldCatalogItem,
  type MainTableLookupCatalogGroup,
} from '@/api/mainTableView'

/**
 * Build Related-columns catalog groups from structural FK fields on the view's owning table.
 * Each FK (e.g. case_id → HMDC Case) becomes a group of the referenced table's fieldDefinitions.
 */
export function buildFkCatalogGroups(
  owningTable: TableDefinition | null | undefined,
  allTables: TableDefinition[],
): MainTableLookupCatalogGroup[] {
  if (!owningTable?.fieldDefinitions?.length) return []

  const groups: MainTableLookupCatalogGroup[] = []
  for (const fd of owningTable.fieldDefinitions) {
    if (!fd.isForeignKey || fd.refTableId == null) continue
    const refTable = allTables.find(t => t.id === fd.refTableId)
    if (!refTable?.fieldDefinitions?.length) continue

    const sourceLabel = fd.displayName || fd.fieldName
    const tableName = refTable.tableDisplayName || refTable.tableName
    const fields: MainTableFieldCatalogItem[] = refTable.fieldDefinitions.map(rf => ({
      fieldName: lookupDisplayFieldName(fd.fieldName, rf.fieldName),
      displayName: rf.displayName || rf.fieldName,
      dataType: rf.dataType || 'VARCHAR',
      systemField: false,
      columnType: 'fk_display' as const,
      lookupSourceField: fd.fieldName,
      lookupDisplayField: rf.fieldName,
      lookupTableId: refTable.id,
      lookupTableName: tableName,
    }))

    groups.push({
      sourceField: fd.fieldName,
      sourceLabel,
      tableId: refTable.id,
      tableName,
      fields,
      relationKind: 'fk',
    })
  }
  return groups
}

/** Flatten FK related groups into picker catalog items (synthetic {@code source@attr} names). */
export function flattenFkCatalogItems(
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
        columnType: 'fk_display',
        lookupSourceField: g.sourceField,
        lookupDisplayField: displayField,
        lookupTableId: g.tableId,
        lookupTableName: g.tableName,
      })
    }
  }
  return out
}
