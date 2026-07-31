import type { FormField } from '@/components/FormRenderer.vue'
import {
  collectLinkFormTargetBindingIdsFromSubListViews,
  filterLinkOnlyStandaloneSubTableFields,
} from '@/components/formRendererHelpers'
import { resolveSubTablePrimaryKeyFields } from '@/composables/tasks/shared'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailLinkBindingsFns {
  findRawBindingInFormsForLinkMerge: (
    forms: any[] | undefined,
    bindingId: number,
  ) => { raw: any; formConfig: Record<string, any> } | null
  linkTargetHasLocalSchemaForMerge: (tid: number, formConfig: Record<string, any>, subForms: Record<string, any>) => boolean
  resolveSubTableSchemaSourceForTargetMerge: (
    tid: number,
    preferFormConfig: Record<string, any>,
    preferSubForms: Record<string, any>,
    contentForms: any[] | undefined,
  ) => {
    formConfig: Record<string, any>
    subForms: Record<string, any>
    origin: 'local' | 'crossForm'
    sourceFormName?: string
  } | null
  stripLinkOnlySubTableFieldsFromBindings: (
    bindings: Array<{ bindingId: number; formFields?: FormField[] }>,
    subForms: Record<string, unknown>,
    mainFormRule?: unknown[],
    formConfig?: Record<string, unknown> | null,
  ) => void
  applyLinkOnlySubTableFieldFilterToMainForm: (formConfig: Record<string, any>) => void
  mergeLinkFormTargetBindingsInto: (
    bindings: Array<{
      bindingId: number
      tableId?: number | null
      bindingType: string
      bindingMode: string
      foreignKeyField: string | null
      tableName: string
      physicalTableName?: string
      tableType: string
      tableDescription: string
      columns: Array<{ field: string; label: string; type?: string }>
      data: any[]
      subMode?: string
      formFields?: FormField[]
      formOptions?: Record<string, any>
      portalViews?: Record<string, any> | null
      primaryKeyFields?: string[]
    }>,
    contentForms: any[] | undefined,
    localFormConfig: Record<string, any>,
    localSubForms: Record<string, any>,
  ) => void
}

export function createApplicationDetailLinkBindings(ctx: ApplicationDetailCtx): ApplicationDetailLinkBindingsFns {
  const { formFields, formTabs, subTableBindings, mainFormNativeSubTableBindingIds } = ctx

  /** Owning form JSON for a binding id (any form in the function unit may declare the binding). */
  function findRawBindingInFormsForLinkMerge(
    forms: any[] | undefined,
    bindingId: number
  ): { raw: any; formConfig: Record<string, any> } | null {
    if (!forms?.length) return null
    for (const f of forms) {
      const list = f.tableBindings || []
      const hit = list.find((x: any) => Number(x.bindingId) === Number(bindingId))
      if (hit) {
        let formConfig: Record<string, any> = {}
        try {
          formConfig = typeof f.data === 'string' ? JSON.parse(f.data || '{}') : (f.data || {})
        } catch {
          formConfig = {}
        }
        return { raw: hit, formConfig }
      }
    }
    return null
  }

  function linkTargetHasLocalSchemaForMerge(tid: number, formConfig: Record<string, any>, subForms: Record<string, any>): boolean {
    const sid = String(tid)
    const sf = subForms?.[tid] ?? subForms?.[sid]
    if (sf?.rule && Array.isArray(sf.rule) && sf.rule.length > 0) return true
    const lv = formConfig?.subListViews?.[tid] ?? formConfig?.subListViews?.[sid]
    if (lv?.columns && Array.isArray(lv.columns) && lv.columns.length > 0) return true
    return false
  }

  function resolveSubTableSchemaSourceForTargetMerge(
    tid: number,
    preferFormConfig: Record<string, any>,
    preferSubForms: Record<string, any>,
    contentForms: any[] | undefined
  ): {
    formConfig: Record<string, any>
    subForms: Record<string, any>
    origin: 'local' | 'crossForm'
    sourceFormName?: string
  } | null {
    if (linkTargetHasLocalSchemaForMerge(tid, preferFormConfig, preferSubForms)) {
      return { formConfig: preferFormConfig, subForms: preferSubForms, origin: 'local' }
    }
    if (!contentForms?.length) return null
    for (const f of contentForms) {
      let formConfig: Record<string, any> = {}
      try {
        formConfig = typeof f.data === 'string' ? JSON.parse(f.data || '{}') : (f.data || {})
      } catch {
        formConfig = {}
      }
      const sf = formConfig.subForms || {}
      if (linkTargetHasLocalSchemaForMerge(tid, formConfig, sf)) {
        return {
          formConfig,
          subForms: sf,
          origin: 'crossForm',
          sourceFormName: f.name != null ? String(f.name) : undefined
        }
      }
    }
    return null
  }

  /**
   * Link Form targets (e.g. subtable2) keep {@code formFields} for modals but drop duplicate
   * {@code subTable} widgets when the designer did not place them on the sub-form canvas.
   */
  function stripLinkOnlySubTableFieldsFromBindings(
    bindings: Array<{ bindingId: number; formFields?: FormField[] }>,
    subForms: Record<string, unknown>,
    mainFormRule?: unknown[],
    formConfig?: Record<string, unknown> | null,
  ) {
    for (const b of bindings) {
      if (!Array.isArray(b.formFields) || b.formFields.length === 0) continue
      const design = (subForms?.[b.bindingId] ?? subForms?.[String(b.bindingId)] ?? {}) as {
        rule?: unknown[]
      }
      const rule = Array.isArray(design.rule) && design.rule.length > 0
        ? design.rule
        : (Array.isArray(mainFormRule) ? mainFormRule : [])
      b.formFields = filterLinkOnlyStandaloneSubTableFields(b.formFields, bindings, rule, undefined, formConfig)
    }
  }

  /** Drop link-only sub-table widgets from the main form field tree once bindings are loaded. */
  function applyLinkOnlySubTableFieldFilterToMainForm(formConfig: Record<string, any>) {
    const rules = Array.isArray(formConfig?.rule) ? formConfig.rule : []
    const bindings = subTableBindings.value
    const nativeIdSet = new Set(mainFormNativeSubTableBindingIds.value.map(Number))
    if (formFields.value.length > 0) {
      formFields.value = filterLinkOnlyStandaloneSubTableFields(
        formFields.value,
        bindings,
        rules,
        nativeIdSet,
        formConfig,
      )
    }
    if (formTabs.value.length > 0) {
      formTabs.value = formTabs.value.map(tab => ({
        ...tab,
        fields: filterLinkOnlyStandaloneSubTableFields(tab.fields, bindings, rules, nativeIdSet, formConfig),
      }))
    }
  }

  /**
   * Link Form targets may reference bindings omitted from the active form's tableBindings slice.
   * Same contract as tasks/detail.vue so Link Form modal / fallback rows resolve on My Request.
   */
  function mergeLinkFormTargetBindingsInto(
    bindings: Array<{
      bindingId: number
      tableId?: number | null
      bindingType: string
      bindingMode: string
      foreignKeyField: string | null
      tableName: string
      physicalTableName?: string
      tableType: string
      tableDescription: string
      columns: Array<{ field: string; label: string; type?: string }>
      data: any[]
      subMode?: string
      formFields?: FormField[]
      formOptions?: Record<string, any>
      portalViews?: Record<string, any> | null
      primaryKeyFields?: string[]
    }>,
    contentForms: any[] | undefined,
    localFormConfig: Record<string, any>,
    localSubForms: Record<string, any>
  ) {
    const known = new Set(bindings.map(b => Number(b.bindingId)))
    let changed = true
    while (changed) {
      changed = false
      const targetIds = new Set<number>()
      const targetNameHint = new Map<number, string | undefined>()
      for (const b of bindings) {
        for (const col of b.columns || []) {
          if ((col as { type?: string }).type !== 'linkForm') continue
          const rawTid = (col as { props?: { boundSubTableBindingId?: number | string; boundSubTableName?: string } }).props
            ?.boundSubTableBindingId
          if (rawTid == null || rawTid === '') continue
          const n = Number(rawTid)
          if (Number.isNaN(n)) continue
          targetIds.add(n)
          if (!targetNameHint.has(n)) {
            const nm = (col as { props?: { boundSubTableName?: string } }).props?.boundSubTableName
            targetNameHint.set(n, nm != null && String(nm).trim() !== '' ? String(nm) : undefined)
          }
        }
      }
      for (const tid of collectLinkFormTargetBindingIdsFromSubListViews(localFormConfig)) {
        targetIds.add(tid)
      }
      for (const tid of targetIds) {
        if (known.has(tid)) continue
        const found = findRawBindingInFormsForLinkMerge(contentForms, tid)
        if (found) {
          const { raw, formConfig } = found
          if (raw.bindingType === 'PRIMARY') continue
          const sf = formConfig.subForms || {}
          const schemaSrc =
            resolveSubTableSchemaSourceForTargetMerge(tid, formConfig, sf, contentForms) ??
            ({ formConfig, subForms: sf, origin: 'local' as const })
          const effFormConfig = schemaSrc.formConfig
          const effSubForms = schemaSrc.subForms
          const columns = ctx.deriveColumnsFromBinding(raw, effFormConfig)
          const subFormDesign = ctx.resolveSubFormDesign(raw, effSubForms)
          const stpv = effFormConfig.subTablePortalViews || {}
          const bindingPortalViews = stpv[raw.bindingId] ?? stpv[String(raw.bindingId)] ?? null
          bindings.push({
            bindingId: raw.bindingId,
            tableId: raw.tableId != null ? Number(raw.tableId) : null,
            bindingType: raw.bindingType,
            bindingMode: raw.bindingMode,
            foreignKeyField: raw.foreignKeyField,
            tableName: raw.tableDisplayName || raw.tableName,
            physicalTableName: raw.tableName,
            tableType: raw.tableType,
            tableDescription: raw.tableDescription,
            columns,
            subMode: raw.subMode,
            formFields: subFormDesign.formFields,
            formOptions: subFormDesign.formOptions,
            assignmentConfig: raw.assignmentConfig,
            portalViews: bindingPortalViews,
            primaryKeyFields: resolveSubTablePrimaryKeyFields(
              raw.primaryKeyFields,
              raw.bindingId,
              effFormConfig
            ),
            fieldDefinitions: raw.fieldDefinitions ?? [],
            bindingLinkMode: raw.bindingLinkMode,
            data: []
          })
          known.add(Number(raw.bindingId))
          changed = true
          continue
        }
        const syntheticSchema = resolveSubTableSchemaSourceForTargetMerge(
          tid,
          localFormConfig,
          localSubForms,
          contentForms
        )
        if (!syntheticSchema) {
          continue
        }
        const hint = targetNameHint.get(tid)
        const tableLabel = hint && String(hint).trim() ? String(hint).trim() : `binding_${tid}`
        const synthetic = {
          bindingId: tid,
          tableId: null as number | null,
          bindingType: 'SUB',
          bindingMode: 'EDITABLE',
          foreignKeyField: null as string | null,
          tableName: tableLabel,
          physicalTableName: tableLabel,
          tableType: '',
          tableDescription: ''
        }
        const columns = ctx.deriveColumnsFromBinding(synthetic, syntheticSchema.formConfig)
        const subFormDesign = ctx.resolveSubFormDesign(synthetic, syntheticSchema.subForms)
        const stpvSchema = syntheticSchema.formConfig.subTablePortalViews || {}
        const bindingPortalViews = stpvSchema[tid] ?? stpvSchema[String(tid)] ?? null
        bindings.push({
          bindingId: tid,
          tableId: null,
          bindingType: synthetic.bindingType,
          bindingMode: synthetic.bindingMode,
          foreignKeyField: synthetic.foreignKeyField,
          tableName: tableLabel,
          physicalTableName: synthetic.physicalTableName,
          tableType: synthetic.tableType,
          tableDescription: synthetic.tableDescription,
          columns,
          formFields: subFormDesign.formFields,
          formOptions: subFormDesign.formOptions,
          assignmentConfig: synthetic.assignmentConfig,
          portalViews: bindingPortalViews,
          primaryKeyFields: resolveSubTablePrimaryKeyFields(null, tid, syntheticSchema.formConfig),
          fieldDefinitions: [],
          data: []
        })
        known.add(tid)
        changed = true
      }
    }
  }

  return {
    findRawBindingInFormsForLinkMerge,
    linkTargetHasLocalSchemaForMerge,
    resolveSubTableSchemaSourceForTargetMerge,
    stripLinkOnlySubTableFieldsFromBindings,
    applyLinkOnlySubTableFieldFilterToMainForm,
    mergeLinkFormTargetBindingsInto,
  }
}
