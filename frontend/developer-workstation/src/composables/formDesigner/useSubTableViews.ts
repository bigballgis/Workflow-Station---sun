import { ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import type { FormDefinition } from '@/api/functionUnit'
import { subTableViewApi, type SubTableFieldDTO, type SubTableViewConfig } from '@/api/subTableView'
import { resolveBindingDisplayName } from '@/utils/bindingDisplayHelpers'
import {
  prepareFormCreateRulesForPersist,
  serializeFormCreateOptionsForPersist,
} from '@/utils/formCreateDefaultEvents'
import { stripFormCreateRulesDisabledDeep } from '@/utils/formCreateRuleUtils'

export type SubTableListColumnDTO = SubTableFieldDTO & {
  columnType?: 'field' | 'linkForm' | 'lookup'
  componentId?: number
  linkedFormId?: number
  linkedFormName?: string
  linkText?: string
  columnLabel?: string
  boundSubTableBindingId?: number
  boundSubTableName?: string
  lookupConfig?: string
}

export interface LinkFormComponentInfo {
  id: number
  componentName: string
  linkedFormId: number
  linkedFormName?: string
  displayField?: string
  linkText: string
  columnLabel?: string
  sortOrder: number
}

interface UseSubTableViewsOptions {
  functionUnitId: number
  store: {
    tables: any[]
    updateForm: (functionUnitId: number, formId: number, payload: Record<string, any>) => Promise<any>
  }
  selectedForm: Ref<FormDefinition | null>
  designerSubBindings: ComputedRef<Array<{
    bindingId: number
    bindingType: string
    tableId: number
    tableName: string
  }>>
  subDesignerRefs: Ref<any[]>
  linkFormComponents: Ref<LinkFormComponentInfo[]>
  defaultFormOption: ComputedRef<Record<string, any>>
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Sub-table / relation-table view state for FormDesigner: list-view columns,
 * relation view fields, per-binding sub form rule cache and the designer refs
 * that feed them. Owns load/save of sub-table list view configuration.
 */
export function useSubTableViews(options: UseSubTableViewsOptions) {
  const {
    functionUnitId, store, selectedForm, designerSubBindings,
    subDesignerRefs, linkFormComponents, defaultFormOption, t,
  } = options

  const relationViewState = ref<Record<number, { allFields: any[]; viewFields: any[] }>>({})
  // Relation table view refs (keyed by bindingId)
  const relationTableViewRefs = ref<Record<number, any>>({})
  // Sub-table list view refs (keyed by bindingId)
  const subTableListViewRefs = ref<Record<number, any>>({})
  // Sub-table list view state (keyed by bindingId)
  const subTableViewState = ref<Record<number, { allFields: SubTableFieldDTO[]; viewFields: SubTableListColumnDTO[] }>>({})
  // In-memory cache: persists sub form rules across tab switches (tabs unmount when not active)
  const subFormCache = ref<Record<number, { rule: any[]; options: any }>>({})

  function setSubTableListViewRef(el: any, bindingId: number) {
    if (el) {
      subTableListViewRefs.value[bindingId] = el
    } else {
      delete subTableListViewRefs.value[bindingId]
    }
  }

  function setSubDesignerRef(el: any, index: number) {
    console.log('[FormDesigner] setSubDesignerRef:', { el: !!el, index, binding: designerSubBindings.value[index] })
    if (!el) {
      // Tab is unmounting — snapshot current rule into cache before ref is lost
      const prev = subDesignerRefs.value[index]
      if (prev) {
        const binding = designerSubBindings.value[index]
        if (binding) {
          try {
            const rawRule = stripFormCreateRulesDisabledDeep(prev.getRule() || []) as any[]
            prepareFormCreateRulesForPersist(rawRule)
            subFormCache.value[binding.bindingId] = {
              rule: rawRule,
              options: serializeFormCreateOptionsForPersist(
                prev.getOption() as Record<string, unknown>,
              ),
            }
            console.log('[FormDesigner] Cached sub form:', { bindingId: binding.bindingId, ruleCount: rawRule.length })
          } catch {}
        }
      }
    }
    subDesignerRefs.value[index] = el
  }

  function updateRelationViewFields(bindingId: number, fields: any[]) {
    const existing = relationViewState.value[bindingId] || { allFields: [], viewFields: [] }
    relationViewState.value = { ...relationViewState.value, [bindingId]: { ...existing, viewFields: fields } }
  }

  function updateRelationViewAllFields(bindingId: number, fields: any[]) {
    const existing = relationViewState.value[bindingId] || { allFields: [], viewFields: [] }
    relationViewState.value = {
      ...relationViewState.value,
      [bindingId]: { ...existing, allFields: fields },
    }
  }

  // Sub-table list view state management
  function updateSubTableViewFields(bindingId: number, fields: SubTableListColumnDTO[]) {
    const existing = subTableViewState.value[bindingId] || { allFields: [], viewFields: [] }
    subTableViewState.value = { ...subTableViewState.value, [bindingId]: { ...existing, viewFields: fields } }
  }

  function updateSubTableViewAllFields(bindingId: number, fields: SubTableFieldDTO[]) {
    const existing = subTableViewState.value[bindingId] || { allFields: [], viewFields: [] }
    subTableViewState.value = { ...subTableViewState.value, [bindingId]: { ...existing, allFields: fields } }
  }

  async function handleSubTableViewSave(bindingId: number) {
    const state = subTableViewState.value[bindingId]
    if (!state || !selectedForm.value) return

    const fields = state.viewFields
      .filter(f => !f.columnType || f.columnType === 'field')
      .map((f, index) => ({
      fieldName: f.fieldName,
      displayLabel: f.displayName || f.fieldName,
      columnWidth: 150,
      sortOrder: index,
      visible: true
    }))

    try {
      if (fields.length > 0) {
        await subTableViewApi.saveViewConfig(selectedForm.value.id, bindingId, fields)
      }
      await persistSubTableListViewColumns(bindingId, state.viewFields)
    } catch (e) {
      console.error('[FormDesigner] Failed to save sub-table view config:', e)
    }
  }

  async function persistSubTableListViewColumns(bindingId: number, columns: SubTableListColumnDTO[]) {
    if (!selectedForm.value) return
    const currentConfig = selectedForm.value.configJson || {}
    const subListViews = {
      ...(currentConfig.subListViews || {}),
      [bindingId]: { columns, allowEmptyColumns: columns.length === 0 }
    }
    const nextConfig = { ...currentConfig, subListViews }
    selectedForm.value = { ...selectedForm.value, configJson: nextConfig }
    const updated = await store.updateForm(functionUnitId, selectedForm.value.id, {
      formName: selectedForm.value.formName,
      formType: selectedForm.value.formType,
      description: selectedForm.value.description,
      configJson: nextConfig,
      ...(selectedForm.value.formType === 'TASK' && selectedForm.value.fieldPermissions
        ? { fieldPermissions: selectedForm.value.fieldPermissions }
        : {})
    })
    selectedForm.value = {
      ...selectedForm.value,
      configJson: updated.configJson || nextConfig
    }
  }

  async function loadSubTableViewConfig(bindingId: number, _binding?: any) {
    if (!selectedForm.value) return

    try {
      // Get or create the view config
      const res = await subTableViewApi.getOrCreateView(selectedForm.value.id, bindingId)
      const config: SubTableViewConfig = res.data

      // Get available fields from the sub-table
      const fieldsRes = await subTableViewApi.getAvailableFields(selectedForm.value.id, bindingId)
      const availableFields: SubTableFieldDTO[] = fieldsRes.data || []

      // Only columns explicitly saved in dw view config (designer picks from catalog).
      const viewFields: SubTableFieldDTO[] = config.viewFields
        .filter(f => f.visible !== false)
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map(f => {
          const available = availableFields.find(af => af.fieldName === f.fieldName)
          return {
            fieldName: f.fieldName,
            dataType: available?.dataType || 'VARCHAR',
            displayName: f.displayLabel || f.fieldName,
          } as SubTableFieldDTO
        })

      const savedListDesigner = (selectedForm.value.configJson?.subListViews || {})[bindingId] || {}
      const liveColumns = subTableViewState.value[bindingId]?.viewFields
      const listConfigForMerge = liveColumns?.length
        ? { ...savedListDesigner, columns: liveColumns }
        : savedListDesigner
      const mergedViewFields = mergeSubTableListColumns(viewFields, listConfigForMerge)
      subTableViewState.value[bindingId] = {
        allFields: availableFields,
        viewFields: mergedViewFields
      }
    } catch (e) {
      console.error('[FormDesigner] Failed to load sub-table view config:', e)
      // Initialize with empty state
      const savedListDesigner = (selectedForm.value.configJson?.subListViews || {})[bindingId] || {}
      const liveColumns = subTableViewState.value[bindingId]?.viewFields
      const listConfigForMerge = liveColumns?.length
        ? { ...savedListDesigner, columns: liveColumns }
        : savedListDesigner
      const mergedViewFields = mergeSubTableListColumns([], listConfigForMerge)
      subTableViewState.value[bindingId] = {
        allFields: [],
        viewFields: mergedViewFields
      }
    }
  }

  function getSubTableListViewBaseColumns(bindingId: number): SubTableListColumnDTO[] {
    const live = subTableViewState.value[bindingId]?.viewFields
    if (live?.length) return live
    const saved = (selectedForm.value?.configJson?.subListViews || {})[bindingId]?.columns
    return Array.isArray(saved) ? saved : []
  }

  /** Append new table/form fields to list view columns without removing link/lookup/action columns. */
  function appendSubTableListFieldColumns(
    existingColumns: SubTableListColumnDTO[],
    newFields: SubTableFieldDTO[]
  ): SubTableListColumnDTO[] {
    const existingFieldNames = new Set(
      existingColumns
        .filter(c => !c.columnType || c.columnType === 'field')
        .map(c => c.fieldName)
    )
    const merged = [...existingColumns]
    for (const field of newFields) {
      if (!field.fieldName || existingFieldNames.has(field.fieldName)) continue
      merged.push({ ...field, columnType: 'field' })
      existingFieldNames.add(field.fieldName)
    }
    return merged
  }

  function subTableFieldColumnsFromFormRule(rule: any[]): SubTableFieldDTO[] {
    if (!Array.isArray(rule)) return []
    return rule
      .filter(r => r?.field && r.type !== 'subTable')
      .map(r => ({
        fieldName: r.field,
        dataType: 'VARCHAR',
        nullable: true,
        isPrimaryKey: false,
        displayName: r.title || r.field,
      }))
  }

  function syncSubTableListViewFromFormRules(bindingId: number, rule: any[]) {
    const newFields = subTableFieldColumnsFromFormRule(rule)
    if (!newFields.length) return
    const state = subTableViewState.value[bindingId] || { allFields: [], viewFields: [] }
    const merged = appendSubTableListFieldColumns(getSubTableListViewBaseColumns(bindingId), newFields)
    if (merged.length === getSubTableListViewBaseColumns(bindingId).length) return
    subTableViewState.value = {
      ...subTableViewState.value,
      [bindingId]: { ...state, viewFields: merged }
    }
  }

  function mergeSubTableListColumns(
    viewFields: SubTableFieldDTO[],
    savedListConfig: any
  ): SubTableListColumnDTO[] {
    const fieldColumns = viewFields.map(field => ({ ...field, columnType: 'field' as const }))
    const savedColumns = Array.isArray(savedListConfig?.columns) ? savedListConfig.columns : []
    if (savedColumns.length > 0) {
      const fieldByName = new Map(fieldColumns.map(field => [field.fieldName, field]))
      const mergedColumns = savedColumns
        .map((column: any) => {
          if (column?.columnType === 'linkForm') return hydrateLinkFormColumn(column)
          if (column?.columnType === 'lookup') return hydrateLookupColumn(column)
          const field = fieldByName.get(column?.fieldName)
          if (field) {
            return { ...field, displayName: column.displayName || column.displayLabel || field.displayName }
          }
          // Keep columns that exist only in configJson (e.g. server dw_sub_table view config was cleared
          // or field names temporarily out of sync) — otherwise merge drops everything and save wipes subListViews.
          if (column?.fieldName) {
            return {
              fieldName: column.fieldName,
              dataType: column.dataType || 'VARCHAR',
              nullable: column.nullable !== false,
              isPrimaryKey: !!column.isPrimaryKey,
              displayName: column.displayLabel || column.displayName || column.fieldName,
              columnType: 'field' as const,
            } as SubTableListColumnDTO
          }
          return null
        })
        .filter(Boolean) as SubTableListColumnDTO[]
      return mergedColumns
    }

    const legacyRules = Array.isArray(savedListConfig?.rule) ? savedListConfig.rule : []
    const legacyLinkColumns = legacyRules
      .filter((rule: any) => rule?.type === 'linkForm')
      .map((rule: any) => hydrateLinkFormColumn({
        columnType: 'linkForm',
        componentId: rule._componentId ?? rule.props?._componentId
      }))
      .filter((column: SubTableListColumnDTO) => !!column.componentId)

    return [...fieldColumns, ...legacyLinkColumns]
  }

  function hydrateLookupColumn(column: any): SubTableListColumnDTO {
    return {
      columnType: 'lookup',
      fieldName: column.fieldName || `lookup:${column.bindingId || 'action'}`,
      dataType: 'LOOKUP',
      nullable: true,
      isPrimaryKey: false,
      displayName: column.displayName || column.columnLabel || 'Lookup',
      columnLabel: column.columnLabel || column.displayName || 'Lookup',
      lookupConfig: column.lookupConfig || '{}'
    }
  }

  function hydrateLinkFormColumn(column: any): SubTableListColumnDTO {
    const componentId = Number(column.componentId)
    const component = linkFormComponents.value.find(c => c.id === componentId)
    return {
      columnType: 'linkForm',
      fieldName: `linkForm:${componentId || column.fieldName || Date.now()}`,
      dataType: 'LINK_FORM',
      nullable: true,
      isPrimaryKey: false,
      componentId,
      linkedFormId: column.linkedFormId ?? component?.linkedFormId,
      linkedFormName: column.linkedFormName ?? component?.linkedFormName,
      displayName: column.displayName || column.columnLabel || component?.columnLabel || component?.componentName || t('linkForm.defaultLinkText'),
      columnLabel: column.columnLabel ?? component?.columnLabel,
      linkText: column.linkText || component?.linkText || t('linkForm.defaultLinkText'),
      boundSubTableBindingId: column.boundSubTableBindingId,
      boundSubTableName: column.boundSubTableName || resolveDesignerBindingDisplayName(column.boundSubTableBindingId) || undefined
    }
  }

  function resolveDesignerBindingDisplayName(bindingId: unknown): string {
    return resolveBindingDisplayName(bindingId, designerSubBindings.value, (tableId) => {
      const table = store.tables.find(t => t.id === tableId)
      return table?.tableDisplayName || table?.tableName
    })
  }

  function getSubTableFormDesign(bindingId: number): { rule: any[]; options: any } {
    const index = designerSubBindings.value.findIndex(b => b.bindingId === bindingId)
    const subRef = index >= 0 ? subDesignerRefs.value[index] : null
    const subForms = selectedForm.value?.configJson?.subForms || {}
    const saved = subForms[bindingId] || subForms[String(bindingId)] || {}
    try {
      if (subRef) {
        return {
          rule: subRef.getRule?.() || [],
          options: subRef.getOption?.() || {}
        }
      }
    } catch {}
    return subFormCache.value[bindingId] || { rule: saved.rule || [], options: saved.options || defaultFormOption.value }
  }

  function getSubTableFormRule(bindingId: number): any[] {
    return getSubTableFormDesign(bindingId).rule
  }

  function getSubTableFormOption(bindingId: number): any {
    return getSubTableFormDesign(bindingId).options
  }

  return {
    relationViewState,
    relationTableViewRefs,
    subTableListViewRefs,
    subTableViewState,
    subFormCache,
    setSubTableListViewRef,
    setSubDesignerRef,
    updateRelationViewFields,
    updateRelationViewAllFields,
    updateSubTableViewFields,
    updateSubTableViewAllFields,
    handleSubTableViewSave,
    persistSubTableListViewColumns,
    loadSubTableViewConfig,
    getSubTableListViewBaseColumns,
    appendSubTableListFieldColumns,
    subTableFieldColumnsFromFormRule,
    syncSubTableListViewFromFormRules,
    mergeSubTableListColumns,
    resolveDesignerBindingDisplayName,
    getSubTableFormDesign,
    getSubTableFormRule,
    getSubTableFormOption,
  }
}
