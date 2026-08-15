import { computed, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { resolveRelationViewEntry } from '@/utils/formConfigBindingResolve'
import { functionUnitApi } from '@/api/functionUnit'
import type { FormDefinition } from '@/api/functionUnit'
import type { SubTableFieldDTO } from '@/api/subTableView'
import { collectSubTableRules, collectRecordNoteScopes } from '@/utils/formDesigner'
import { normalizeBindingId } from '@/utils/bindingDisplayHelpers'
import {
  prepareFormCreateRulesForPersist,
  serializeFormCreateOptionsForPersist,
} from '@/utils/formCreateDefaultEvents'
import { ensureFormCreateRulesValidationDeep } from '@/utils/formCreateValidateRules'
import {
  commitDesignerPanelEditsBeforePreview,
  flushDesignerValidatePanelToActiveRule,
} from '@/utils/formDesignerPreviewValidation'
import { walkRulesApplyTableFieldDefaultsToPersistedRules } from '@/utils/formCreateRuleDefaults'
import { stripFormCreateRulesDisabledDeep } from '@/utils/formCreateRuleUtils'
import { isRequestIdRule } from '@/utils/formFieldMeta'
import { TABLE_AUDIT_FIELD_NAMES } from '@/utils/tableAuditFields'
import type { SubTableListColumnDTO } from './useSubTableViews'
import type { PortalViewsValue } from './useSubTablePortalViews'
import type { BlockingProgressApi } from '@/composables/useBlockingProgress'
import {
  parseMiAssignmentsFromBpmn,
  validateMiAssignmentComponents,
} from '@/utils/miAssignmentConfig'

type DesignerLike = { getRule?: () => unknown[]; setRule?: (r: unknown[]) => void } | null | undefined

interface UseFormSaveOptions {
  functionUnitId: number
  store: { updateForm: (functionUnitId: number, formId: number, payload: Record<string, any>) => Promise<any> }
  selectedForm: Ref<FormDefinition | null>
  designerRef: Ref<any>
  subDesignerRefs: Ref<any[]>
  designerSubBindings: ComputedRef<Array<{
    bindingId: number
    bindingType: string
    assignmentTableName: string
  }>>
  subFormCache: Ref<Record<number, { rule: any[]; options: any }>>
  relationViewState: Ref<Record<number, { allFields: any[]; viewFields: any[] }>>
  subTableViewState: Ref<Record<number, { allFields: SubTableFieldDTO[]; viewFields: SubTableListColumnDTO[] }>>
  subTableListViewRefs: Ref<Record<number, any>>
  subTablePortalViewsState: Ref<Record<number, PortalViewsValue>>
  getActiveDesignerRef: () => DesignerLike
  getPrimaryBindingFieldDefinitions: () => FieldDefinition[]
  syncSubTableListViewFromFormRules: (bindingId: number, rule: any[]) => void
  loadForms: () => Promise<void>
  autoSaving: Ref<boolean>
  lastAutoSaveTime: Ref<Date | null>
  /** When set, Save provisions missing tables/bindings for cross-FU pasted rules before updateForm. */
  provisionAndRepairForSave?: (nextConfig: Record<string, unknown>) => Promise<Record<string, unknown> | null>
  /** Predict whether Save will create tables (drives blocking progress copy). */
  willProvisionOnSave?: (nextConfig: Record<string, unknown>) => boolean
  /** Manual Save only: top-bar + fullscreen lock while provisioning / persisting. */
  blockingProgress?: BlockingProgressApi
  getBpmnXml: () => string | undefined
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Form persistence for FormDesigner: collects main/sub designer rules,
 * relation/list view state and portalViews into configJson and saves; also
 * owns Data_Table field-name validation and TASK field permission editing.
 */
export function useFormSave(options: UseFormSaveOptions) {
  const {
    functionUnitId, store, selectedForm, designerRef, subDesignerRefs, designerSubBindings,
    subFormCache, relationViewState, subTableViewState, subTableListViewRefs,
    subTablePortalViewsState, getActiveDesignerRef, getPrimaryBindingFieldDefinitions,
    loadForms, autoSaving, lastAutoSaveTime,
    provisionAndRepairForSave, willProvisionOnSave, blockingProgress, getBpmnXml, t,
  } = options

  const savingForm = ref(false)

  // Data_Table columns for field name autocomplete/validation
  const dataTableColumns = ref<string[]>([])

  /** Whether the user has been notified of an auto-save failure (reset on success) —
   *  failures must be visible, but not re-toasted every 30s. */
  const autoSaveFailureNotified = ref(false)

  /** Load Data_Table columns for field name autocomplete */
  async function loadDataTableColumns() {
    try {
      const res = await functionUnitApi.getDataTableColumns(functionUnitId)
      dataTableColumns.value = res?.data || []
    } catch {
      // FALLBACK(external): column names only feed autocomplete and best-effort validation
      // (the backend does not enforce it). A failed load merely drops a hint layer and cannot
      // corrupt data; validateFieldNames skips itself when the list is empty.
      dataTableColumns.value = []
    }
  }

  /**
   * Standard audit fields auto-appended to every new table by TableDesignComponentImpl.
   * Always valid in form rules even before the table backfill runs.
   * EXACT four names — semantics must stay in sync with backend platform-common
   * SystemAuditFields and portal frontend subTableAddDialogHelpers/rowInit.ts;
   * any change to the matching rule must update all three places.
   */
  const ALWAYS_VALID_FIELDS = new Set([...TABLE_AUDIT_FIELD_NAMES, '__request_id'])

  /** Validate field names against Data_Table columns */
  function validateFieldNames(fieldNames: string[]): string[] {
    if (dataTableColumns.value.length === 0) return []
    return fieldNames.filter(name => !ALWAYS_VALID_FIELDS.has(name) && !dataTableColumns.value.includes(name))
  }

  /** Get current form fields from the designer for field permission config */
  const currentFormFields = computed(() => {
    if (!designerRef.value || !selectedForm.value) return []
    try {
      const rule = designerRef.value.getRule() || []
      return rule
        .filter((r: any) => r.field && r.type !== 'subTable')
        .map((r: any) => ({ field: r.field, title: r.title || r.field }))
    } catch {
      // FALLBACK(ux): read-only display (field list for the permission panel);
      // fall back to the saved config when the designer is not ready yet.
      const rule = selectedForm.value.configJson?.rule || []
      return rule
        .filter((r: any) => r.field && r.type !== 'subTable')
        .map((r: any) => ({ field: r.field, title: r.title || r.field }))
    }
  })

  /** Get field permission value */
  function getFieldPermission(fieldName: string): string {
    return selectedForm.value?.fieldPermissions?.[fieldName] || 'EDITABLE'
  }

  /** Set field permission value */
  function setFieldPermission(fieldName: string, value: string) {
    if (!selectedForm.value) return
    if (!selectedForm.value.fieldPermissions) {
      selectedForm.value.fieldPermissions = {}
    }
    selectedForm.value.fieldPermissions[fieldName] = value
  }

  async function handleSaveForm(isManual = false) {
    if (!selectedForm.value || !designerRef.value) return

    // The form this save is for. If the user switches to another form while this save is
    // in flight (awaits below yield), the canvas/collected state belongs to THIS form —
    // persisting it under the newly selected form would clobber that form's design with
    // another table's fields. Verified again right before updateForm.
    const targetFormId = selectedForm.value.id

    if (!isManual) {
      autoSaving.value = true
    }

    try {
      commitDesignerPanelEditsBeforePreview()
      flushDesignerValidatePanelToActiveRule(getActiveDesignerRef())
      Object.values(subDesignerRefs.value).forEach((subRef) => {
        if (subRef) flushDesignerValidatePanelToActiveRule(subRef as Parameters<typeof flushDesignerValidatePanelToActiveRule>[0])
      })

      // getRule() returning null/undefined means the designer is not ready (distinct from a
      // legitimately emptied canvas, which returns []). Saving anyway would persist an empty
      // rule and wipe the whole form design — and auto-save fires on a timer, so the damage
      // would be silent. Abort instead.
      const rawRule = designerRef.value.getRule()
      if (rawRule == null) {
        console.error('[FormDesigner] getRule() returned null/undefined; aborting save to protect persisted form design')
        if (isManual) ElMessage.error(t('form.saveFailed'))
        return
      }
      const rule = stripFormCreateRulesDisabledDeep(rawRule) as any[]
      ensureFormCreateRulesValidationDeep(rule)
      walkRulesApplyTableFieldDefaultsToPersistedRules(rule, getPrimaryBindingFieldDefinitions())
      // prepare flattens `_on`→`on` and drops empty `$FNX:` stubs. Do NOT call
      // walkRulesEnsureComponentEvents here: with leftover `_fc_id` it treats the
      // persist snapshot as a live canvas rule, moves handlers back to `_on`, and
      // Event panel / reload then look empty after Save.
      prepareFormCreateRulesForPersist(rule)
      const options = serializeFormCreateOptionsForPersist(
        designerRef.value.getOption() as Record<string, unknown>,
      )

      const subTableRules = collectSubTableRules(rule)

      // Validate: all subTable placeholders must have a _bindingId selected
      const invalidPlaceholders = subTableRules.filter((r: any) => !r._bindingId)
      if (invalidPlaceholders.length > 0) {
        if (isManual) ElMessage.error(t('form.subTableBindingRequired'))
        return
      }

      // SUB-type check runs AFTER provisionAndRepairForSave: cross-FU paste may carry
      // stale _bindingId that only become valid SUB bindings once missing tables are created.

      // RecordNote: at most one component per scope (TABLE / RECORD) on a form
      const recordNoteScopes = collectRecordNoteScopes(rule)
      if (recordNoteScopes.some((scope, idx) => recordNoteScopes.indexOf(scope) !== idx)) {
        if (isManual) ElMessage.error(t('form.recordNoteDuplicateScope'))
        return
      }
      // RecordNote: Single Record scope is sub-table-form only — the main canvas
      // must stay whole-table (Relation Table tabs have no form-design canvas at
      // all). Backstop for configs that bypassed the disabled panel option.
      if (recordNoteScopes.includes('RECORD')) {
        if (isManual) ElMessage.error(t('form.recordNoteRecordScopeMainForm'))
        return
      }

      // Validate field names against Data_Table columns (for PROCESS and TASK forms).
      // Refresh column list first — the table may have been created after the designer mounted.
      // Validation is best-effort; the backend does not enforce this check, so a stale
      // dataTableColumns response must not block the user from saving.
      if (selectedForm.value.formType === 'PROCESS' || selectedForm.value.formType === 'TASK') {
        await loadDataTableColumns()
        const fieldNames = rule
          .filter((r: any) => r.field && r.type !== 'subTable' && !isRequestIdRule(r))
          .map((r: any) => r.field as string)
        const invalidFields = validateFieldNames(fieldNames)
        if (invalidFields.length > 0) {
          console.warn('[FormDesigner] Field names not in Data_Table columns:', invalidFields)
        }
      }

      // Collect sub form rules — prefer live ref, then cache, then previously saved.
      // A failed collection must NEVER drop the binding from subForms entirely (that would
      // erase the sub-form design on save); always fall through live -> cache -> saved config.
      // Seed from configJson first so paste/repair payloads keep subForms when the target FU
      // still has no designerSubBindings tabs (empty binding list would otherwise drop them).
      const subForms: Record<number, { rule: any[]; options: any; miAssignmentAdopted?: boolean }> = {}
      const savedSubForms = selectedForm.value.configJson?.subForms || {}
      for (const [key, existing] of Object.entries(savedSubForms)) {
        const bindingId = Number(key)
        if (!Number.isFinite(bindingId) || !existing) continue
        const existingRule = stripFormCreateRulesDisabledDeep((existing as { rule?: unknown[] }).rule || []) as any[]
        prepareFormCreateRulesForPersist(existingRule)
        subForms[bindingId] = {
          rule: existingRule,
          options: serializeFormCreateOptionsForPersist(
            (existing as { options?: Record<string, unknown> }).options,
          ),
          miAssignmentAdopted: (existing as { miAssignmentAdopted?: boolean }).miAssignmentAdopted === true,
        }
      }
      designerSubBindings.value.forEach((binding, index) => {
        const subRef = subDesignerRefs.value[index]
        let collected: { rule: any[]; options: any; miAssignmentAdopted?: boolean } | null = null
        if (subRef) {
          // Tab is currently active and mounted
          try {
            flushDesignerValidatePanelToActiveRule(subRef as Parameters<typeof flushDesignerValidatePanelToActiveRule>[0])
            const rawSubRule = subRef.getRule()
            if (rawSubRule == null) {
              // Designer not ready (distinct from a legitimately empty canvas []) —
              // treat as a failed collection and use the fallback chain.
              console.error(`[FormDesigner] sub designer getRule() null for binding ${binding.bindingId}; falling back to cache/saved`)
            } else {
              const liveRule = stripFormCreateRulesDisabledDeep(rawSubRule) as any[]
              ensureFormCreateRulesValidationDeep(liveRule)
              prepareFormCreateRulesForPersist(liveRule)
              const liveOptions = serializeFormCreateOptionsForPersist(
                subRef.getOption() as Record<string, unknown>,
              )
              // The canvas was hydrated through buildEffectiveSubFormConfig this session
              // (loadSubDesigners/handleTabChange), so its one-time Assignment Mode
              // auto-adoption pass has already run — persist that so a deliberate
              // deletion afterwards is never silently re-created on the next load.
              collected = { rule: liveRule, options: liveOptions, miAssignmentAdopted: true }
              // Also update cache
              subFormCache.value[binding.bindingId] = collected
            }
          } catch (e) {
            console.error(`[FormDesigner] collecting live sub form failed for binding ${binding.bindingId}; falling back to cache/saved`, e)
          }
        }
        if (!collected && subFormCache.value[binding.bindingId]) {
          // Tab was visited but is now unmounted (or live collection failed) — use cache.
          // The cache was itself populated from a hydrated canvas — same reasoning as above.
          const cached = subFormCache.value[binding.bindingId]
          const cachedRule = stripFormCreateRulesDisabledDeep(cached.rule || []) as any[]
          prepareFormCreateRulesForPersist(cachedRule)
          collected = {
            rule: cachedRule,
            options: serializeFormCreateOptionsForPersist(cached.options),
            miAssignmentAdopted: true,
          }
        }
        if (!collected) {
          // Tab never visited this session — preserve previously saved data AS-IS,
          // including whatever adoption flag it already carried.
          const existing = (selectedForm.value!.configJson?.subForms || {})[binding.bindingId]
          if (existing) {
            const existingRule = stripFormCreateRulesDisabledDeep(existing.rule || []) as any[]
            prepareFormCreateRulesForPersist(existingRule)
            collected = {
              rule: existingRule,
              options: serializeFormCreateOptionsForPersist(existing.options),
              miAssignmentAdopted: (existing as { miAssignmentAdopted?: boolean }).miAssignmentAdopted === true,
            }
          }
        }
        if (collected) {
          subForms[binding.bindingId] = collected
        }
      })

      // Collect relation table view fields
      const relationViews: Record<number, { viewFields: any[]; allFields: any[] }> = {}
      designerSubBindings.value.forEach((binding) => {
        if (binding.bindingType === 'RELATED') {
          const state = relationViewState.value[binding.bindingId]
          if (state && (state.viewFields.length > 0 || state.allFields.length > 0)) {
            relationViews[binding.bindingId] = state
          } else {
            // Preserve previously saved data
            const existing = resolveRelationViewEntry(
              selectedForm.value!.configJson?.relationViews,
              binding.bindingId,
              selectedForm.value!.tableBindings ?? [],
            ) ?? (selectedForm.value!.configJson?.relationViews || {})[binding.bindingId]
            if (existing) relationViews[binding.bindingId] = existing
          }
        }
      })

      // Collect sub-table list view columns, including dropped Link Form columns.
      const subListViews: Record<number, { columns: SubTableListColumnDTO[] }> = {
        ...(selectedForm.value.configJson?.subListViews || {})
      }
      designerSubBindings.value.forEach((binding) => {
        if (binding.bindingType !== 'SUB') return
        const listRef = subTableListViewRefs.value[binding.bindingId]
        if (listRef) {
          const columns = listRef.getListColumns?.() || listRef.getViewFields?.() || []
          const state = subTableViewState.value[binding.bindingId]
          const existing = (selectedForm.value!.configJson?.subListViews || {})[binding.bindingId]
          const existingColumns = Array.isArray(existing?.columns) ? existing.columns : []
          // Only treat list state as "ready" when we have columns in memory. allFields alone is not enough:
          // after a bad merge, viewFields can be empty while allFields is populated — saving would otherwise
          // persist { columns: [] } and wipe configJson.subListViews.
          const stateLoaded = !!state && (state.viewFields?.length || 0) > 0
          if (columns.length === 0 && existingColumns.length > 0 && !stateLoaded) {
            // The list-view tab can mount before its async config load finishes; preserve saved columns.
            subListViews[binding.bindingId] = existing
          } else {
            subListViews[binding.bindingId] = { columns }
            const nextState = state || { allFields: [], viewFields: [] }
            subTableViewState.value[binding.bindingId] = {
              ...nextState,
              viewFields: columns
            }
          }
        } else {
          const state = subTableViewState.value[binding.bindingId]
          if (state?.viewFields?.length) {
            subListViews[binding.bindingId] = { columns: state.viewFields }
          } else {
            const existing = (selectedForm.value!.configJson?.subListViews || {})[binding.bindingId]
            if (existing) subListViews[binding.bindingId] = existing
          }
        }
      })

      // Collect per-binding portalViews — start from previously saved config so untouched
      // bindings keep their settings, then overlay anything the designer edited in this session.
      const subTablePortalViews: Record<number, PortalViewsValue> = {
        ...(selectedForm.value.configJson?.subTablePortalViews || {}),
        ...subTablePortalViewsState.value
      }

      if (selectedForm.value?.id !== targetFormId) {
        console.warn(`[FormDesigner] form switched (${targetFormId} -> ${selectedForm.value?.id ?? 'none'}) while collecting save payload; aborting to avoid saving one table's fields onto another form`)
        return
      }

      let nextConfig: Record<string, unknown> = { rule, options, subForms, relationViews, subListViews, subTablePortalViews }

      // Manual Save: full-screen lock when provisioning tables (slow); always mark savingForm.
      let blockingOpened = false
      if (isManual) {
        savingForm.value = true
        const needsProvision = !!provisionAndRepairForSave
          && (willProvisionOnSave?.(nextConfig) === true)
        if (needsProvision && blockingProgress) {
          blockingProgress.open(
            t('form.saveBlockingProvisioning'),
            t('form.saveBlockingProvisioningHint'),
          )
          blockingOpened = true
        }
      }

      try {
        // Manual Save only: provision missing tables + remap. Auto-save must not create tables.
        if (isManual && provisionAndRepairForSave) {
          try {
            const repaired = await provisionAndRepairForSave(nextConfig)
            if (repaired && typeof repaired === 'object') {
              nextConfig = repaired
            }
          } catch (e: unknown) {
            console.error('[FormDesigner] provision/repair before save failed', e)
            ElMessage.error(t('form.pasteConfigFailed'))
            return
          }
        }

        // 子表占位符必须绑定 SUB 类型表绑定（流程/任务表单下一主多子）— after provision so
        // newly created SUB bindings and remapped _bindingId are visible in designerSubBindings.
        if (selectedForm.value.formType === 'PROCESS' || selectedForm.value.formType === 'TASK') {
          const ruleAfter = Array.isArray(nextConfig.rule) ? nextConfig.rule as any[] : rule
          const bindingMap = new Map<number, string>()
          for (const b of designerSubBindings.value) {
            const id = normalizeBindingId(b.bindingId)
            if (id != null) bindingMap.set(id, b.bindingType)
          }
          for (const st of collectSubTableRules(ruleAfter)) {
            const bindingId = normalizeBindingId(st._bindingId)
            if (bindingId == null) continue
            const bindingType = bindingMap.get(bindingId)
            if (!bindingType || bindingType !== 'SUB') {
              if (isManual) ElMessage.error(t('form.subTableOnlySubBinding'))
              return
            }
          }
        }

        // Whether a form carries the Assignment Mode component is the developer's own
        // call — this only blocks a genuine BPMN misconfiguration (different nodes
        // disagreeing on the assignment contract for the same sub-table).
        const miGuard = validateMiAssignmentComponents(
          parseMiAssignmentsFromBpmn(getBpmnXml()),
          designerSubBindings.value
            .filter((binding) => binding.bindingType === 'SUB')
            .map((binding) => ({
              bindingId: binding.bindingId,
              tableName: binding.assignmentTableName,
            })),
          nextConfig,
        )
        if (miGuard.blocking.length > 0) {
          const issue = miGuard.blocking[0]
          ElMessage.error(t('form.miAssignmentConflict', {
            subTable: issue.subTableName,
            nodes: issue.nodeIds.join(', '),
          }))
          return
        }

        if (selectedForm.value?.id !== targetFormId) {
          console.warn(`[FormDesigner] form switched (${targetFormId} -> ${selectedForm.value?.id ?? 'none'}) while collecting save payload; aborting to avoid saving one table's fields onto another form`)
          return
        }

        if (blockingOpened) {
          blockingProgress!.setMessage(
            t('form.saveBlockingSaving'),
            t('form.saveBlockingHint'),
          )
        }

        const updated = await store.updateForm(functionUnitId, selectedForm.value.id, {
          formName: selectedForm.value.formName,
          formType: selectedForm.value.formType,
          description: selectedForm.value.description,
          configJson: nextConfig,
          ...(selectedForm.value.formType === 'TASK' && selectedForm.value.fieldPermissions
            ? { fieldPermissions: selectedForm.value.fieldPermissions }
            : {})
        })
        // Only write back if the user is still on the same form — updateForm yields, and the
        // selection may have moved on; overlaying this form's configJson onto the newly
        // selected form would show this table's fields there until its own load finishes.
        if (selectedForm.value?.id === targetFormId) {
          selectedForm.value = {
            ...selectedForm.value,
            configJson: updated.configJson || nextConfig
          }
        }

        if (isManual) {
          ElMessage.success(t('form.saveSuccess'))
          await loadForms()
        } else {
          lastAutoSaveTime.value = new Date()
          autoSaveFailureNotified.value = false
        }
      } finally {
        if (blockingOpened) {
          blockingProgress!.close()
        }
        if (isManual) {
          savingForm.value = false
        }
      }
    } catch (e: any) {
      // 保存失败不许静默：用户以为已自动保存、离开页面即丢工作。
      console.error('[FormDesigner] save failed', e)
      if (isManual) {
        ElMessage.error(e.response?.data?.message || t('form.saveFailed'))
      } else if (!autoSaveFailureNotified.value) {
        autoSaveFailureNotified.value = true
        ElMessage.warning(e.response?.data?.message || t('form.saveFailed'))
      }
    } finally {
      if (!isManual) {
        autoSaving.value = false
      }
      if (isManual) {
        savingForm.value = false
      }
    }
  }

  return {
    dataTableColumns,
    loadDataTableColumns,
    validateFieldNames,
    currentFormFields,
    getFieldPermission,
    setFieldPermission,
    handleSaveForm,
    savingForm,
  }
}
