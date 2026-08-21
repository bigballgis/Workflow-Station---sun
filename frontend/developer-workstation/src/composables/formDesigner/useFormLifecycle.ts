import { nextTick, reactive, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { TabPaneName } from 'element-plus'
import type { FieldDefinition, FormDefinition, FormScene, FormType } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import type { SubTableFieldDTO } from '@/api/subTableView'
import { cloneFormRules, injectUploadButtonLabels, mergeLoadedFormOptions } from '@/utils/formDesigner'
import {
  inflateComponentEventsForDesigner,
  walkRulesEnsureComponentEvents,
} from '@/utils/formCreateDefaultEvents'
import { stripFormCreateRulesDisabledDeep } from '@/utils/formCreateRuleUtils'
import { syncFormRulesWithTableFields } from '@/utils/formFieldMeta'
import { resolveRelationViewEntry, resolveBindingKeyedEntry } from '@/utils/formConfigBindingResolve'
import type { TableFieldDefLike } from '@/utils/formCreateRuleDefaults'
import { parseProcessNodesFromBpmnXml, type BpmnProcessNode } from '@/utils/bpmnFormBindingUpdate'
import type { SubTableListColumnDTO } from './useSubTableViews'

interface UseFormLifecycleOptions {
  functionUnitId: number
  store: {
    forms: FormDefinition[]
    tables: any[]
    process: { bpmnXml?: string } | null
    fetchForms: (functionUnitId: number) => Promise<unknown>
    fetchTables: (functionUnitId: number) => Promise<unknown>
    fetchProcess: (functionUnitId: number) => Promise<unknown>
    createForm: (functionUnitId: number, payload: Record<string, any>) => Promise<any>
  }
  selectedForm: Ref<FormDefinition | null>
  designerRef: Ref<any>
  subDesignerRefs: Ref<any[]>
  subFormCache: Ref<Record<number, { rule: any[]; options: any }>>
  subTableListViewRefs: Ref<Record<number, any>>
  subTableViewState: Ref<Record<number, { allFields: SubTableFieldDTO[]; viewFields: SubTableListColumnDTO[] }>>
  relationViewState: Ref<Record<number, { allFields: any[]; viewFields: any[] }>>
  designerSubBindings: ComputedRef<Array<{ bindingId: number; bindingType: string; tableId: number }>>
  activeDesignerTab: Ref<string>
  showCreateDialog: Ref<boolean>
  defaultFormOption: ComputedRef<Record<string, any>>
  buildEffectiveMainFormConfig: (row: FormDefinition, bindings: { bindingType: string; tableId: number }[]) => Record<string, any>
  buildEffectiveSubFormConfig: (
    subForms: Record<string, unknown> | undefined,
    bindingId: number,
    bindings: { id?: number; bindingType: string; tableId: number; sortOrder?: number }[],
    tableId: number,
  ) => { rule: unknown[]; options: Record<string, unknown> }
  getTableFieldDefinitions: (tableId: number) => FieldDefinition[]
  getPrimaryBindingFieldDefinitions: () => FieldDefinition[]
  getTableFieldDefinitionsByTableId: (tableId?: number | null) => FieldDefinition[]
  mergeTaskPermissionsForFields: (fields: FieldDefinition[]) => void
  hydrateDesignerRulesFromLatestTableDefaults: (rules: unknown[], fieldDefs: TableFieldDefLike[]) => void
  refreshFormRulesFromTableMetadata: () => void
  loadSubTableViewConfig: (bindingId: number, binding: any) => Promise<void>
  parseFormBindingsFromBpmn: () => void
  patchDesignerRulesDefaultEvents: () => void
  installDesignerPreviewCaptureHooks: () => void
  onDesignerStructureChange: () => void
  setupAutoSavePolling: () => void
  cleanupAutoSavePolling: () => void
  setupMarkerObserver: () => void
  teardownMarkerObserver: () => void
  scheduleSyncHiddenMarkers: () => void
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Form list/selection lifecycle for FormDesigner: load forms, select a form
 * (hydrating main + sub designers), tab switching, back-to-list cleanup and
 * the create-form dialog flow.
 */
export function useFormLifecycle(options: UseFormLifecycleOptions) {
  const {
    functionUnitId, store, selectedForm, designerRef, subDesignerRefs, subFormCache,
    subTableListViewRefs, subTableViewState, relationViewState,
    designerSubBindings, activeDesignerTab, showCreateDialog, defaultFormOption,
    buildEffectiveMainFormConfig, buildEffectiveSubFormConfig, getTableFieldDefinitions, getPrimaryBindingFieldDefinitions,
    getTableFieldDefinitionsByTableId, mergeTaskPermissionsForFields,
    hydrateDesignerRulesFromLatestTableDefaults, refreshFormRulesFromTableMetadata,
    loadSubTableViewConfig, parseFormBindingsFromBpmn,
    patchDesignerRulesDefaultEvents, installDesignerPreviewCaptureHooks, onDesignerStructureChange,
    setupAutoSavePolling, cleanupAutoSavePolling,
    setupMarkerObserver, teardownMarkerObserver, scheduleSyncHiddenMarkers, t,
  } = options

  const loading = ref(false)
  // Sub-table tabs default to form design
  const subTableActiveTab = ref('form')

  const createForm = reactive({
    formName: '',
    formType: 'PROCESS' as FormType,
    scene: 'TASK' as FormScene,
    description: '',
    boundTableId: null as number | null,
  })
  // Stage binding state for create dialog (TASK type)
  const createFormStageIds = ref<string[]>([])
  const createDialogProcessNodes = ref<BpmnProcessNode[]>([])

  async function loadForms() {
    loading.value = true
    try {
      await store.fetchForms(functionUnitId)
      await store.fetchTables(functionUnitId)
      await store.fetchProcess(functionUnitId)
      if (selectedForm.value) {
        const refreshed = store.forms.find(form => form.id === selectedForm.value?.id)
        if (refreshed) {
          selectedForm.value = {
            ...selectedForm.value,
            ...refreshed,
            tableBindings: selectedForm.value.tableBindings
          }
        }
        refreshFormRulesFromTableMetadata()
      }
      // 解析BPMN XML获取表单绑定信息
      parseFormBindingsFromBpmn()
    } finally {
      loading.value = false
    }
  }

  async function handleSelectForm(row: FormDefinition) {
    // Clean up any existing polling before selecting new form
    cleanupAutoSavePolling()

    selectedForm.value = { ...row }
    subDesignerRefs.value = []
    subFormCache.value = {}
    subTableListViewRefs.value = {}
    subTableViewState.value = {}
    // Seed per-binding portalViews from previously saved configJson so the editor reflects
    // persisted values immediately when the user opens any sub-table tab.
    activeDesignerTab.value = 'main'
    subTableActiveTab.value = 'form'

    await store.fetchTables(functionUnitId)

    let bindings: any[] = []
    try {
      const res = await functionUnitApi.getFormBindings(functionUnitId, row.id)
      bindings = res.data || []
    } catch {
      bindings = []
    }

    // ACTION forms (FORM_POPUP) are reached via their Action Button, not a PRIMARY table row,
    // so they don't require a PRIMARY binding — the Main Table tab is hidden entirely when one
    // is absent (see FormDesigner.vue's showMainTableTab). Land on the ACTION binding's own
    // canvas instead of a tab that won't render.
    const hasPrimary = bindings.some((b) => b.bindingType === 'PRIMARY')
    if (!hasPrimary) {
      const firstBinding =
        bindings.find((b) => b.bindingType === 'ACTION')
        ?? bindings.find((b) => b.bindingType === 'SUB')
        ?? bindings.find((b) => b.bindingType === 'RELATED')
      if (firstBinding) {
        activeDesignerTab.value = String(firstBinding.id)
      }
    }

    const config = row.configJson || {}
    const savedViews = config.relationViews || {}
    const initialState: Record<number, { allFields: any[]; viewFields: any[] }> = {}
    for (const b of bindings) {
      if (b.bindingType === 'RELATED') {
        const id = b.id as number
        const saved = resolveRelationViewEntry(savedViews, id, bindings)
        initialState[id] = saved
          ? { allFields: saved.allFields || [], viewFields: saved.viewFields || [] }
          : { allFields: [], viewFields: [] }
      }
    }
    relationViewState.value = initialState
    const savedSubListViews = config.subListViews || {}
    const initialSubTableViewState: Record<number, { allFields: SubTableFieldDTO[]; viewFields: SubTableListColumnDTO[] }> = {}
    for (const b of bindings) {
      // ACTION bindings (e.g. FORM_POPUP "Meeting Remark") get a list view the same way SUB does.
      if (b.bindingType === 'SUB' || b.bindingType === 'ACTION') {
        const id = b.id as number
        const saved = resolveBindingKeyedEntry(savedSubListViews, id, bindings, b.bindingType)
          ?? savedSubListViews[id]
        initialSubTableViewState[id] = {
          allFields: [],
          viewFields: Array.isArray(saved?.columns) ? saved.columns : []
        }
      }
    }
    subTableViewState.value = initialSubTableViewState

    const effectiveMain = buildEffectiveMainFormConfig(row, bindings)
    const primaryBinding = bindings.find((b) => b.bindingType === 'PRIMARY')
    const primaryFields = primaryBinding ? getTableFieldDefinitions(primaryBinding.tableId) : []
    if (effectiveMain.rule?.length && primaryFields.length) {
      effectiveMain.rule = syncFormRulesWithTableFields(effectiveMain.rule, primaryFields)
      mergeTaskPermissionsForFields(primaryFields)
    } else if (effectiveMain.rule?.length === 0 && primaryFields.length) {
      mergeTaskPermissionsForFields(primaryFields)
    }

    const mergedConfig: Record<string, any> = {
      ...(row.configJson || {}),
      ...effectiveMain
    }

    if (selectedForm.value) {
      selectedForm.value = {
        ...selectedForm.value,
        tableBindings: bindings,
        configJson: mergedConfig
      }
    }

    nextTick(() => {
      setTimeout(() => {
        if (!designerRef.value) return
        try {
          const rules = stripFormCreateRulesDisabledDeep(
            cloneFormRules(
              effectiveMain.rule && effectiveMain.rule.length ? effectiveMain.rule : []
            ) as unknown[]
          ) as ReturnType<typeof cloneFormRules>
          injectUploadButtonLabels(rules, t('form.clickToUpload'))
          inflateComponentEventsForDesigner(rules)
          walkRulesEnsureComponentEvents(rules)
          hydrateDesignerRulesFromLatestTableDefaults(rules, getPrimaryBindingFieldDefinitions())
          designerRef.value.setRule(rules)
          if (designerRef.value.activeModule) designerRef.value.activeModule = 'base'
          nextTick(() => {
            patchDesignerRulesDefaultEvents()
            installDesignerPreviewCaptureHooks()
          })
          designerRef.value.setOption(
            mergeLoadedFormOptions(
              effectiveMain.options && Object.keys(effectiveMain.options).length
                ? effectiveMain.options
                : undefined,
              defaultFormOption.value,
              t('form.clickToUpload')
            )
          )
        } catch (e) {
          console.error('Failed to load main form config:', e)
          try {
            designerRef.value.setRule([])
            designerRef.value.setOption({ ...defaultFormOption.value })
          } catch {}
        }
        setupAutoSavePolling()
        setupMarkerObserver()
        scheduleSyncHiddenMarkers()
        installDesignerPreviewCaptureHooks()
      }, 100)
    })

    nextTick(() => setTimeout(() => loadSubDesigners(row), 200))
  }

  function loadSubDesigners(row: FormDefinition) {
    const config = (selectedForm.value?.configJson || row.configJson || {}) as Record<string, any>
    const subForms = config.subForms || {}
    designerSubBindings.value.forEach((binding, index) => {
      nextTick(() => {
        setTimeout(() => {
          const subRef = subDesignerRefs.value[index]
          if (subRef) {
          const subConfig = buildEffectiveSubFormConfig(
            subForms,
            binding.bindingId,
            selectedForm.value?.tableBindings ?? [],
            binding.tableId,
          )
          try {
            const subFields = getTableFieldDefinitions(binding.tableId)
            let rawRules = subConfig.rule && subConfig.rule.length ? subConfig.rule : []
            if (rawRules.length && subFields.length) {
              rawRules = syncFormRulesWithTableFields(rawRules, subFields)
              mergeTaskPermissionsForFields(subFields)
            }
            const rules = stripFormCreateRulesDisabledDeep(
              cloneFormRules(rawRules) as unknown[]
            ) as ReturnType<typeof cloneFormRules>
              injectUploadButtonLabels(rules, t('form.clickToUpload'))
              inflateComponentEventsForDesigner(rules)
              walkRulesEnsureComponentEvents(rules)
              hydrateDesignerRulesFromLatestTableDefaults(rules, getTableFieldDefinitionsByTableId(binding.tableId))
              subRef.setRule(rules)
              if (subRef.activeModule) subRef.activeModule = 'base'
              subRef.setOption(
                mergeLoadedFormOptions(
                  subConfig.options && Object.keys(subConfig.options).length ? subConfig.options : undefined,
                  defaultFormOption.value,
                  t('form.clickToUpload')
                )
              )
              installDesignerPreviewCaptureHooks()
            } catch {}
          }
          // Hydration is async (nextTick + setTimeout) and touches no reactive state
          // the MI assignment warning computed tracks — without this it can stay
          // stuck on whatever it saw before the sub-designer's rule was populated
          // (e.g. "missing component") until the user manually visits that tab.
          onDesignerStructureChange()
        }, 150)
      })
    })
  }

  function handleTabChange(tabName: TabPaneName) {
    if (tabName === 'main') {
      nextTick(() => {
        setupMarkerObserver()
        scheduleSyncHiddenMarkers()
      })
      return
    }
    const bindingId = Number(tabName)
    const index = designerSubBindings.value.findIndex(b => b.bindingId === bindingId)
    if (index < 0) return
    const binding = designerSubBindings.value[index]
    const config = selectedForm.value?.configJson || {}

    // For RELATED bindings, restore saved view fields
    if (binding.bindingType === 'RELATED') {
      if (!relationViewState.value[bindingId]) {
        const saved = resolveRelationViewEntry(
          config.relationViews || {},
          bindingId,
          selectedForm.value?.tableBindings ?? [],
        )
        relationViewState.value = {
          ...relationViewState.value,
          [bindingId]: saved
            ? { allFields: saved.allFields || [], viewFields: saved.viewFields || [] }
            : { allFields: [], viewFields: [] },
        }
      }
      return
    }

    // For SUB / ACTION bindings, load sub-table list view config
    if (binding.bindingType === 'SUB' || binding.bindingType === 'ACTION') {
      if (!subTableViewState.value[bindingId] || subTableViewState.value[bindingId].allFields.length === 0) {
        loadSubTableViewConfig(bindingId, binding)
      }
    }

    const subForms = config.subForms || {}
    nextTick(() => {
      setTimeout(() => {
        const subRef = subDesignerRefs.value[index]
        if (subRef) {
          // Use cache if available (user already visited this tab), else fall back to saved config
          const cached = subFormCache.value[bindingId]
          const subConfig = cached || buildEffectiveSubFormConfig(
            subForms,
            bindingId,
            selectedForm.value?.tableBindings ?? [],
            binding.tableId,
          )
          try {
            const subFields = getTableFieldDefinitions(binding.tableId)
            let rawRules = subConfig.rule && subConfig.rule.length ? subConfig.rule : []
            if (rawRules.length && subFields.length) {
              rawRules = syncFormRulesWithTableFields(rawRules, subFields)
              mergeTaskPermissionsForFields(subFields)
            }
            const rules = stripFormCreateRulesDisabledDeep(
              cloneFormRules(rawRules) as unknown[]
            ) as ReturnType<typeof cloneFormRules>
            injectUploadButtonLabels(rules, t('form.clickToUpload'))
            inflateComponentEventsForDesigner(rules)
            walkRulesEnsureComponentEvents(rules)
            hydrateDesignerRulesFromLatestTableDefaults(rules, getTableFieldDefinitionsByTableId(binding.tableId))
            subRef.setRule(rules)
            if (subRef.activeModule) subRef.activeModule = 'base'
            subRef.setOption(
              mergeLoadedFormOptions(
                subConfig.options && Object.keys(subConfig.options).length ? subConfig.options : undefined,
                defaultFormOption.value,
                t('form.clickToUpload')
              )
            )
          } catch {}
        }
        setupMarkerObserver()
        onDesignerStructureChange()
      }, 100)
    })
  }

  function handleSubTableInnerTabChange(tabName: string, binding: any) {
    if (tabName !== 'listView') return
    if (!subTableViewState.value[binding.bindingId] || subTableViewState.value[binding.bindingId].allFields.length === 0) {
      loadSubTableViewConfig(binding.bindingId, binding)
    }
  }

  function handleBackToList() {
    teardownMarkerObserver()
    selectedForm.value = null
    cleanupAutoSavePolling()
  }

  async function handleCreateForm() {
    if (!createForm.formName.trim()) {
      ElMessage.warning(t('form.enterFormName'))
      return
    }
    // Step forms (PROCESS / TASK) are created as a To Do + My Requests pair: every step needs both
    // designs, and the two rows are linked by BPMN node id rather than by name.
    const createBothScenes = createForm.formType === 'PROCESS' || createForm.formType === 'TASK'
    // PROCESS type: one per scene, not one per function unit — the start step needs
    // its own read-only design for My Requests alongside the New Request one.
    // A paired create needs BOTH slots free, or the second row would fail after the first is saved.
    if (createForm.formType === 'PROCESS') {
      const scenesNeeded = createBothScenes ? ['TASK', 'REQUEST'] : [createForm.scene]
      const existingProcess = store.forms.find(
        f => f.formType === 'PROCESS' && scenesNeeded.includes(f.scene ?? 'TASK')
      )
      if (existingProcess) {
        ElMessage.warning(t('form.processFormAlreadyExists'))
        return
      }
    }
    // TASK type: require stage binding
    if (createForm.formType === 'TASK') {
      if (createFormStageIds.value.length === 0) {
        ElMessage.warning(t('form.stageBindingRequired'))
        return
      }
    }
    try {
      // The selected stage ids are deliberately NOT sent: FormDefinitionRequest has no
      // stageBindings field, so they were dropped on arrival (Jackson ignores unknown properties)
      // and the create looked like it had bound the nodes when it never did. Node binding runs
      // through the BPMN write in useFormNodeBinding / FormStageBindingController instead; the
      // picker above stays as the up-front prompt to choose nodes.
      await store.createForm(functionUnitId, {
        formName: createForm.formName,
        formType: createForm.formType,
        // A paired create always starts from the To Do scene; the backend adds the My Requests row.
        scene: createBothScenes ? 'TASK' : createForm.scene,
        description: createForm.description,
        boundTableId: createForm.boundTableId || undefined,
        configJson: { rule: [], options: {} },
        ...(createBothScenes ? { createBothScenes: true } : {}),
      })
      ElMessage.success(t('form.createSuccess'))
      showCreateDialog.value = false
      Object.assign(createForm, {
        formName: '', formType: 'PROCESS', scene: 'TASK', description: '', boundTableId: null,
      })
      createFormStageIds.value = []
      loadForms()
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('form.createFailed'))
    }
  }

  /** Load process nodes for stage binding in create dialog */
  async function loadCreateDialogProcessNodes() {
    try {
      const processData = await functionUnitApi.getProcess(functionUnitId)
      const bpmnXml = processData?.data?.bpmnXml
      if (bpmnXml) {
        createDialogProcessNodes.value = parseProcessNodesFromBpmnXml(bpmnXml, ['userTask'])
      } else {
        createDialogProcessNodes.value = []
      }
    } catch {
      createDialogProcessNodes.value = []
    }
  }

  /** Handle form type change in create dialog */
  function handleCreateFormTypeChange(type: FormType) {
    if (type === 'TASK' && createDialogProcessNodes.value.length === 0) {
      loadCreateDialogProcessNodes()
    }
    createFormStageIds.value = []
    // DETAIL forms are opened from a view row, not from a workflow step, so the
    // scene axis (To Do vs My Requests) does not apply to them.
    // ACTION forms are opened by a To Do action button, and My Requests has no action buttons,
    // so they are pinned to To Do as well (the dialog also disables the My Requests option).
    if (type === 'DETAIL' || type === 'ACTION') {
      createForm.scene = 'TASK'
    }
  }

  return {
    loading,
    subTableActiveTab,
    createForm,
    createFormStageIds,
    createDialogProcessNodes,
    loadForms,
    handleSelectForm,
    loadSubDesigners,
    handleTabChange,
    handleSubTableInnerTabChange,
    handleBackToList,
    handleCreateForm,
    loadCreateDialogProcessNodes,
    handleCreateFormTypeChange,
  }
}
