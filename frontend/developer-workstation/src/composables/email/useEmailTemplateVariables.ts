import { ref, toValue, type MaybeRefOrGetter } from 'vue'
import { functionUnitApi, type TableDefinition, type FormDefinition, type TableBinding } from '@/api/functionUnit'
import { connectionApi } from '@/api/connection'
import { relationTableBindingApi, type RelationTableDTO } from '@/api/relationTable'
import { buildLookupCatalogGroups } from '@/utils/mainTableViewLookupCatalog'

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

/** Shown in the Subject placeholder — pass as a vue-i18n param, not inside locale strings. */
export const EMAIL_SUBJECT_VAR_EXAMPLE = '${name}'

/** Group label sentinel — mapped to i18n in EmailTemplateDesigner. */
export const EMAIL_VAR_GROUP_SUBTABLES = '__SUBTABLES__'
export const EMAIL_VAR_GROUP_LOOKUP = '__LOOKUP__'
export const EMAIL_VAR_GROUP_ENV_TEXT = '__ENV_TEXT__'

const TEXT_ENV_KEY = /^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/

async function loadOptionalFormCatalog(
  functionUnitId: number,
): Promise<{ forms: FormDefinition[]; relationTables: RelationTableDTO[] }> {
  try {
    const [formsRes, rtRes] = await Promise.all([
      functionUnitApi.getForms(functionUnitId),
      relationTableBindingApi.getAvailableTables().catch(() => ({ data: [] as RelationTableDTO[] })),
    ])
    const relationTables = ((rtRes as { data?: RelationTableDTO[] })?.data
      || (Array.isArray(rtRes) ? rtRes : [])) as RelationTableDTO[]
    return { forms: formsRes.data || [], relationTables }
  } catch {
    return { forms: [], relationTables: [] }
  }
}

async function loadTextEnvGroup(functionUnitId: number): Promise<EmailVariableGroup | null> {
  try {
    const res = await connectionApi.textOptions(functionUnitId)
    const options = (res.data || [])
      .filter(row => TEXT_ENV_KEY.test(row.varKey || ''))
      .map(row => ({
        token: `\${env:${row.varKey}}`,
        label: `${row.displayName || row.varKey} (${row.varKey})`,
      }))
    if (!options.length) return null
    return { label: EMAIL_VAR_GROUP_ENV_TEXT, options }
  } catch {
    // FALLBACK(ux): TEXT catalog unavailable — main-table tokens still insert.
    return null
  }
}

/**
 * Loads insertable email-template variables for a Function Unit:
 * - main-table fields  -> ${fieldName}
 * - sub-table fields   -> ${subTableField:<bindingId>:<fieldName>}
 * - sub-table tables   -> ${subTableHtml:<bindingId>:<col=Header,...>} (one row per record)
 * - lookup/related RT  -> ${lookupField:<sourceField>:<targetAttr>}
 */
export function useEmailTemplateVariables(functionUnitId: MaybeRefOrGetter<number>) {
  const groups = ref<EmailVariableGroup[]>([])
  const loading = ref(false)

  /** Structural / MI bookkeeping columns that should not appear in an email table by default. */
  const META_COLUMNS = new Set<string>([
    'row_id', 'sub_task_id', 'sub_task_current_node', 'sub_task_status',
    'assignee_id', 'assignee', 'id_idw', 'participant_id', 'task_status',
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
      .filter(tbl => String(tbl.tableType || '').toUpperCase() === 'MAIN'
        && Array.isArray(tbl.fieldDefinitions)
        && tbl.fieldDefinitions.length > 0)
      .map(tbl => ({
        label: tbl.tableDisplayName || tbl.tableName,
        options: tbl.fieldDefinitions.map(f => ({
          token: `\${${f.fieldName}}`,
          label: `${f.displayName || f.fieldName} (${f.fieldName})`,
        })),
      }))
  }

  function buildSubTableFieldGroups(
    tables: TableDefinition[],
    bindings: TableBinding[],
  ): EmailVariableGroup[] {
    const tableById = new Map<number, TableDefinition>(tables.map(tbl => [tbl.id, tbl]))
    const out: EmailVariableGroup[] = []

    for (const binding of bindings) {
      const tbl = tableById.get(binding.tableId)
      if (!tbl?.fieldDefinitions?.length) continue

      out.push({
        label: `${tbl.tableDisplayName || tbl.tableName} (#${binding.id})`,
        options: tbl.fieldDefinitions
          .filter(f => !META_COLUMNS.has(f.fieldName))
          .map(f => ({
            token: `\${subTableField:${binding.id}:${f.fieldName}}`,
            label: `${f.displayName || f.fieldName} (${f.fieldName})`,
          })),
      })
    }
    return out
  }

  function buildSubTableHtmlGroup(
    tables: TableDefinition[],
    bindings: TableBinding[],
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
          label: tableLabel,
        })
      } else {
        options.push({
          token: `\${subTableHtml:${b.id}}`,
          label: tableLabel,
        })
      }
    }
    if (!options.length) return null
    return { label: EMAIL_VAR_GROUP_SUBTABLES, options }
  }

  /** Lookup/Related: attributes on Relation Tables targeted by form Lookup widgets. */
  function buildLookupFieldGroups(
    forms: FormDefinition[],
    relationTables: RelationTableDTO[],
  ): EmailVariableGroup[] {
    return buildEmailLookupVariableGroups(forms, relationTables)
  }

  async function load() {
    const id = Number(toValue(functionUnitId))
    if (!Number.isFinite(id) || id <= 0) {
      groups.value = []
      return
    }
    loading.value = true
    try {
      const tablesRes = await functionUnitApi.getTables(id)
      const tables = tablesRes.data || []
      // FALLBACK(ux): forms/RT catalog is only needed for sub-table and lookup tokens.
      // Send Task To/From still need MAIN ${fieldName} when those calls fail.
      const { forms, relationTables } = await loadOptionalFormCatalog(id)
      const subBindings = pickRepresentativeSubBindings(forms)
      const subHtmlGroup = buildSubTableHtmlGroup(tables, subBindings)
      const envGroup = await loadTextEnvGroup(id)
      groups.value = [
        ...buildMainFieldGroups(tables),
        ...buildLookupFieldGroups(forms, relationTables),
        ...buildSubTableFieldGroups(tables, subBindings),
        ...(subHtmlGroup ? [subHtmlGroup] : []),
        ...(envGroup ? [envGroup] : []),
      ]
    } catch {
      groups.value = []
    } finally {
      loading.value = false
    }
  }

  return { groups, loading, load }
}

/**
 * Pure builder for Insert Variable → Lookup attribute groups.
 * Token form: ${lookupField:sourceField:targetAttr}
 */
export function buildEmailLookupVariableGroups(
  forms: FormDefinition[],
  relationTables: RelationTableDTO[],
): EmailVariableGroup[] {
  const catalogGroups = buildLookupCatalogGroups(forms, relationTables)
  const out: EmailVariableGroup[] = []
  for (const g of catalogGroups) {
    const options: EmailVariableOption[] = []
    for (const f of g.fields || []) {
      const attr = String(f.lookupDisplayField || f.fieldName || '').trim()
      if (!attr) continue
      options.push({
        token: `\${lookupField:${g.sourceField}:${attr}}`,
        label: `${f.displayName || attr} (${attr})`,
      })
    }
    if (!options.length) continue
    out.push({
      label: `${EMAIL_VAR_GROUP_LOOKUP}:${g.sourceLabel || g.sourceField}`,
      options,
    })
  }
  return out
}

/** Map sentinel group labels (__SUBTABLES__ / __LOOKUP__:…) to i18n text. */
export function resolveEmailVariableGroupLabel(
  label: string,
  t: (key: string, params?: Record<string, unknown>) => string,
): string {
  if (label === EMAIL_VAR_GROUP_SUBTABLES) {
    return t('emailTemplate.subTableGroup')
  }
  const lookupPrefix = `${EMAIL_VAR_GROUP_LOOKUP}:`
  if (label.startsWith(lookupPrefix)) {
    return t('emailTemplate.lookupGroup', { source: label.slice(lookupPrefix.length) })
  }
  if (label === EMAIL_VAR_GROUP_ENV_TEXT) {
    return t('emailTemplate.envTextGroup')
  }
  return label
}
