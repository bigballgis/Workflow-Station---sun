import axios from 'axios'
import { getUser } from './auth'
import i18n from '@/i18n'

// Create a separate axios instance for function unit API
export const functionUnitAxios = axios.create({
  baseURL: '',
  timeout: 30000,
  withCredentials: true
})

functionUnitAxios.interceptors.request.use(config => {
  // Add X-User-Id request header for backend permission check
  const user = getUser()
  if (user && user.userId) {
    config.headers['X-User-Id'] = user.userId
  }
  
  return config
})

functionUnitAxios.interceptors.response.use(
  response => {
    // Check the success field in the response body to ensure it's a successful response
    if (response.data && typeof response.data === 'object' && 'success' in response.data) {
      if (response.data.success === false) {
        // If success is false, even with HTTP status 200, it should be treated as an error
        const error = new Error(response.data.error?.message || i18n.global.t('api.requestFailed'))
        ;(error as any).response = {
          status: response.data.error?.code === '403' ? 403 : 500,
          data: response.data
        }
        return Promise.reject(error)
      }
    }
    return response.data
  },
  async error => {
    const { response } = error
    
    // Handle 401 Unauthorized
    if (response?.status === 401) {
      const { clearAuth } = await import('./auth')
      const { redirectToUnifiedLogin } = await import('@/utils/sso')
      clearAuth()
      redirectToUnifiedLogin('developer-workstation')
      return Promise.reject(error)
    }
    
    // Handle 403 Forbidden
    if (response?.status === 403) {
      const { clearAuth } = await import('./auth')
      const { getStoredUser } = await import('./auth')
      const user = getStoredUser()
      if (!user) {
        clearAuth()
        const { redirectToUnifiedLogin } = await import('@/utils/sso')
        redirectToUnifiedLogin('developer-workstation')
      }
      return Promise.reject(error)
    }
    
    return Promise.reject(error)
  }
)

export interface FunctionUnit {
  id: number
  name: string
  description?: string
  icon?: { id: number; name: string; url: string }
  status: string
  currentVersion?: string
  createdBy: string
  createdAt: string
  updatedBy?: string
  updatedAt?: string
  tableDefinitions?: TableDefinition[]
  formDefinitions?: FormDefinition[]
  actionDefinitions?: ActionDefinition[]
  processDefinition?: ProcessDefinition
}

export interface FunctionUnitResponse {
  id: number
  name: string
  description?: string
  iconId?: number
  iconUrl?: string
  status: string
  currentVersion?: string
  createdAt: string
  updatedAt?: string
  tableCount: number
  formCount: number
  actionCount: number
  hasProcess: boolean
}

export interface FunctionUnitRequest {
  name: string
  description?: string
  iconId?: number
}

/** 主表 Request ID 配置:有序字段 + 分隔符,拼成一条 request 的人类可读标识(如 HR-2026-001)。仅 MAIN 表有意义。 */
export interface RequestIdConfig {
  /** 有序字段名;顺序即拼接先后(存 fieldName) */
  fieldNames: string[]
  /** 字段间分隔符,如 '-' / '/' / '_' / '.' / ' ' / ''(无) */
  separator: string
}

export interface TableDefinition {
  id: number
  tableName: string
  tableDisplayName?: string
  tableType: string
  description?: string
  requestIdConfig?: RequestIdConfig | null
  fieldDefinitions: FieldDefinition[]
}

export interface FieldDefinition {
  id?: number
  fieldName: string
  dataType: string
  length?: number
  precision?: number
  scale?: number
  nullable: boolean
  isPrimaryKey: boolean
  defaultValue?: string
  displayName?: string
  isForeignKey?: boolean
  refTableId?: number
  refPrimaryKeyFields?: string[]
  pkGeneration?: Record<string, unknown>
  pkGenerationJson?: Record<string, unknown>
  fkDisplayMode?: 'readonly' | 'hidden'
  relationCardinality?: string
}

export type FormType = 'PROCESS' | 'TASK' | 'ACTION'

export interface StageBinding {
  id?: number
  stageId: string
  stageName?: string
}

export interface FormDefinition {
  id: number
  formName: string
  formType: FormType
  description?: string
  configJson: Record<string, any>
  boundTableId?: number
  boundTableName?: string
  formSchema?: string // deprecated, use configJson instead
  tableBindings?: TableBinding[]
  fieldPermissions?: Record<string, string>
  showLiveValues?: boolean
  stageBindings?: StageBinding[]
}

// Table binding type
export type BindingType = 'PRIMARY' | 'SUB' | 'RELATED'

// Sub binding mode
export type SubBindingMode = 'FULL' | 'FORM_ONLY'

// Binding mode
export type BindingMode = 'EDITABLE' | 'READONLY'

// Table binding interface
export type BindingLinkMode = 'structuralFk' | 'miParticipantRow'

export interface TableBinding {
  id?: number
  tableId: number
  tableName?: string
  tableType?: string
  bindingType: BindingType
  bindingMode: BindingMode
  foreignKeyField?: string
  bindingLinkMode?: BindingLinkMode
  sortOrder: number
  subListViewId?: number
  subMode?: SubBindingMode
}

// Table binding request
export interface TableBindingRequest {
  tableId?: number
  relationTableId?: number
  bindingType: BindingType
  bindingMode?: BindingMode
  foreignKeyField?: string
  bindingLinkMode?: BindingLinkMode
  sortOrder?: number
  subMode?: SubBindingMode
}

export interface ActionDefinition {
  id: string | number // Support both String IDs (new) and numeric IDs (legacy)
  actionName: string
  actionType: string
  description?: string
  configJson: Record<string, any>
  actionConfig?: string // deprecated, use configJson instead
}

export interface ProcessDefinition {
  id: number
  processKey: string
  processName: string
  bpmnXml?: string
  description?: string
}

export interface TableRelationDTO {
  id?: number
  sourceTableId: number
  sourceFieldName: string
  relationType: string
  targetTableId: number
  targetFieldName: string
}

export interface ForeignKeyDTO {
  id: number
  sourceTableId: number
  sourceTableName: string
  sourceFieldId: number
  sourceFieldName: string
  targetTableId: number
  targetTableName: string
  targetFieldId: number
  targetFieldName: string
  onDelete?: string
  onUpdate?: string
}

export interface ValidationResult {
  valid: boolean
  errors: string[]
  warnings: string[]
}

export interface GatewayEvaluationItem {
  flowId: string
  condition?: string
  result: boolean
  reason?: string
}

export interface GatewayEvaluation {
  gatewayId: string
  gatewayType?: string
  defaultFlowId?: string
  selectedFlowId?: string
  evaluations: GatewayEvaluationItem[]
}

export interface DebugLookupProbeRequest {
  formId: number
  bindingId: number
  lookupConfig: Record<string, any>
  keyword?: string
  runtimeVariables?: Record<string, any>
  page?: number
  size?: number
  sort?: string[]
  searchMode?: 'contains' | 'startsWith' | 'exact'
}

export interface DebugLookupProbeResult {
  columns: Array<{ fieldName: string; label?: string }>
  rows: Array<Record<string, any>>
  appliedFilters?: Array<{ fieldName: string; value: any }>
  page: number
  size: number
  total: number
}

export interface DebugActionRunRequest {
  nodeId: string
  actionId: string | number
  runtimeVariables?: Record<string, any>
  formData?: Record<string, any>
  dryRun?: boolean
}

export interface DebugActionRunResult {
  success: boolean
  actionResult?: Record<string, any>
  variablePatches?: Record<string, any>
  logs?: string[]
  durationMs?: number
}

export interface Version {
  id: number
  versionNumber: string
  changeLog?: string
  createdBy: string
  createdAt: string
  current?: boolean
  snapshotData?: string
}

export const functionUnitApi = {
  // Function Unit CRUD
  list: (params: { name?: string; status?: string; page?: number; size?: number }) =>
    functionUnitAxios.get<any, { data: { content: FunctionUnitResponse[]; totalElements: number } }>('/api/v1/function-units', { params }),
  
  getById: (id: number) => 
    functionUnitAxios.get<any, { data: FunctionUnit }>(`/api/v1/function-units/${id}`),
  
  create: (data: FunctionUnitRequest) => 
    functionUnitAxios.post<any, { data: FunctionUnit }>('/api/v1/function-units', data),
  
  update: (id: number, data: FunctionUnitRequest) => 
    functionUnitAxios.put<any, { data: FunctionUnit }>(`/api/v1/function-units/${id}`, data),
  
  delete: (id: number) => 
    functionUnitAxios.delete(`/api/v1/function-units/${id}`),
  
  publish: (id: number, changeLog?: string) => 
    functionUnitAxios.post<any, { data: FunctionUnit }>(`/api/v1/function-units/${id}/publish`, null, { params: { changeLog } }),
  
  clone: (id: number, newName: string) => 
    functionUnitAxios.post<any, { data: FunctionUnit }>(`/api/v1/function-units/${id}/clone`, null, { params: { newName } }),
  
  validate: (id: number) => 
    functionUnitAxios.get<any, { data: ValidationResult }>(`/api/v1/function-units/${id}/validate`),

  // Table Definitions
  getTables: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: TableDefinition[] }>(`/api/v1/function-units/${functionUnitId}/tables`),
  
  createTable: (functionUnitId: number, data: Partial<TableDefinition>) =>
    functionUnitAxios.post<any, { data: TableDefinition }>(`/api/v1/function-units/${functionUnitId}/tables`, data),

  checkTableNameAvailable: (functionUnitId: number, tableName: string, excludeTableId?: number) =>
    functionUnitAxios.get<any, { data: { available: boolean; tableName: string } }>(
      `/api/v1/function-units/${functionUnitId}/tables/name-available`,
      { params: { tableName, excludeTableId } },
    ),
  
  updateTable: (functionUnitId: number, tableId: number, data: Partial<TableDefinition>) =>
    functionUnitAxios.put<any, { data: TableDefinition }>(`/api/v1/function-units/${functionUnitId}/tables/${tableId}`, data),
  
  deleteTable: (functionUnitId: number, tableId: number) =>
    functionUnitAxios.delete(`/api/v1/function-units/${functionUnitId}/tables/${tableId}`),

  // Form Definitions
  getForms: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: FormDefinition[] }>(`/api/v1/function-units/${functionUnitId}/forms`),
  
  createForm: (functionUnitId: number, data: Partial<FormDefinition>) =>
    functionUnitAxios.post<any, { data: FormDefinition }>(`/api/v1/function-units/${functionUnitId}/forms`, data),
  
  updateForm: (functionUnitId: number, formId: number, data: Partial<FormDefinition>) =>
    functionUnitAxios.put<any, { data: FormDefinition }>(`/api/v1/function-units/${functionUnitId}/forms/${formId}`, data),
  
  deleteForm: (functionUnitId: number, formId: number) =>
    functionUnitAxios.delete(`/api/v1/function-units/${functionUnitId}/forms/${formId}`),

  // Action Definitions
  getActions: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: ActionDefinition[] }>(`/api/v1/function-units/${functionUnitId}/actions`),
  
  createAction: (functionUnitId: number, data: Partial<ActionDefinition>) =>
    functionUnitAxios.post<any, { data: ActionDefinition }>(`/api/v1/function-units/${functionUnitId}/actions`, data),
  
  updateAction: (functionUnitId: number, actionId: string | number, data: Partial<ActionDefinition>) =>
    functionUnitAxios.put<any, { data: ActionDefinition }>(`/api/v1/function-units/${functionUnitId}/actions/${actionId}`, data),
  
  deleteAction: (functionUnitId: number, actionId: string | number) =>
    functionUnitAxios.delete(`/api/v1/function-units/${functionUnitId}/actions/${actionId}`),

  // Process Definition
  getProcess: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: ProcessDefinition }>(`/api/v1/function-units/${functionUnitId}/process`),
  
  saveProcess: (functionUnitId: number, data: Partial<ProcessDefinition>) =>
    functionUnitAxios.post<any, { data: ProcessDefinition }>(`/api/v1/function-units/${functionUnitId}/process`, data),

  // Versions
  getVersions: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: Version[] }>(`/api/v1/function-units/${functionUnitId}/versions`),
  
  rollback: (functionUnitId: number, versionId: number) =>
    functionUnitAxios.post<any, { data: FunctionUnit }>(`/api/v1/function-units/${functionUnitId}/versions/${versionId}/rollback`),

  compareVersions: (functionUnitId: number, versionId1: number, versionId2: number) =>
    functionUnitAxios.get<any, { data: any }>(`/api/v1/function-units/${functionUnitId}/versions/compare`, {
      params: { versionId1, versionId2 }
    }),

  // Table DDL
  generateDDL: (functionUnitId: number, tableId: number, dialect: string) =>
    functionUnitAxios.get<any, { data: string }>(`/api/v1/function-units/${functionUnitId}/tables/${tableId}/ddl`, {
      params: { dialect }
    }),

  validateTables: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: ValidationResult }>(`/api/v1/function-units/${functionUnitId}/tables/validate`),

  // Table Relations
  saveTableRelations: (functionUnitId: number, relations: TableRelationDTO[]) =>
    functionUnitAxios.post<any, { data: TableRelationDTO[] }>(`/api/v1/function-units/${functionUnitId}/table-relations`, relations),

  getTableRelations: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: TableRelationDTO[] }>(`/api/v1/function-units/${functionUnitId}/table-relations`),

  // Foreign Keys
  getForeignKeys: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: ForeignKeyDTO[] }>(`/api/v1/function-units/${functionUnitId}/tables/foreign-keys`),

  allocatePrimaryKeys: (functionUnitId: number, payload: {
    tableId: number
    fieldName: string
    count?: number
    scopeKey?: string
  }) =>
    functionUnitAxios.post<any, { data: { values: string[] } }>(
      `/api/v1/function-units/${functionUnitId}/tables/primary-keys/allocate`,
      payload,
    ),

  // Action test
  testAction: (functionUnitId: number, actionId: string | number, testData: any) =>
    functionUnitAxios.post<any, { data: any }>(`/api/v1/function-units/${functionUnitId}/actions/${actionId}/test`, testData),

  // Process validation
  validateProcess: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: ValidationResult }>(`/api/v1/function-units/${functionUnitId}/process/validate`),

  // Process simulation
  simulateProcess: (functionUnitId: number, variables: any) =>
    functionUnitAxios.post<any, { data: any }>(`/api/v1/function-units/${functionUnitId}/process/simulate`, variables),

  // Process debug lookup probe
  debugLookupProbe: (functionUnitId: number, payload: DebugLookupProbeRequest) =>
    functionUnitAxios.post<any, { data: DebugLookupProbeResult }>(
      `/api/v1/function-units/${functionUnitId}/process/debug/lookup/probe`,
      payload
    ),

  // Process debug action runner
  debugRunAction: (functionUnitId: number, payload: DebugActionRunRequest) =>
    functionUnitAxios.post<any, { data: DebugActionRunResult }>(
      `/api/v1/function-units/${functionUnitId}/process/debug/actions/run`,
      payload
    ),

  // Form Table Bindings
  getFormBindings: (functionUnitId: number, formId: number) =>
    functionUnitAxios.get<any, { data: TableBinding[] }>(`/api/v1/function-units/${functionUnitId}/forms/${formId}/bindings`),
  
  createFormBinding: (functionUnitId: number, formId: number, data: TableBindingRequest) =>
    functionUnitAxios.post<any, { data: TableBinding }>(`/api/v1/function-units/${functionUnitId}/forms/${formId}/bindings`, data),
  
  updateFormBinding: (functionUnitId: number, formId: number, bindingId: number, data: TableBindingRequest) =>
    functionUnitAxios.put<any, { data: TableBinding }>(`/api/v1/function-units/${functionUnitId}/forms/${formId}/bindings/${bindingId}`, data),
  
  deleteFormBinding: (functionUnitId: number, formId: number, bindingId: number) =>
    functionUnitAxios.delete(`/api/v1/function-units/${functionUnitId}/forms/${formId}/bindings/${bindingId}`),

  // Form Design Helpers
  getDataTableColumns: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: string[] }>(`/api/v1/function-units/${functionUnitId}/forms/data-table-columns`),

  copyTaskForm: (functionUnitId: number, formId: number) =>
    functionUnitAxios.post<any, { data: FormDefinition }>(`/api/v1/function-units/${functionUnitId}/forms/${formId}/copy`),

  copyProcessToTaskForm: (functionUnitId: number, formId: number) =>
    functionUnitAxios.post<any, { data: FormDefinition }>(`/api/v1/function-units/${functionUnitId}/forms/${formId}/copy-to-task`),

  // Export, Import and Deploy
  exportFunctionUnit: (functionUnitId: number) =>
    functionUnitAxios.get(`/api/v1/function-units/${functionUnitId}/export`, { responseType: 'blob' }),

  importFunctionUnit: (file: File, conflictStrategy: 'SKIP' | 'OVERWRITE' | 'RENAME' = 'RENAME') => {
    const formData = new FormData()
    formData.append('file', file)
    return functionUnitAxios.post<any, { data: { status: string; message?: string; functionUnitId?: number } }>(
      '/api/v1/export-import/import',
      formData,
      { params: { conflictStrategy } }
    )
  },
  
  deploy: (functionUnitId: number, request: DeployRequest) =>
    functionUnitAxios.post<any, { data: DeployResponse }>(`/api/v1/function-units/${functionUnitId}/deploy`, request),
  
  getDeploymentStatus: (deploymentId: string) =>
    functionUnitAxios.get<any, { data: DeployResponse }>(`/api/v1/function-units/deployments/${deploymentId}/status`),
  
  getDeploymentHistory: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: DeployResponse[] }>(`/api/v1/function-units/${functionUnitId}/deployments`)
}

// Deploy types
export interface DeployRequest {
  targetUrl?: string
  environment?: 'DEVELOPMENT' | 'TESTING' | 'PRODUCTION'
  conflictStrategy?: string
  autoEnable?: boolean
  changeLog?: string
}


export interface DeployResponse {
  deploymentId: string
  status: 'PENDING' | 'DEPLOYING' | 'SUCCESS' | 'FAILED' | 'ROLLED_BACK'
  message?: string
  progress?: number
  steps?: DeployStep[]
  deployedAt?: string
  versionNumber?: string
  changeLog?: string
}


export interface DeployStep {
  name: string
  status: string
  message?: string
  completedAt?: string
}
