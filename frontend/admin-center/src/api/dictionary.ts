import { get, post, put, del } from './request'

// ==================== 类型定义 ====================

export interface Dictionary {
  id: string
  name: string
  code: string
  type: 'SYSTEM' | 'BUSINESS' | 'CUSTOM'
  description?: string
  status: 'ACTIVE' | 'INACTIVE'
  version: number
  createdAt: string
  createdBy?: string
  updatedAt: string
  updatedBy?: string
}

export interface DictionaryItem {
  id: string
  dictionaryId: string
  code: string
  nameEn: string
  nameZhCn?: string
  nameZhTw?: string
  parentItemId?: string
  sortOrder: number
  status: 'ACTIVE' | 'INACTIVE'
  validFrom?: string
  validTo?: string
  extraData?: Record<string, any>
  createdAt?: string
  updatedAt?: string
}

export interface DictionaryItemLocalized {
  id: string
  code: string
  name: string
  parentItemId?: string
  sortOrder: number
  status: string
}

export interface DictionaryVersion {
  id: string
  dictionaryId: string
  version: number
  snapshot: string
  changedBy: string
  changedAt: string
  changeReason?: string
}

export interface DictionaryCreateRequest {
  name: string
  code: string
  type: 'SYSTEM' | 'BUSINESS' | 'CUSTOM'
  description?: string
}

export interface DictionaryUpdateRequest {
  name?: string
  description?: string
}

export interface DictionaryItemRequest {
  code: string
  nameEn: string
  nameZhCn?: string
  nameZhTw?: string
  parentItemId?: string
  sortOrder?: number
  validFrom?: string
  validTo?: string
  extraData?: Record<string, any>
}

// ==================== 字典 CRUD API ====================

export const dictionaryApi = {
  // 获取字典列表
  list: (type?: string, status?: string) =>
    get<Dictionary[]>('/dictionaries', { params: { type, status } }),

  // 分页获取字典列表
  listPaged: (page = 0, size = 20) =>
    get<any>('/dictionaries/page', { params: { page, size } }),

  // 根据ID获取字典
  getById: (id: string) =>
    get<Dictionary>(`/dictionaries/${id}`),

  // 根据代码获取字典
  getByCode: (code: string) =>
    get<Dictionary>(`/dictionaries/code/${code}`),

  // 搜索字典
  search: (keyword: string) =>
    get<Dictionary[]>('/dictionaries/search', { params: { keyword } }),

  // 创建字典
  create: (data: DictionaryCreateRequest) =>
    post<Dictionary>('/dictionaries', data),

  // 更新字典
  update: (id: string, data: DictionaryUpdateRequest) =>
    put<Dictionary>(`/dictionaries/${id}`, data),

  // 删除字典
  delete: (id: string) =>
    del<void>(`/dictionaries/${id}`),

  // 启用字典
  activate: (id: string) =>
    post<Dictionary>(`/dictionaries/${id}/activate`),

  // 禁用字典
  deactivate: (id: string) =>
    post<Dictionary>(`/dictionaries/${id}/deactivate`),

  // ==================== 字典项管理 API ====================

  // 获取字典项列表
  getItems: (dictionaryId: string) =>
    get<DictionaryItem[]>(`/dictionaries/${dictionaryId}/items`),

  // 获取有效字典项
  getValidItems: (dictionaryId: string) =>
    get<DictionaryItem[]>(`/dictionaries/${dictionaryId}/items/valid`),

  // 获取本地化字典项
  getLocalizedItems: (dictionaryId: string, language = 'zh-CN') =>
    get<DictionaryItemLocalized[]>(`/dictionaries/${dictionaryId}/items/localized`, {
      params: { language }
    }),

  // 创建字典项
  createItem: (dictionaryId: string, data: DictionaryItemRequest) =>
    post<DictionaryItem>(`/dictionaries/${dictionaryId}/items`, data),

  // 更新字典项
  updateItem: (itemId: string, data: DictionaryItemRequest) =>
    put<DictionaryItem>(`/dictionaries/items/${itemId}`, data),

  // 删除字典项
  deleteItem: (itemId: string) =>
    del<void>(`/dictionaries/items/${itemId}`),

  // 更新字典项翻译
  updateItemTranslations: (itemId: string, translations: Record<string, string>) =>
    put<void>(`/dictionaries/items/${itemId}/translations`, translations),

  // ==================== 版本管理 API ====================

  // 获取版本历史
  getVersionHistory: (dictionaryId: string) =>
    get<DictionaryVersion[]>(`/dictionaries/${dictionaryId}/versions`),

  // 回滚到指定版本
  rollback: (dictionaryId: string, version: number) =>
    post<Dictionary>(`/dictionaries/${dictionaryId}/rollback/${version}`)
}
