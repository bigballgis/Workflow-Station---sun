import { ref } from 'vue'
import { functionUnitApi, type TableDefinition, type FormDefinition, type TableBinding } from '@/api/functionUnit'

export interface EmailVariableOption {
  /** Token inserted into the editor, e.g. ${fieldName} or ${subTableField:271:card_number} */
  token: string
  /** Human-readable label shown in the dropdown */
  label: string
}

export interface EmailVariableGroup {
  label: string
  options: EmailVariableOption[]
}

/**
 * Loads insertable email-template variables for a Function Unit:
 * - main-table fields  -> ${fieldName}
 * - sub-table fields   -> ${subTableField:<bindingId>:<fieldName>}
 * - sub-table tables   -> ${subTableHtml:<bindingId>:<col=Header,...>} (one row per record)
 */
export function useEmailTemplateVariables(functionUnitId: number) {
  const groups = ref<EmailVariableGroup[]>([])
  const loading = ref(false)

  /** Structural / MI bookkeeping columns that should not appear in an email table by default. */
  const META_COLUMNS = new Set<string>([
    'row_id', 'sub_task_id', 'sub_task_current_node', 'sub_task_status',
    'assignee_id', 'assignee', 'id_idw', 'participant_id', 'task_status'
  ])

  /** Strip characters that would break the ${subTableHtml:id:col=Header,...} grammar. */
  function sanitizeHeader(label: string): string {
    return label.replace(/[,=}]/g, ' ').trim()
  }

  /**
   * A sub-table may be bound by several forms (PROCESS submission + TASK forms), each with its
   * own binding id. The email runtime keeps the rows under the submission (PROCESS) binding, so we
   * surface ONE representative binding per table — preferring the PROCESS form — to avoid listing
   * the same sub-table multiple times with binding ids that would resolve to empty at send time.
   */
  function pickRepresentativeSubBindings(forms: FormDefinition[]): TableBinding[] {
    const byTable = new Map<number, { binding: TableBinding; isProcess: boolean }>()
    for (const form of forms) {
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

  function buildMainFieldGroups(tables: TableDefinition[]): EmailVariableGroup[] {
    return tables
      .filter(tbl => tbl.tableType === 'MAIN'
        && Array.isArray(tbl.fieldDefinitions)
        && tbl.fieldDefinitions.length > 0)
      .map(tbl => ({
        label: tbl.tableDisplayName || tbl.tableName,
        options: tbl.fieldDefinitions.map(f => ({
          token: `\${${f.fieldName}}`,
          label: `${f.displayName || f.fieldName} (${f.fieldName})`
        }))
      }))
  }

  function buildSubTableFieldGroups(
    tables: TableDefinition[],
    bindings: TableBinding[]
  ): EmailVariableGroup[] {
    const tableById = new Map<number, TableDefinition>(tables.map(tbl => [tbl.id, tbl]))
    const groups: EmailVariableGroup[] = []

    for (const binding of bindings) {
      const tbl = tableById.get(binding.tableId)
      if (!tbl?.fieldDefinitions?.length) continue

      groups.push({
        label: `${tbl.tableDisplayName || tbl.tableName} (#${binding.id})`,
        options: tbl.fieldDefinitions
          .filter(f => !META_COLUMNS.has(f.fieldName))
          .map(f => ({
            token: `\${subTableField:${binding.id}:${f.fieldName}}`,
            label: `${f.displayName || f.fieldName} (${f.fieldName})`
          }))
      })
    }
    return groups
  }

  function buildSubTableHtmlGroup(
    tables: TableDefinition[],
    bindings: TableBinding[]
  ): EmailVariableGroup | null {
    const tableById = new Map<number, TableDefinition>(tables.map(tbl => [tbl.id, tbl]))
    const options: EmailVariableOption[] = []
    for (const b of bindings) {
      const tbl = tableById.get(b.tableId)
      const businessFields = (tbl?.fieldDefinitions || [])
        .filter(f => !META_COLUMNS.has(f.fieldName))
      const tableLabel = tbl?.tableDisplayName || tbl?.tableName || b.tableName || `#${b.id}`

      if (businessFields.length > 0) {
        const cols = businessFields
          .map(f => `${f.fieldName}=${sanitizeHeader(f.displayName || f.fieldName)}`)
          .join(',')
        options.push({
          token: `\${subTableHtml:${b.id}:${cols}}`,
          label: tableLabel
        })
      } else {
        options.push({
          token: `\${subTableHtml:${b.id}}`,
          label: tableLabel
        })
      }
    }
    if (!options.length) return null
    return { label: '__SUBTABLES__', options }
  }

  async function load() {
    loading.value = true
    try {
      const [tablesRes, formsRes] = await Promise.all([
        functionUnitApi.getTables(functionUnitId),
        functionUnitApi.getForms(functionUnitId)
      ])
      const tables = tablesRes.data || []
      const forms = formsRes.data || []
      const subBindings = pickRepresentativeSubBindings(forms)
      const mainGroups = buildMainFieldGroups(tables)
      const subFieldGroups = buildSubTableFieldGroups(tables, subBindings)
      const subHtmlGroup = buildSubTableHtmlGroup(tables, subBindings)
      groups.value = [
        ...mainGroups,
        ...subFieldGroups,
        ...(subHtmlGroup ? [subHtmlGroup] : [])
      ]
    } catch {
      groups.value = []
    } finally {
      loading.value = false
    }
  }

  return { groups, loading, load }
}
