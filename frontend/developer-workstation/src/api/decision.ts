import api from '@/api'

// Decision Definition interfaces
export interface DecisionDefinition {
  id: number
  decisionKey: string
  decisionName: string
  dmnXml: string
  hitPolicy: string
  description?: string
  createdAt: string
  updatedAt?: string
}

export interface DecisionDefinitionRequest {
  decisionKey: string
  decisionName: string
  dmnXml: string
  hitPolicy?: string
  description?: string
}

export interface DecisionTableModel {
  inputs: DecisionColumnDef[]
  outputs: DecisionColumnDef[]
  rules: DecisionRuleModel[]
  hitPolicy: string
}

export interface DecisionColumnDef {
  id: string
  label: string
  expression?: string
  typeRef?: string
}

export interface DecisionRuleModel {
  inputEntries: string[]
  outputEntries: string[]
  description?: string
}

export interface DecisionValidationResult {
  valid: boolean
  errors: string[]
  warnings: string[]
}

export const decisionApi = {
  list: (functionUnitId: number) =>
    api.get<any, { data: DecisionDefinition[] }>(`/function-units/${functionUnitId}/decisions`),

  getById: (functionUnitId: number, decisionId: number) =>
    api.get<any, { data: DecisionDefinition }>(`/function-units/${functionUnitId}/decisions/${decisionId}`),

  create: (functionUnitId: number, data: DecisionDefinitionRequest) =>
    api.post<any, { data: DecisionDefinition }>(`/function-units/${functionUnitId}/decisions`, data),

  update: (functionUnitId: number, decisionId: number, data: DecisionDefinitionRequest) =>
    api.put<any, { data: DecisionDefinition }>(`/function-units/${functionUnitId}/decisions/${decisionId}`, data),

  delete: (functionUnitId: number, decisionId: number) =>
    api.delete(`/function-units/${functionUnitId}/decisions/${decisionId}`),

  validate: (functionUnitId: number, decisionId: number) =>
    api.get<any, { data: DecisionValidationResult }>(`/function-units/${functionUnitId}/decisions/${decisionId}/validate`),

  getModel: (functionUnitId: number, decisionId: number) =>
    api.get<any, { data: DecisionTableModel }>(`/function-units/${functionUnitId}/decisions/${decisionId}/model`),

  updateModel: (functionUnitId: number, decisionId: number, model: DecisionTableModel) =>
    api.post<any, { data: DecisionDefinition }>(`/function-units/${functionUnitId}/decisions/${decisionId}/model`, model),
}
