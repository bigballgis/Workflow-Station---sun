import { computed, nextTick, reactive, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FieldDefinition, FormDefinition, TableBinding } from '@/api/functionUnit'
import type { FormPreviewItem, RecordNotePreviewConfig } from '@/components/designer/formPreviewTypes'
import { getRuleChildren, isCardRule, getLayoutLabel, snapshotRulesForPreview, walkFormCreateRules, withSubTableBindingIdInProps } from '@/utils/formDesigner'
import {
  applyPreviewDefaultsToItemRules,
  attachPreviewMountedDefaultSync,
  materializePreviewItemsEvents,
  mergeComponentEventsFromSavedRules,
  preserveSerializedHandlersInShadowBuckets,
  sanitizePreviewItemsHandlers,
  sanitizePreviewRuleHandlers,
} from '@/utils/formCreatePreviewEvents'
import { ensureFormCreateRulesValidationDeep } from '@/utils/formCreateValidateRules'
import {
  flushDesignerPropsPanelToActiveRule,
  flushDesignerValidatePanelToActiveRule,
  mergePreviewValidateFormOption,
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
import { nestAssignmentFieldsIntoContainer, type AssignmentConfig } from '@/utils/miAssignmentConfig'
import {
  applyActionFormCanvasToPreview,
  resolveActionFormCanvasRule,
  selectPreviewCanvasTableBinding,
} from '@/utils/actionFormCanvasRule'

type DesignerLike = { getRule?: () => unknown[]; setRule?: (r: unknown[]) => void } | null | undefined

/** True when the sub-form design placed the Assignment Mode component itself. */
function hasAssignmentContainer(rules: unknown[]): boolean {
  let found = false
  walkFormCreateRules(rules, rule => {
    if (rule.type === 'miAssignment') found = true
  })
  return found
}

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
  toSubTablePreviewColumns: (bindingId: number, rule: any[], config: any) => any[]
  makeLookupPreviewItem: (ruleItem: any, config: any) => any
  getTableName: (tableId: number, fallback?: string) => string
  getAssignmentConfig: (tableId: number) => AssignmentConfig | undefined
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Form Preview dialog state and the preview build pipeline for FormDesigner.
 *
 * Preview is read-only: rules are snapshotted from getRule() and transformed in memory.
 * Never call designer setRule/setOption during preview — fc-designer full canvas redraws
 * were the primary cause of main-thread hangs on multi-sub-table forms (e.g. MCY).
 */
export function useFormPreviewBuild(options: UseFormPreviewBuildOptions) {
  const {
    functionUnitId, store, selectedForm, designerRef, subDesignerRefs, subFormCache,
    designerSubBindings, getActiveDesignerRef, getTableFieldDefinitions,
    getPrimaryBindingFieldDefinitions,
    toSubTablePreviewColumns, makeLookupPreviewItem,
    getTableName, getAssignmentConfig, t,
  } = options

  const showPreviewDialog = ref(false)
  const previewBuilding = ref(false)
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
    assignmentConfig?: AssignmentConfig
  }>>([])
  const previewSubData = ref<Record<number, any>>({})
  const previewTableRows = ref<Record<number, any[]>>({})
  // Mixed preview items: alternating form-create rule segments and inline sub-tables
  const previewItems = ref<FormPreviewItem[]>([])

  /** Live Preview may overlay unsaved ACTION canvas; handlePreview writes this. */
  const previewCanvasTableBindingOverride = ref<TableBinding | null>(null)

  const previewCanvasTableBinding = computed(() => {
    if (previewCanvasTableBindingOverride.value) return previewCanvasTableBindingOverride.value
    const form = selectedForm.value
    const resolved = resolveActionFormCanvasRule({
      formType: form?.formType,
      tableBindings: form?.tableBindings,
      topLevelRule: form?.configJson?.rule,
      subForms: form?.configJson?.subForms,
    })
    return selectPreviewCanvasTableBinding({
      tableBindings: form?.tableBindings ?? [],
      usedActionCanvas: resolved.usedActionCanvas,
      actionBindingId: resolved.actionBindingId,
    })
  })

  const previewPrimaryTableDisplayName = computed(() => {
    const canvas = previewCanvasTableBinding.value
    if (!canvas) return ''
    const table = store.tables.find(t => t.id === canvas.tableId)
    return table?.tableDisplayName || table?.tableName || ''
  })

  const previewPrimaryTableId = computed(() => previewCanvasTableBinding.value?.tableId ?? null)

  const previewParentTablesById = computed(() => {
    const out: Record<number, { fieldDefinitions: FieldDefinition[] }> = {}
    for (const b of selectedForm.value?.tableBindings ?? []) {
      if (b.bindingType !== 'PRIMARY' && b.bindingType !== 'SUB' && b.bindingType !== 'ACTION') continue
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
    // Built-in bottom submit/validate button hidden in preview (see mergePreviewValidateFormOption).
    submitBtn: false,
    resetBtn: false,
    language: {
      en: {
        clickToUpload: t('form.clickToUpload'),
      },
    },
  })

  function flushDesignerPanelsForPreview() {
    const ref = getActiveDesignerRef()
    flushDesignerValidatePanelToActiveRule(ref)
    flushDesignerPropsPanelToActiveRule(ref)
  }

  function prepareCustomPreviewValidation() {
    flushDesignerPanelsForPreview()
  }

  async function handlePreview() {
    if (!selectedForm.value) {
      ElMessage.warning(t('form.noFormContent'))
      return
    }

    previewBuilding.value = true
    previewFormReady.value = false
    previewItems.value = []
    showPreviewDialog.value = true
    await nextTick()

    const tablesPromise = store.fetchTables(functionUnitId).catch((e: unknown) => {
      console.warn('[FormDesigner] fetchTables before preview failed:', e)
    })

    // Wrapper to catch errors during preview generation
    async function buildPreview() {
    if (!selectedForm.value) {
      return
    }
    // Always use the MAIN designer's live rule so unsaved reordering is reflected in preview.
    // Preview renders the whole form (main + inline sub tables); sub designers' live rules are
    // collected per binding below. Reading the active SUB designer here would render the sub form
    // as the main form and later clobber the main canvas via setRule.
    // Fall back to saved configJson rule only when the designer ref is unavailable.
    let rawRule: any[] = []
    try {
      flushDesignerPanelsForPreview()
      rawRule = snapshotRulesForPreview(designerRef.value?.getRule() || [])
    } catch (e) {
      // FALLBACK(ux): read-only preview — designer not ready falls through to the saved
      // configJson rule below. Log so a systematic designer failure stays discoverable.
      console.warn('[FormDesigner] live rule read failed for preview; using saved config', e)
    }
    if (!rawRule.length) {
      rawRule = snapshotRulesForPreview(selectedForm.value.configJson?.rule || [])
    } else {
      const savedRule = snapshotRulesForPreview(selectedForm.value.configJson?.rule || [])
      mergeComponentEventsFromSavedRules(rawRule, savedRule)
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
      // Built-in bottom submit/validate button hidden in preview.
      submitBtn: false,
      resetBtn: false,
    })

    const config = selectedForm.value.configJson || {}
    const subForms = config.subForms || {}
    const nonPrimary = (selectedForm.value.tableBindings || []).filter((b: TableBinding) => b.bindingType !== 'PRIMARY')

    // Build a map of bindingId -> binding info for quick lookup
    const bindingMap = new Map<number, any>()
    for (const b of nonPrimary) {
      const bindingId = b.id as number
      const index = designerSubBindings.value.findIndex(d => d.bindingId === bindingId)
      const subRef = subDesignerRefs.value[index]
      let rule: any[] = []
      let option: any = {}
      try {
        if (subRef) {
          const liveRule = subRef.getRule?.()
          if (liveRule == null) {
            rule = snapshotRulesForPreview(subFormCache.value[bindingId]?.rule || subForms[bindingId]?.rule || [])
            option = subFormCache.value[bindingId]?.options || subForms[bindingId]?.options || {}
          } else {
            rule = snapshotRulesForPreview(liveRule)
            option = subRef.getOption() || {}
          }
        } else if (subFormCache.value[bindingId]) {
          rule = snapshotRulesForPreview(subFormCache.value[bindingId].rule || [])
          option = subFormCache.value[bindingId].options || {}
        } else {
          rule = snapshotRulesForPreview(subForms[bindingId]?.rule || [])
          option = subForms[bindingId]?.options || {}
        }
      } catch (e) {
        // FALLBACK(ux): read-only preview — live sub-designer read failed, fall through the
        // cache -> saved chain (same shape as useFormSave's collection, but nothing persists here).
        console.warn(`[FormDesigner] live sub form read failed for preview (binding ${bindingId}); using cache/saved`, e)
        rule = snapshotRulesForPreview(subFormCache.value[bindingId]?.rule || subForms[bindingId]?.rule || [])
        option = subFormCache.value[bindingId]?.options || subForms[bindingId]?.options || {}
      }
      const tableFields = (store.tables.find(t => t.id === b.tableId)?.fieldDefinitions) || []
      if (rule.length && tableFields.length) {
        rule = syncFormRulesWithTableFields(rule, tableFields) as any[]
      }
      // Fold assignee / BU / role into the Assignment Mode container so preview shows
      // the same single nested unit the designer and runtime do. Sub-forms saved before
      // the container existed keep them as siblings; this is their migration on read.
      rule = nestAssignmentFieldsIntoContainer(rule, getAssignmentConfig(b.tableId)) as any[]
      rule = mapFormCreateRulesReadonlyDeep(rule) as any[]
      ensureFormCreateRulesValidationDeep(rule)
      // Keep $FNX strings in `_on`/`_hook` before sanitize strips them from `on`/`hook`.
      preserveSerializedHandlersInShadowBuckets(rule)
      // Strip designer `$FNX:` handler strings from on/hook so the sub-form row dialog's
      // base form-create instance doesn't crash on them (same freeze guard as the main form).
      sanitizePreviewRuleHandlers(rule)
      // Saved/cached rules keep _bindingId only at top level — restore props._bindingId so
      // nested subTable placeholders in preview form-create resolve their binding.
      rule = withSubTableBindingIdInProps(rule)
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
        // Only a design that actually placed the Assignment Mode component gets the
        // block. The BPMN contract supplies its content, not its existence — passing
        // the config unconditionally made every MI-configured sub-table render an
        // assignment block the author never put on the canvas.
        assignmentConfig: hasAssignmentContainer(rule) ? getAssignmentConfig(b.tableId) : undefined,
        rule,
        option,
        columns,
        subMode: b.subMode,
      })
    }

    // ACTION forms author fields on the ACTION binding canvas (Portal FORM_POPUP parity).
    const actionCanvas = applyActionFormCanvasToPreview({
      formType: selectedForm.value.formType,
      tableBindings: selectedForm.value.tableBindings,
      topLevelRule: rawRule,
      savedSubForms: subForms,
      bindingMap,
      primaryFieldDefs: getPrimaryBindingFieldDefinitions(),
      getTableFieldDefinitions,
    })
    rawRule = snapshotRulesForPreview(actionCanvas.rule)
    const canvasFieldDefs = actionCanvas.fieldDefs
    previewCanvasTableBindingOverride.value = selectPreviewCanvasTableBinding({
      tableBindings: selectedForm.value.tableBindings ?? [],
      usedActionCanvas: actionCanvas.usedActionCanvas,
      actionBindingId: actionCanvas.actionBindingId,
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

    applyTableFieldDefaultsToRulesAndModel(rawRule, canvasFieldDefs, previewData.value, true, {
      tableOverridesRule: true,
    })
    seedFormDataFromRules(rawRule, previewData.value, true)
    syncModelValuesOntoRules(rawRule, previewData.value)

    // form-create proprietary types that should not be rendered in preview
    const FC_SKIP_PREVIEW = new Set(['subForm', 'tableForm', 'tableFormColumn', 'group', 'el-row', 'el-col'])

    // Both rule types bind a SUB table and get their own preview item kind.
    const isSubBindingRuleType = (type: unknown) => type === 'subTable' || type === 'inlineSubForm'

    function containsSubTableRule(item: any): boolean {
      let found = false
      walkFormCreateRules([item], (rule) => {
        if (found) return
        if (isSubBindingRuleType(rule.type) && (rule._bindingId ?? (rule.props as Record<string, unknown> | undefined)?._bindingId) != null) {
          found = true
        }
      })
      return found
    }

    function buildPreviewItems(
      ruleItems: any[],
      localBindingMap: Map<number, any>,
      keyPrefix = 'seg',
      seen: WeakSet<object> = new WeakSet<object>(),
    ): FormPreviewItem[] {
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
        if (ruleItem && typeof ruleItem === 'object') {
          if (seen.has(ruleItem)) continue
          seen.add(ruleItem)
        }

        const itemBindingId = ruleItem._bindingId ?? ruleItem.props?._bindingId ?? null

        if (ruleItem.type === 'subTable' && itemBindingId != null) {
          if (isFormCreateRuleHidden(ruleItem)) {
            continue
          }
          flushSegment()
          const binding = localBindingMap.get(Number(itemBindingId))
          if (binding) {
            // 逐操作权限来自放置组件 rule.props（仅显式 false 下发，undefined 由 SubTableField 回退 editable）
            const placedProps = (ruleItem.props ?? {}) as Record<string, unknown>
            items.push({
              kind: 'subTable',
              binding: {
                ...binding,
                // Summary presentation designed on the canvas.
                compactCells: placedProps.compactCells === true ? true : undefined,
                allowAdd: placedProps.allowAdd === false ? false : undefined,
                allowEdit: placedProps.allowEdit === false ? false : undefined,
                allowDelete: placedProps.allowDelete === false ? false : undefined,
              },
              sourceRule: ruleItem as Record<string, unknown>,
            })
            localBindingMap.delete(Number(itemBindingId))
          }
        } else if (ruleItem.type === 'inlineSubForm' && itemBindingId != null) {
          // Inline Form: render the bound sub-form's rule in place — no grid, no Add button.
          if (isFormCreateRuleHidden(ruleItem)) {
            continue
          }
          flushSegment()
          // Resolve against the FULL map, not localBindingMap: the subTable branch deletes a
          // binding once it has rendered a grid for it, and the same binding may legitimately
          // be shown both as a grid and inline (or the grid may simply appear earlier in the
          // rule). Reading the pruned map made the inline block silently vanish in that case.
          const binding = fullBindingMap.get(Number(itemBindingId))
          if (binding) {
            items.push({
              kind: 'inlineSubForm',
              binding,
              modelKey: `${keyPrefix}_inline_${itemBindingId}`,
              sourceRule: ruleItem as Record<string, unknown>,
            })
            // Claim it so an unplaced-binding pass cannot append a duplicate standalone table.
            localBindingMap.delete(Number(itemBindingId))
          }
        } else if (isCardRule(ruleItem)) {
          // Extract every card so lookups inside cards use FormPreviewItems cascade
          // (same path as main-form kind:'lookup'), not form-create LookupComponent alone.
          flushSegment()
          items.push({
            kind: 'card',
            title: getLayoutLabel(ruleItem),
            items: buildPreviewItems(
              getRuleChildren(ruleItem),
              localBindingMap,
              `card_${segmentIndex++}`,
              new WeakSet<object>(),
            ),
            modelKey: `${keyPrefix}_card_${segmentIndex}`,
          })
        } else if (ruleItem.type === 'lookup') {
          if (!isFormCreateRuleHidden(ruleItem)) {
            flushSegment()
            items.push(makeLookupPreviewItem(ruleItem, config))
          }
        } else if (ruleItem.type === 'recordNote') {
          // Keep it out of the form-create segment: inside one, type 'recordNote' resolves to
          // the designer canvas placeholder (dashed "active after deploy" box) instead of the
          // portal-shaped Notes panel Preview is supposed to show.
          if (!isFormCreateRuleHidden(ruleItem)) {
            flushSegment()
            items.push({
              kind: 'recordNote',
              config: (ruleItem.props ?? {}) as RecordNotePreviewConfig,
              modelKey: `${keyPrefix}_note_${segmentIndex++}`,
            })
          }
        } else if (FC_SKIP_PREVIEW.has(ruleItem.type)) {
          const layoutChildren = getRuleChildren(ruleItem)
          if (containsSubTableRule(ruleItem) || layoutChildren.length > 0) {
            flushSegment()
            items.push(
              ...buildPreviewItems(
                layoutChildren,
                localBindingMap,
                `${keyPrefix}_layout_${segmentIndex++}`,
                seen,
              ),
            )
          }
        } else if (!isFormCreateRuleHidden(ruleItem)) {
          currentSegment.push(ruleItem)
        }
      }

      flushSegment()
      return items
    }

    // Snapshot before the walk prunes it — Inline Form resolves against the full set
    // (buildPreviewItems deletes each binding as it claims one for a grid).
    const fullBindingMap = new Map(bindingMap)
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
    // Guard: the base form-create preview instance can't run designer `$FNX:` handler strings.
    // Any non-function left on rule.on/rule.hook would throw "w is not a function" every render
    // tick and freeze the preview. Runs last so real handlers installed above are kept.
    sanitizePreviewItemsHandlers(previewItems.value)
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

    await nextTick()
    previewFormReady.value = true
    } // end of buildPreview function

    // Wrap the entire preview building in try-catch to handle circular dependency errors
    try {
      await tablesPromise
      await buildPreview()
    } catch (e: unknown) {
      console.error('[FormDesigner] Preview build error:', e)
      // Try a simpler preview with just the basic rule
      try {
        const fallbackCanvas = applyActionFormCanvasToPreview({
          formType: selectedForm.value?.formType,
          tableBindings: selectedForm.value?.tableBindings,
          topLevelRule: selectedForm.value?.configJson?.rule || [],
          savedSubForms: selectedForm.value?.configJson?.subForms || {},
          bindingMap: new Map(),
          primaryFieldDefs: [],
        })
        previewCanvasTableBindingOverride.value = selectPreviewCanvasTableBinding({
          tableBindings: selectedForm.value?.tableBindings ?? [],
          usedActionCanvas: fallbackCanvas.usedActionCanvas,
          actionBindingId: fallbackCanvas.actionBindingId,
        })
        const basicRule = fallbackCanvas.rule.filter((r: { type?: string }) => r.type !== 'subTable')
        previewItems.value = [{ kind: 'fields', rule: basicRule, modelKey: 'fallback' }]
        previewSubBindings.value = []
        previewFormReady.value = true
      } catch (e2) {
        console.error('[FormDesigner] Fallback preview also failed:', e2)
        showPreviewDialog.value = false
        ElMessage.error(t('form.previewFailed'))
      }
    } finally {
      previewBuilding.value = false
    }
  }

  return {
    showPreviewDialog,
    previewBuilding,
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
