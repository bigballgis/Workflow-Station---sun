import { defineStore } from 'pinia'
import { ref } from 'vue'
import { functionUnitApi, type FunctionUnit, type FunctionUnitResponse, type FunctionUnitRequest, type TableDefinition, type FormDefinition, type ActionDefinition, type ProcessDefinition, type Version, type ValidationResult } from '@/api/functionUnit'

export const useFunctionUnitStore = defineStore('functionUnit', () => {
  const list = ref<FunctionUnitResponse[]>([])
  const current = ref<FunctionUnit | null>(null)
  const loading = ref(false)
  const total = ref(0)

  // Tables, forms, actions for current function unit
  const tables = ref<TableDefinition[]>([])
  const forms = ref<FormDefinition[]>([])
  const actions = ref<ActionDefinition[]>([])
  const process = ref<ProcessDefinition | null>(null)
  const versions = ref<Version[]>([])

  async function fetchList(params: { name?: string; status?: string; page?: number; size?: number }) {
    loading.value = true
    try {
      const res = await functionUnitApi.list(params)
      console.log('[FunctionUnitStore] API response:', res)
      console.log('[FunctionUnitStore] Response type:', typeof res, 'isArray:', Array.isArray(res))
      
      // 后端返回格式: ApiResponse<Page<FunctionUnitResponse>>
      // ApiResponse: { success: true, data: Page }
      // Page: { content: [], totalElements: number, ... }
      // 拦截器已经返回了 response.data，所以 res 就是 ApiResponse
      
      if (res && res.success && res.data) {
        const pageData = res.data
        // pageData 是 Spring Data Page 对象
        if (pageData.content !== undefined) {
          list.value = pageData.content || []
          total.value = pageData.totalElements || 0
          console.log('[FunctionUnitStore] Successfully parsed:', {
            listCount: list.value.length,
            total: total.value,
            firstItem: list.value[0]
          })
        } else {
          console.warn('[FunctionUnitStore] Page data missing content:', pageData)
          list.value = []
          total.value = 0
        }
      } else if (res && res.data) {
        // 兼容处理：如果 data 直接是 Page 对象
        const pageData = res.data
        if (pageData.content !== undefined) {
          list.value = pageData.content || []
          total.value = pageData.totalElements || 0
          console.log('[FunctionUnitStore] Parsed (no success field):', {
            listCount: list.value.length,
            total: total.value
          })
        } else {
          console.warn('[FunctionUnitStore] Unexpected response format:', res)
          list.value = []
          total.value = 0
        }
      } else {
        console.warn('[FunctionUnitStore] Invalid response:', res)
        list.value = []
        total.value = 0
      }
    } catch (error) {
      console.error('[FunctionUnitStore] Error fetching list:', error)
      list.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  async function fetchById(id: number) {
    loading.value = true
    try {
      const res = await functionUnitApi.getById(id)
      current.value = res.data
    } finally {
      loading.value = false
    }
  }

  async function create(data: FunctionUnitRequest) {
    const res = await functionUnitApi.create(data)
    return res.data
  }

  async function update(id: number, data: FunctionUnitRequest) {
    const res = await functionUnitApi.update(id, data)
    return res.data
  }

  async function remove(id: number) {
    await functionUnitApi.delete(id)
  }

  async function publish(id: number, changeLog?: string) {
    const res = await functionUnitApi.publish(id, changeLog)
    return res.data
  }

  async function clone(id: number, newName: string) {
    const res = await functionUnitApi.clone(id, newName)
    return res.data
  }

  async function validate(id: number): Promise<ValidationResult> {
    const res = await functionUnitApi.validate(id)
    return res.data
  }

  // Table operations
  async function fetchTables(functionUnitId: number) {
    const res = await functionUnitApi.getTables(functionUnitId)
    tables.value = res.data
    return res.data
  }

  async function createTable(functionUnitId: number, data: Partial<TableDefinition>) {
    const res = await functionUnitApi.createTable(functionUnitId, data)
    return res.data
  }

  async function updateTable(functionUnitId: number, tableId: number, data: Partial<TableDefinition>) {
    const res = await functionUnitApi.updateTable(functionUnitId, tableId, data)
    return res.data
  }

  async function deleteTable(functionUnitId: number, tableId: number) {
    await functionUnitApi.deleteTable(functionUnitId, tableId)
  }

  // Form operations
  async function fetchForms(functionUnitId: number) {
    const res = await functionUnitApi.getForms(functionUnitId)
    forms.value = res.data
    return res.data
  }

  async function createForm(functionUnitId: number, data: Partial<FormDefinition>) {
    const res = await functionUnitApi.createForm(functionUnitId, data)
    return res.data
  }

  async function updateForm(functionUnitId: number, formId: number, data: Partial<FormDefinition>) {
    const res = await functionUnitApi.updateForm(functionUnitId, formId, data)
    return res.data
  }

  async function deleteForm(functionUnitId: number, formId: number) {
    await functionUnitApi.deleteForm(functionUnitId, formId)
  }

  // Action operations
  async function fetchActions(functionUnitId: number) {
    const res = await functionUnitApi.getActions(functionUnitId)
    actions.value = res.data
    return res.data
  }

  async function createAction(functionUnitId: number, data: Partial<ActionDefinition>) {
    const res = await functionUnitApi.createAction(functionUnitId, data)
    return res.data
  }

  async function updateAction(functionUnitId: number, actionId: number, data: Partial<ActionDefinition>) {
    const res = await functionUnitApi.updateAction(functionUnitId, actionId, data)
    return res.data
  }

  async function deleteAction(functionUnitId: number, actionId: number) {
    await functionUnitApi.deleteAction(functionUnitId, actionId)
  }

  // Process operations
  async function fetchProcess(functionUnitId: number) {
    try {
      const res = await functionUnitApi.getProcess(functionUnitId)
      process.value = res.data
      return res.data
    } catch {
      process.value = null
      return null
    }
  }

  async function saveProcess(functionUnitId: number, data: Partial<ProcessDefinition>) {
    const res = await functionUnitApi.saveProcess(functionUnitId, data)
    process.value = res.data
    return res.data
  }

  // Version operations
  async function fetchVersions(functionUnitId: number) {
    const res = await functionUnitApi.getVersions(functionUnitId)
    versions.value = res.data
    return res.data
  }

  async function rollback(functionUnitId: number, versionId: number) {
    const res = await functionUnitApi.rollback(functionUnitId, versionId)
    return res.data
  }

  return { 
    list, current, loading, total, tables, forms, actions, process, versions,
    fetchList, fetchById, create, update, remove, publish, clone, validate,
    fetchTables, createTable, updateTable, deleteTable,
    fetchForms, createForm, updateForm, deleteForm,
    fetchActions, createAction, updateAction, deleteAction,
    fetchProcess, saveProcess,
    fetchVersions, rollback
  }
})
