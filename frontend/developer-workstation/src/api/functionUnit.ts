import axios from 'axios'
import { TOKEN_KEY, getUser } from './auth'

// Create a separate axios instance for function unit API
const functionUnitAxios = axios.create({
  baseURL: '',
  timeout: 30000
})

functionUnitAxios.interceptors.request.use(config => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  
  // 添加 X-User-Id 请求头，用于后端权限检查
  const user = getUser()
  if (user && user.userId) {
    config.headers['X-User-Id'] = user.userId
  }
  
  return config
})

functionUnitAxios.interceptors.response.use(
  response => {
    console.log('[FunctionUnitAPI] Response:', response.status, response.data)
    return response.data
  },
  async error => {
    const { response } = error
    
    // 处理 401 未授权
    if (response?.status === 401) {
      const { clearAuth } = await import('./auth')
      const router = (await import('@/router')).default
      clearAuth()
      router.push('/login')
      return Promise.reject(error)
    }
    
    // 处理 403 禁止访问
    if (response?.status === 403) {
      const { TOKEN_KEY, clearAuth } = await import('./auth')
      const token = localStorage.getItem(TOKEN_KEY)
      if (!token) {
        // 没有 token，清除认证并重定向到登录页
        clearAuth()
        const router = (await import('@/router')).default
        router.push('/login')
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

export interface TableDefinition {
  id: number
  tableName: string
  tableType: string
  description?: string
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
  description?: string
}

export interface FormDefinition {
  id: number
  formName: string
  formType: string
  description?: string
  configJson: Record<string, any>
  boundTableId?: number
  boundTableName?: string
  formSchema?: string // deprecated, use configJson instead
  tableBindings?: TableBinding[]
}

// 表绑定类型
export type BindingType = 'PRIMARY' | 'SUB' | 'RELATED'

// 绑定模式
export type BindingMode = 'EDITABLE' | 'READONLY'

// 表绑定接口
export interface TableBinding {
  id?: number
  tableId: number
  tableName?: string
  bindingType: BindingType
  bindingMode: BindingMode
  foreignKeyField?: string
  sortOrder: number
}

// 表绑定请求
export interface TableBindingRequest {
  tableId: number
  bindingType: BindingType
  bindingMode?: BindingMode
  foreignKeyField?: string
  sortOrder?: number
}

export interface ActionDefinition {
  id: number
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

export interface Version {
  id: number
  versionNumber: string
  changeLog?: string
  createdBy: string
  createdAt: string
  snapshotData: string
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
  
  updateTable: (functionUnitId: number, tableId: number, data: Partial<TableDefinition>) => {
    console.log('[FunctionUnitAPI] Updating table:', { functionUnitId, tableId, data })
    console.log('[FunctionUnitAPI] Request data fields:', data.fields)
    console.log('[FunctionUnitAPI] Request data JSON:', JSON.stringify(data, null, 2))
    return functionUnitAxios.put<any, { data: TableDefinition }>(`/api/v1/function-units/${functionUnitId}/tables/${tableId}`, data)
  },
  
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
  
  updateAction: (functionUnitId: number, actionId: number, data: Partial<ActionDefinition>) =>
    functionUnitAxios.put<any, { data: ActionDefinition }>(`/api/v1/function-units/${functionUnitId}/actions/${actionId}`, data),
  
  deleteAction: (functionUnitId: number, actionId: number) =>
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

  // Foreign Keys
  getForeignKeys: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: ForeignKeyDTO[] }>(`/api/v1/function-units/${functionUnitId}/tables/foreign-keys`),

  // Action test
  testAction: (functionUnitId: number, actionId: number, testData: any) =>
    functionUnitAxios.post<any, { data: any }>(`/api/v1/function-units/${functionUnitId}/actions/${actionId}/test`, testData),

  // Process validation
  validateProcess: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: ValidationResult }>(`/api/v1/function-units/${functionUnitId}/process/validate`),

  // Process simulation
  simulateProcess: (functionUnitId: number, variables: any) =>
    functionUnitAxios.post<any, { data: any }>(`/api/v1/function-units/${functionUnitId}/process/simulate`, variables),

  // Form Table Bindings
  getFormBindings: (functionUnitId: number, formId: number) =>
    functionUnitAxios.get<any, { data: TableBinding[] }>(`/api/v1/function-units/${functionUnitId}/forms/${formId}/bindings`),
  
  createFormBinding: (functionUnitId: number, formId: number, data: TableBindingRequest) =>
    functionUnitAxios.post<any, { data: TableBinding }>(`/api/v1/function-units/${functionUnitId}/forms/${formId}/bindings`, data),
  
  updateFormBinding: (functionUnitId: number, formId: number, bindingId: number, data: TableBindingRequest) =>
    functionUnitAxios.put<any, { data: TableBinding }>(`/api/v1/function-units/${functionUnitId}/forms/${formId}/bindings/${bindingId}`, data),
  
  deleteFormBinding: (functionUnitId: number, formId: number, bindingId: number) =>
    functionUnitAxios.delete(`/api/v1/function-units/${functionUnitId}/forms/${formId}/bindings/${bindingId}`),

  // Export and Deploy
  exportFunctionUnit: (functionUnitId: number) =>
    functionUnitAxios.get(`/api/v1/function-units/${functionUnitId}/export`, { responseType: 'blob' }),
  
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
}

export interface DeployResponse {
  deploymentId: string
  status: 'PENDING' | 'DEPLOYING' | 'SUCCESS' | 'FAILED' | 'ROLLED_BACK'
  message?: string
  progress?: number
  steps?: DeployStep[]
  deployedAt?: string
}

export interface DeployStep {
  name: string
  status: string
  message?: string
  completedAt?: string
}
