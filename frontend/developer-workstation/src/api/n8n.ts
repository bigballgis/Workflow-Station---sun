import axios from 'axios'

/**
 * N8N API module for developer-workstation
 * Provides access to N8N connection configs and workflow lists via admin-center proxy
 */

const adminCenterAxios = axios.create({
  baseURL: '/api/v1/admin',
  timeout: 30000,
  withCredentials: true
})

adminCenterAxios.interceptors.response.use(
  response => response.data,
  error => Promise.reject(error)
)

/** N8N connection config */
export interface N8nConfig {
  id: string
  name: string
  baseUrl: string
  isActive: boolean
  createdAt?: string
  updatedAt?: string
}

/** N8N workflow */
export interface N8nWorkflow {
  id: string
  name: string
  active: boolean
  tags?: string[]
  createdAt?: string
  webhookUrl?: string
}

/** N8N task config for BPMN serialization */
export interface N8nTaskConfig {
  configId: string
  workflowId: string
  webhookUrl: string
  timeoutSeconds: number
  retryCount: number
  inputMapping: VariableMapping[]
  outputMapping: VariableMapping[]
}

/** Variable mapping entry */
export interface VariableMapping {
  source: string
  target: string
}

export const n8nApi = {
  /** Get all N8N connection configs */
  getConfigs: (): Promise<N8nConfig[]> =>
    adminCenterAxios.get('/n8n-config'),

  /** Get N8N workflows for a specific config */
  getWorkflows: (configId: string): Promise<N8nWorkflow[]> =>
    adminCenterAxios.get(`/n8n-config/${configId}/workflows`),
}
