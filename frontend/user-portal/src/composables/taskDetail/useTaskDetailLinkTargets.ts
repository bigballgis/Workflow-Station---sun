import {
  resolveSubTablePrimaryKeyFields,
} from '@/composables/tasks/shared'
import {
  resolveSubTableSchemaByTableId,
} from '@/components/subTableAddDialogHelpers'
import type { TaskDetailState } from './useTaskDetailState'
import type { TaskDetailCtx } from './context'

export interface TaskDetailLinkTargetFns {
  findRawBindingInForms: (
    forms: any[] | undefined,
    bindingId: number,
  ) => { raw: any; formConfig: Record<string, any> } | null
  mergeLinkFormTargetBindingsInto: (
    bindings: TaskDetailState['subTableBindings']['value'],
    contentForms: any[] | undefined,
    localFormConfig: Record<string, any>,
    localSubForms: Record<string, any>,
  ) => void
}

export function createTaskDetailLinkTargets(ctx: TaskDetailCtx): TaskDetailLinkTargetFns {
  const { subTableBindings } = ctx

  /** Owning form JSON for a binding id (any form in the function unit may declare the binding). */
  function findRawBindingInForms(
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

  function linkTargetHasLocalSchema(tid: number, formConfig: Record<string, any>, subForms: Record<string, any>): boolean {
    const sid = String(tid)
    const sf = subForms?.[tid] ?? subForms?.[sid]
    if (sf?.rule && Array.isArray(sf.rule) && sf.rule.length > 0) return true
    const lv = formConfig?.subListViews?.[tid] ?? formConfig?.subListViews?.[sid]
    if (lv?.columns && Array.isArray(lv.columns) && lv.columns.length > 0) return true
    return false
  }

  /**
   * subForms/subListViews for a binding id may be saved under **another** form in the same function unit
   * (e.g. subtable2 edited on form B while the user task runs form A that only links to it).
   */
  function resolveSubTableSchemaSourceForTarget(
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
    if (linkTargetHasLocalSchema(tid, preferFormConfig, preferSubForms)) {
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
      if (linkTargetHasLocalSchema(tid, formConfig, sf)) {
        return {
          formConfig,
          subForms: sf,
          origin: 'crossForm',
          sourceFormName: f.name != null ? String(f.name) : undefined
        }
      }
    }
    const found = findRawBindingInForms(contentForms, tid)
    const tableId = found?.raw?.tableId != null ? Number(found.raw.tableId) : NaN
    if (Number.isFinite(tableId)) {
      const alt = resolveSubTableSchemaByTableId(tableId, contentForms, tid)
      if (alt) {
        return {
          formConfig: alt.formConfig,
          subForms: alt.subForms,
          origin: 'crossForm',
          sourceFormName: undefined,
        }
      }
    }
    return null
  }

  /**
   * Link Form targets may reference bindings omitted from the active form's tableBindings slice.
   * Pull those definitions from any form in the function unit so FormRenderer can resolve inline subtable2.
   * If the target never appears in any FU form's tableBindings (API slice gap), fall back to the **current**
   * form's configJson.subForms / subListViews — designer often still stores subtable2 schema there.
   */
  function mergeLinkFormTargetBindingsInto(
    bindings: typeof subTableBindings.value,
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
      for (const tid of targetIds) {
        if (known.has(tid)) continue
        const found = findRawBindingInForms(contentForms, tid)
        if (found) {
          const { raw, formConfig } = found
          if (raw.bindingType === 'PRIMARY') continue
          const sf = formConfig.subForms || {}
          const schemaSrc =
            resolveSubTableSchemaSourceForTarget(tid, formConfig, sf, contentForms) ??
            ({ formConfig, subForms: sf, origin: 'local' as const } as const)
          const effFormConfig = schemaSrc.formConfig
          const effSubForms = schemaSrc.subForms
          const columns = ctx.deriveColumnsFromBinding(raw, effSubForms, effFormConfig)
          const subFormDesign = ctx.resolveSubFormDesign(raw, effSubForms)
          bindings.push({
            bindingId: raw.bindingId,
            tableId: raw.tableId ?? null,
            bindingType: raw.bindingType,
            bindingMode: raw.bindingMode,
            foreignKeyField: raw.foreignKeyField,
            tableName: raw.tableDisplayName || raw.tableName,
            designerTableName: raw.tableName,
            relationTableId: (raw as any).relationTableId ?? null,
            relationTableName: (raw as any).relationTableName ?? null,
            tableType: raw.tableType,
            tableDescription: raw.tableDescription,
            columns,
            formFields: subFormDesign.formFields,
            formOptions: subFormDesign.formOptions,
            assignmentConfig: raw.assignmentConfig,
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
        const syntheticSchema = resolveSubTableSchemaSourceForTarget(
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
          designerTableName: tableLabel,
          tableType: '',
          tableDescription: ''
        }
        const columns = ctx.deriveColumnsFromBinding(synthetic, syntheticSchema.subForms, syntheticSchema.formConfig)
        const subFormDesign = ctx.resolveSubFormDesign(synthetic, syntheticSchema.subForms)
        bindings.push({
          bindingId: tid,
          tableId: null,
          bindingType: synthetic.bindingType,
          bindingMode: synthetic.bindingMode,
          foreignKeyField: synthetic.foreignKeyField,
          tableName: tableLabel,
          designerTableName: synthetic.designerTableName,
          tableType: synthetic.tableType,
          tableDescription: synthetic.tableDescription,
          columns,
          formFields: subFormDesign.formFields,
          formOptions: subFormDesign.formOptions,
          assignmentConfig: synthetic.assignmentConfig,
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
    findRawBindingInForms,
    mergeLinkFormTargetBindingsInto,
  }
}
