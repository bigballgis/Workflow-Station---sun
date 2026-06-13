import { ref, computed } from 'vue'
import type { ProcessInstance } from '@/api/process'
import type { ProcessNode, ProcessFlow } from '@/components/ProcessDiagram.vue'
import type { HistoryRecord } from '@/components/ProcessHistory.vue'
import type { FormField, FormTab } from '@/components/FormRenderer.vue'
import type { RelationFieldDef } from '@/components/subTableAddDialogHelpers'
import type { MiSubProcessScopeConfig } from '@/composables/tasks/miSubProcessScope'

// Previous node forms (read-only display, ordered)
export interface PreviousFormEntry {
  formId: string
  formName: string
  labelWidth: string
  fields: FormField[]
  tabs: FormTab[]
  isMiSubTask: boolean
  /** Initiator My Request: MI sub-task userTask is the runtime current step (not in "previous" slice). */
  isActiveMiSubTaskStep?: boolean
  subTableBindings: Array<{
    bindingId: number
    tableId?: number | null
    bindingType: string
    bindingMode: string
    foreignKeyField: string | null
    tableName: string
    tableType: string
    tableDescription: string
    columns: Array<{ field: string; label: string; type?: string }>
    data: any[]
    /** When FORM_ONLY and not in form rule, binding exists for Link Form only (no standalone block). */
    subMode?: string
    formFields?: FormField[]
    formOptions?: Record<string, any>
    portalViews?: Record<string, any> | null
    primaryKeyFields?: string[]
  }>
}

// Workflow diagram: click a BPMN node to preview its bound form (My Request)
export interface ApplicationDiagramNodeFormInfo {
  formName: string
  /** Matches the process "current" BPMN userTask — reuse main form layout + variables */
  isCurrentStep: boolean
  fields: FormField[]
  tabs: FormTab[]
  values: Record<string, any>
  subTableBindings: PreviousFormEntry['subTableBindings']
  /** tableBindings on this BPMN form — excludes merge-only link targets. */
  nativeSubTableBindingIds: number[]
  /** Designer configJson for link-form suppression (subListViews). */
  formConfig: Record<string, any>
}

export type ApplicationDetailSecondaryCtx = {
  content: any
  useInitiatorFormOnly: boolean
  bpmnAllOrderedForms: Array<{ formId: string | null; formName: string | null; taskName: string | null }>
  bindingRelationTableMap: Map<number, number | null>
  selectedForm: any
  lookupSourceId: number | null
}

export function createApplicationDetailState(options: {
  processId: string
  snapshotTime: string | undefined
  snapshotTaskName: string | undefined
  snapshotTaskId: string | undefined
  snapshotTaskDefinitionKey: string | undefined
}) {
  const { processId, snapshotTime, snapshotTaskName, snapshotTaskId, snapshotTaskDefinitionKey } = options

  /** True when the viewer is the process initiator on My Request (not Completed Tasks snapshot). */
  const isInitiatorMyRequestView = ref(false)

  const loading = ref(true)
  const urging = ref(false)
  const withdrawing = ref(false)
  const processInfo = ref<ProcessInstance>({} as ProcessInstance)

  // Process diagram data
  const processNodes = ref<ProcessNode[]>([])
  const processFlows = ref<ProcessFlow[]>([])
  const currentNodeId = ref('')
  const completedNodeIds = ref<string[]>([])
  const bpmnXml = ref('')
  const activeMiSubProcessScope = ref<MiSubProcessScopeConfig | null>(null)
  const miMissingPrimaryKeyWarned = new Set<string>()

  /** Heavy BPMN node/flow parse is deferred so form + sub-tables can paint first. */
  const diagramReady = ref(false)

  // Form data
  const formFields = ref<FormField[]>([])
  const formTabs = ref<FormTab[]>([])
  const formFieldsAfterTabs = ref<FormField[]>([])
  const formData = ref<Record<string, any>>({})
  const currentFormName = ref('')
  const formLabelWidth = ref('160px')
  const formFormOptions = ref<Record<string, unknown>>({})

  // Sub-table bindings
  const subTableBindings = ref<Array<{
    bindingId: number
    tableId?: number | null
    bindingType: string
    bindingMode: string
    foreignKeyField: string | null
    tableName: string
    tableType: string
    tableDescription: string
    columns: Array<{ field: string; label: string; type?: string }>
    data: any[]
    subMode?: string
    formFields?: FormField[]
    formOptions?: Record<string, any>
    // Per-binding portalViews loaded from form configJson.subTablePortalViews[bindingId].
    portalViews?: Record<string, any> | null
    /** From dw_field_definitions via admin assembleFunctionUnitContent; drives row merge / PK resolution. */
    primaryKeyFields?: string[]
    fieldDefinitions?: Array<Record<string, unknown>>
  }>>([])

  const primaryTableBinding = ref<{ tableId?: number | null; tableName?: string } | null>(null)
  const functionUnitIdRef = ref('')

  /** Cached from loadFunctionUnitContent — shared attachment slice merge (parity with tasks/detail.vue). */
  const lastBindingRelationTableMap = ref<Map<number, number | null>>(new Map())

  /** Binding ids from the active form's tableBindings (not merge-only link targets). */
  const mainFormNativeSubTableBindingIds = ref<number[]>([])

  /** Cached designer config for the active main form (subListViews, subTablePortalViews, rule). */
  const mainFormConfig = ref<Record<string, any>>({})

  /** Lookup config fallback map (from rt_lookup_configs) */
  const lookupDbConfigs = ref<Record<string, { tableId: number; searchFields: string[]; displayField: string; viewFields: any[] }>>({})

  // Relation view configs from configJson (designed in developer-workstation)
  const relationViewConfigs = ref<Record<string, { viewFields: any[]; allFields: any[] }>>({})

  const previousForms = ref<PreviousFormEntry[]>([])

  const selectedNodeId = ref<string | null>(null)
  const nodeFormMap = ref<Map<string, ApplicationDiagramNodeFormInfo>>(new Map())

  const selectedNodeForm = computed((): ApplicationDiagramNodeFormInfo | null => {
    if (!selectedNodeId.value) return null
    return nodeFormMap.value.get(selectedNodeId.value) ?? null
  })

  // Link-form columns need access to other bindings as fallback data sources.
  // Keep the contract aligned with `tasks/detail.vue` (linkableSubTableBindings).
  const linkableSubTableBindings = computed<any[]>(() => [
    ...(subTableBindings.value as any[]),
    ...previousForms.value.flatMap(form => (form.subTableBindings as any[]))
  ])

  /** Same as tasks/detail.vue: link-form `.find()` must resolve prev-form bindings before current (empty MI slice). */
  // Sub-task form detail dialog
  const subTaskDetailVisible = ref(false)
  const subTaskDetailTitle = ref('')
  const subTaskDetailFields = ref<FormField[]>([])
  const subTaskDetailData = ref<Record<string, any>>({})
  const subTaskDetailSubTableBindings = ref<Array<{
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
  }>>([])
  const subTaskDetailLinkableBindings = computed(() => [
    ...subTaskDetailSubTableBindings.value,
    ...linkableSubTableBindings.value,
  ])
  const subTaskFormSchema = ref<any>(null)
  const subTaskFormId = ref<string | null>(null)
  /** My Request always shows the form section; current node + portalViews drive MI summary vs full layout. */
  const showCurrentFormSection = computed(() => true)

  const hasSubTaskFormSchema = computed(() => !!subTaskFormSchema.value)

  // Flow history records
  const historyRecords = ref<HistoryRecord[]>([])
  const snapshotActivityId = ref<string | null>(snapshotTaskDefinitionKey || null)

  return {
    processId,
    snapshotTime,
    snapshotTaskName,
    snapshotTaskId,
    snapshotTaskDefinitionKey,
    isInitiatorMyRequestView,
    loading,
    urging,
    withdrawing,
    processInfo,
    processNodes,
    processFlows,
    currentNodeId,
    completedNodeIds,
    bpmnXml,
    activeMiSubProcessScope,
    miMissingPrimaryKeyWarned,
    diagramReady,
    formFields,
    formTabs,
    formFieldsAfterTabs,
    formData,
    currentFormName,
    formLabelWidth,
    formFormOptions,
    subTableBindings,
    primaryTableBinding,
    functionUnitIdRef,
    lastBindingRelationTableMap,
    mainFormNativeSubTableBindingIds,
    mainFormConfig,
    lookupDbConfigs,
    relationViewConfigs,
    previousForms,
    selectedNodeId,
    nodeFormMap,
    selectedNodeForm,
    linkableSubTableBindings,
    subTaskDetailVisible,
    subTaskDetailTitle,
    subTaskDetailFields,
    subTaskDetailData,
    subTaskDetailSubTableBindings,
    subTaskDetailLinkableBindings,
    subTaskFormSchema,
    subTaskFormId,
    showCurrentFormSection,
    hasSubTaskFormSchema,
    historyRecords,
    snapshotActivityId,
    /** Deferred diagram parse gate — mirrors the original `let diagramParseScheduled`. */
    diagramParseScheduled: false,
    /** Diagram node preview map deferred-build state (original `let` pair). */
    pendingNodeFormMapContent: null as any | null,
    nodeFormMapBuildScheduled: false,
    /** Secondary (idle-time) load context (original `let` pair). */
    pendingApplicationDetailSecondary: null as ApplicationDetailSecondaryCtx | null,
    applicationDetailSecondaryScheduled: false,
    /** Function unit forms + relation-table field index — schema fallback parity with tasks/detail.vue */
    cachedContentForms: [] as any[],
    cachedRelationTableFieldIndex: new Map<number, RelationFieldDef[]>(),
  }
}

export type ApplicationDetailState = ReturnType<typeof createApplicationDetailState>
