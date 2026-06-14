import { computed, nextTick, reactive, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FieldDefinition, FormDefinition, TableBinding } from '@/api/functionUnit'
import type { FormPreviewItem } from '@/components/designer/formPreviewTypes'
import { getRuleChildren, isCardRule, getLayoutLabel } from '@/utils/formDesigner'
import {
  applyPreviewDefaultsToItemRules,
  attachPreviewMountedDefaultSync,
  materializePreviewItemsEvents,
} from '@/utils/formCreatePreviewEvents'
import { ensureFormCreateRulesValidationDeep } from '@/utils/formCreateValidateRules'
import {
  mergePreviewValidateFormOption,
  prepareDesignerPreviewValidation,
} from '@/utils/formDesignerPreviewValidation'
import { wrapFormLevelOnChangeForFormCreate } from '@/utils/formCreateEventRuntime'
import { isEmptyFormCreateHandler } from '@/utils/formCreateDefaultEvents'
import {
  applyTableFieldDefaultsToRulesAndModel,
  seedFormDataFromRules,
  syncModelValuesOntoRules,
} from '@/utils/formCreateRuleDefaults'
import {
  isFormCreateRuleHidden,
  mapFormCreateRulesReadonlyDeep,
} from '@/utils/formCreateRuleUtils'
import { syncFormRulesWithTableFields } from '@/utils/formFieldMeta'
import type { PortalViewsValue } from './useSubTablePortalViews'

type DesignerLike = { getRule?: () => unknown[]; setRule?: (r: unknown[]) => void } | null | undefined

interface UseFormPreviewBuildOptions {
  functionUnitId: number
  store: { tables: any[]; fetchTables: (functionUnitId: number) => Promise<unknown> }
  selectedForm: Ref<FormDefinition | null>
  designerRef: Ref<any>
  subDesignerRefs: Ref<any[]>
  subFormCache: Ref<Record<number, { rule: any[]; options: any }>>
  designerSubBindings: ComputedRef<Array<{ bindingId: number; tableId: number }>>
  getActiveDesignerRef: () => DesignerLike
  getTableFieldDefinitions: (tableId: number) => FieldDefinition[]
  getPrimaryBindingFieldDefinitions: () => FieldDefinition[]
  refreshFormRulesFromTableMetadata: () => void
  toSubTablePreviewColumns: (bindingId: number, rule: any[], config: any) => any[]
  makeLookupPreviewItem: (ruleItem: any, config: any) => any
  mergePortalViewsForPreview: (ruleItem: any, bindingId: number) => PortalViewsValue
  getTableName: (tableId: number, fallback?: string) => string
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Form Preview dialog state and the preview build pipeline for FormDesigner:
 * normalizes live designer rules, derives sub-table/lookup preview items and
 * assembles the FormPreviewItems model.
 */
export function useFormPreviewBuild(options: UseFormPreviewBuildOptions) {
  const {
    functionUnitId, store, selectedForm, designerRef, subDesignerRefs, subFormCache,
    designerSubBindings, getActiveDesignerRef, getTableFieldDefinitions,
    getPrimaryBindingFieldDefinitions, refreshFormRulesFromTableMetadata,
    toSubTablePreviewColumns, makeLookupPreviewItem, mergePortalViewsForPreview,
    getTableName, t,
  } = options

  const showPreviewDialog = ref(false)
  const previewFormReady = ref(false)
  const previewDialogOption = ref<Record<string, unknown>>({})
  const previewData = ref({})
  const previewRule = ref<any[]>([])
  const previewSubBindings = ref<Array<{
    bindingId: number
    bindingType: string
    bindingMode: string
    tableName: string
    tableType: string
    tableDescription: string
    rule: any[]
    option?: any
    columns: any[]
    subMode?: string
  }>>([])
  const previewSubData = ref<Record<number, any>>({})
  const previewTableRows = ref<Record<number, any[]>>({})
  // Mixed preview items: alternating form-create rule segments and inline sub-tables
  const previewItems = ref<FormPreviewItem[]>([])

  const previewPrimaryTableDisplayName = computed(() => {
    const primary = selectedForm.value?.tableBindings?.find((b: TableBinding) => b.bindingType === 'PRIMARY')
    if (!primary) return ''
    const table = store.tables.find(t => t.id === primary.tableId)
    return table?.tableDisplayName || table?.tableName || ''
  })

  const previewPrimaryTableId = computed(() => {
    const primary = selectedForm.value?.tableBindings?.find((b: TableBinding) => b.bindingType === 'PRIMARY')
    return primary?.tableId ?? null
  })

  const previewParentTablesById = computed(() => {
    const out: Record<number, { fieldDefinitions: FieldDefinition[] }> = {}
    for (const b of selectedForm.value?.tableBindings ?? []) {
      if (b.bindingType !== 'PRIMARY' && b.bindingType !== 'SUB') continue
      const table = store.tables.find(t => t.id === b.tableId)
      if (table?.fieldDefinitions?.length) {
        out[b.tableId] = { fieldDefinitions: table.fieldDefinitions }
      }
    }
    return out
  })

  const previewTableBindingsForContext = computed(() =>
    (selectedForm.value?.tableBindings ?? []).map((b: TableBinding) => ({
      tableId: b.tableId,
      bindingType: b.bindingType,
    })),
  )

  // Preview option: mutable flags + form-create English strings (library defaults to zh-cn without `locale` / `language`)
  const previewOptionState = reactive({
    submitBtn: false as boolean | Record<string, unknown>,
    resetBtn: false,
  })
  const previewOption = computed(() => ({
    ...previewOptionState,
    language: {
      en: {
        clickToUpload: t('form.clickToUpload'),
      },
    },
  }))

  const getPreviewOption = (): Record<string, any> => ({
    submitBtn: {
      show: true,
      innerText: t('common.validate'),
    },
    resetBtn: false,
    language: {
      en: {
        clickToUpload: t('form.clickToUpload'),
      },
    },
  })

  function prepareCustomPreviewValidation() {
    prepareDesignerPreviewValidation(getActiveDesignerRef(), t('common.validate'))
  }

  async function handlePreview() {
    console.log('[DEBUG] ==================== handlePreview START ====================')
    try {
      await store.fetchTables(functionUnitId)
    } catch (e) {
      console.warn('[FormDesigner] fetchTables before preview failed:', e)
    }
    refreshFormRulesFromTableMetadata()

    // Wrapper to catch errors during preview generation
    async function buildPreview() {
    if (!selectedForm.value) {
      console.log('[DEBUG] no selectedForm, returning early')
      return
    }
    // Always use live designer rule so unsaved reordering is reflected in preview.
    // Fall back to saved configJson rule only when the designer ref is unavailable.
    let rawRule: any[] = []
    try {
      prepareDesignerPreviewValidation(getActiveDesignerRef(), t('common.validate'))
      rawRule = (getActiveDesignerRef() as any)?.getRule?.() || designerRef.value?.getRule() || []
    } catch {}
    if (!rawRule.length) {
      rawRule = selectedForm.value.configJson?.rule || []
    }
    const primaryBinding = (selectedForm.value.tableBindings || []).find(
      (b: TableBinding) => b.bindingType === 'PRIMARY',
    )
    const primaryFields = primaryBinding ? getTableFieldDefinitions(primaryBinding.tableId) : []
    if (rawRule.length && primaryFields.length) {
      rawRule = syncFormRulesWithTableFields(rawRule, primaryFields) as any[]
    }
    previewData.value = {}
    previewSubData.value = {}
    previewTableRows.value = {}

    // Sync label position from designer option
    Object.assign(previewOptionState, {
      submitBtn: {
        show: true,
        innerText: t('common.validate'),
      },
      resetBtn: false,
    })

    const config = selectedForm.value.configJson || {}
    const subForms = config.subForms || {}
    const nonPrimary = (selectedForm.value.tableBindings || []).filter((b: TableBinding) => b.bindingType !== 'PRIMARY')

    // Build a map of bindingId -> binding info for quick lookup
    const bindingMap = new Map<number, any>()
    nonPrimary.forEach((b: TableBinding) => {
      const bindingId = b.id as number
      const index = designerSubBindings.value.findIndex(d => d.bindingId === bindingId)
      const subRef = subDesignerRefs.value[index]
      let rule: any[] = []
      let option: any = {}
      try {
        if (subRef) {
          rule = subRef.getRule() || []
          option = subRef.getOption() || {}
        } else if (subFormCache.value[bindingId]) {
          rule = subFormCache.value[bindingId].rule || []
          option = subFormCache.value[bindingId].options || {}
        } else {
          rule = subForms[bindingId]?.rule || []
          option = subForms[bindingId]?.options || {}
        }
      } catch {
        rule = subFormCache.value[bindingId]?.rule || subForms[bindingId]?.rule || []
        option = subFormCache.value[bindingId]?.options || subForms[bindingId]?.options || {}
      }
      const tableFields = (store.tables.find(t => t.id === b.tableId)?.fieldDefinitions) || []
      if (rule.length && tableFields.length) {
        rule = syncFormRulesWithTableFields(rule, tableFields) as any[]
      }
      rule = mapFormCreateRulesReadonlyDeep(rule) as any[]
      ensureFormCreateRulesValidationDeep(rule)
      const columns = toSubTablePreviewColumns(bindingId, rule, config)
      previewTableRows.value[bindingId] = []
      bindingMap.set(bindingId, {
        bindingId,
        bindingType: b.bindingType,
        bindingMode: b.bindingMode,
        tableName: getTableName(b.tableId, b.tableName),
        tableType: (store.tables.find(t => t.id === b.tableId)?.tableType) || (b.bindingType === 'RELATED' ? 'RELATION' : ''),
        tableDescription: (store.tables.find(t => t.id === b.tableId)?.description) || '',
        tableId: b.tableId,
        fieldDefinitions: (store.tables.find(t => t.id === b.tableId)?.fieldDefinitions) || [],
        bindingLinkMode: b.bindingLinkMode,
        bindingForeignKeyField: b.foreignKeyField,
        rule,
        option,
        columns,
        subMode: b.subMode,
      })
    })

    // ── Normalize custom types for form-create preview ──────────────────────────
    // form-create cannot pass nested options (with children) to custom components
    // correctly, so we convert them to native element-plus tags that form-create
    // renders via its built-in el-* passthrough.
    rawRule = rawRule.map((r: any) => {
      const rp = r.props || {}
      if (r.type === 'transfer') {
        return {
          ...r,
          type: 'el-transfer',
          props: {
            data: (rp.options ?? []).map((o: any) => ({ key: o.value, label: o.label })),
            titles: [rp.leftTitle || 'Source', rp.rightTitle || 'Target'],
            filterable: true,
          },
        }
      }
      if (r.type === 'cascader') {
        return {
          ...r,
          type: 'el-cascader',
          props: {
            options: rp.options ?? [],
            props: rp.cascaderProps || rp.props,
            placeholder: rp.placeholder || 'Please select',
            clearable: true,
          },
        }
      }
      // Strip prefix/suffix virtual nodes that form-create can't render in preview
      if (r.prefix || r.suffix) {
        const { prefix, suffix, ...rest } = r
        return rest
      }
      return r
    })

    rawRule = mapFormCreateRulesReadonlyDeep(rawRule) as any[]

    ensureFormCreateRulesValidationDeep(rawRule)
    try {
      designerRef.value?.setRule?.(rawRule)
    } catch {
      /* ignore designer setRule sync errors */
    }

    const tableFieldDefs = getPrimaryBindingFieldDefinitions()
    applyTableFieldDefaultsToRulesAndModel(rawRule, tableFieldDefs, previewData.value, true, {
      tableOverridesRule: true,
    })
    seedFormDataFromRules(rawRule, previewData.value, true)
    syncModelValuesOntoRules(rawRule, previewData.value)

    // form-create proprietary types that should not be rendered in preview
    const FC_SKIP_PREVIEW = new Set(['subForm', 'tableForm', 'tableFormColumn', 'group', 'el-row', 'el-col'])

    function containsSubTableRule(item: any): boolean {
      if (!item) return false
      if (item.type === 'subTable' && (item._bindingId ?? item.props?._bindingId) != null) return true
      return getRuleChildren(item).some(child => containsSubTableRule(child))
    }

    function buildPreviewItems(ruleItems: any[], localBindingMap: Map<number, any>, keyPrefix = 'seg'): FormPreviewItem[] {
      const items: FormPreviewItem[] = []
      let currentSegment: any[] = []
      let segmentIndex = 0

      function flushSegment() {
        if (currentSegment.length > 0) {
          items.push({ kind: 'fields', rule: [...currentSegment], modelKey: `${keyPrefix}_${segmentIndex++}` })
          currentSegment = []
        }
      }

      for (const ruleItem of ruleItems) {
        const itemBindingId = ruleItem._bindingId ?? ruleItem.props?._bindingId ?? null

        if (ruleItem.type === 'subTable' && itemBindingId != null) {
          if (isFormCreateRuleHidden(ruleItem)) {
            continue
          }
          flushSegment()
          const binding = localBindingMap.get(Number(itemBindingId))
          if (binding) {
            const mergedPv = mergePortalViewsForPreview(ruleItem, Number(itemBindingId))
            items.push({
              kind: 'subTable',
              binding: { ...binding, portalViews: mergedPv },
            })
            localBindingMap.delete(Number(itemBindingId))
          }
        } else if (isCardRule(ruleItem) && containsSubTableRule(ruleItem)) {
          flushSegment()
          items.push({
            kind: 'card',
            title: getLayoutLabel(ruleItem),
            items: buildPreviewItems(getRuleChildren(ruleItem), localBindingMap, `card_${segmentIndex++}`),
            modelKey: `${keyPrefix}_card_${segmentIndex}`,
          })
        } else if (ruleItem.type === 'lookup') {
          if (!isFormCreateRuleHidden(ruleItem)) {
            flushSegment()
            items.push(makeLookupPreviewItem(ruleItem, config))
          }
        } else if (FC_SKIP_PREVIEW.has(ruleItem.type)) {
          const layoutChildren = getRuleChildren(ruleItem)
          if (containsSubTableRule(ruleItem) || layoutChildren.length > 0) {
            flushSegment()
            items.push(...buildPreviewItems(layoutChildren, localBindingMap, `${keyPrefix}_layout_${segmentIndex++}`))
          }
        } else if (!isFormCreateRuleHidden(ruleItem)) {
          currentSegment.push(ruleItem)
        }
      }

      flushSegment()
      return items
    }

    const items = buildPreviewItems(rawRule, bindingMap)
    // Only SUB bindings that were explicitly placed via subTable component are shown;
    // unplaced bindings (no component in the form) are not rendered.

    previewItems.value = items
    for (const pi of previewItems.value) {
      if (pi.kind === 'fields') {
        syncModelValuesOntoRules(pi.rule, previewData.value)
      } else if (pi.kind === 'card') {
        for (const cardItem of pi.items) {
          if (cardItem.kind === 'fields') {
            syncModelValuesOntoRules(cardItem.rule, previewData.value)
          }
        }
      }
    }
    previewData.value = { ...previewData.value }
    applyPreviewDefaultsToItemRules(previewItems.value, previewData)
    materializePreviewItemsEvents(previewItems.value, previewData)
    const previewOpt = mergePreviewValidateFormOption(
      {
        ...getPreviewOption(),
        ...(config.options && typeof config.options === 'object' ? config.options : {}),
      },
      t('common.validate'),
    )
    previewOpt.onSubmit = () => {
      /* Preview-only: submit button triggers form-create validation; no data action. */
    }
    const savedOnChange = config.options?.onChange
    if (!isEmptyFormCreateHandler(savedOnChange)) {
      previewOpt.onChange = wrapFormLevelOnChangeForFormCreate(savedOnChange)
    }
    previewDialogOption.value = attachPreviewMountedDefaultSync(previewOpt, previewData)
    // Keep previewRule for backward compat (used by previewSubBindings logic elsewhere if any)
    previewRule.value = rawRule.filter(r => r.type !== 'subTable')
    previewSubBindings.value = [] // no longer used for bottom rendering

    previewFormReady.value = false
    showPreviewDialog.value = true
    await nextTick()
    previewFormReady.value = true
    console.log('[DEBUG] ==================== handlePreview END ====================')
    } // end of buildPreview function

    // Wrap the entire preview building in try-catch to handle circular dependency errors
    try {
      await buildPreview()
    } catch (e: any) {
      console.error('[FormDesigner] Preview build error:', e)
      // Try a simpler preview with just the basic rule
      try {
        const basicRule = (selectedForm.value?.configJson?.rule || []).filter((r: any) => r.type !== 'subTable')
        previewItems.value = [{ kind: 'fields', rule: basicRule, modelKey: 'fallback' }]
        previewSubBindings.value = []
        showPreviewDialog.value = true
      } catch (e2) {
        console.error('[FormDesigner] Fallback preview also failed:', e2)
        ElMessage.error(t('form.previewFailed'))
      }
    }
  }

  return {
    showPreviewDialog,
    previewFormReady,
    previewDialogOption,
    previewData,
    previewRule,
    previewSubBindings,
    previewSubData,
    previewTableRows,
    previewItems,
    previewPrimaryTableDisplayName,
    previewPrimaryTableId,
    previewParentTablesById,
    previewTableBindingsForContext,
    previewOption,
    getPreviewOption,
    prepareCustomPreviewValidation,
    handlePreview,
  }
}
