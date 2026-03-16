import api from './index'

export interface CommonFieldDefinition {
  id?: number
  fieldName: string
  displayName?: string
  dataType: string
  length?: number
  isPrimaryKey?: boolean
  nullable?: boolean
  isUnique?: boolean
  defaultValue?: string
  description?: string
  sortOrder?: number
}

export interface CommonTableDefinition {
  id: number
  code: string
  name: string
  description?: string
  status: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  fieldDefinitions: CommonFieldDefinition[]
}

export interface CommonTableRequest {
  code: string
  name: string
  description?: string
  status?: string
  fields?: CommonFieldDefinition[]
}

const BASE = '/common-tables'

export const commonTableApi = {
  list(): Promise<{ success: boolean; data: CommonTableDefinition[] }> {
    return api.get(BASE)
  },

  getById(id: number): Promise<{ success: boolean; data: CommonTableDefinition }> {
    return api.get(`${BASE}/${id}`)
  },

  getByCode(code: string): Promise<{ success: boolean; data: CommonTableDefinition }> {
    return api.get(`${BASE}/by-code/${code}`)
  },

  create(data: CommonTableRequest): Promise<{ success: boolean; data: CommonTableDefinition }> {
    return api.post(BASE, data)
  },

  update(id: number, data: CommonTableRequest): Promise<{ success: boolean; data: CommonTableDefinition }> {
    return api.put(`${BASE}/${id}`, data)
  },

  delete(id: number): Promise<{ success: boolean }> {
    return api.delete(`${BASE}/${id}`)
  }
}
