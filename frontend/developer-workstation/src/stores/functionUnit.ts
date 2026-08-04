import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { functionUnitApi, type FunctionUnit, type FunctionUnitResponse, type FunctionUnitRequest, type TableDefinition, type FormDefinition, type ActionDefinition, type ProcessDefinition, type Version, type ValidationResult } from '@/api/functionUnit'
import { sortFormsByType } from '@/utils/formDesigner'
import i18n from '@/i18n'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'

export const useFunctionUnitStore = defineStore('functionUnit', () => {
  const list = ref<FunctionUnitResponse[]>([])
  const current = ref<FunctionUnit | null>(null)
  const loading = ref(false)
  // Distinguishes "the load failed" from "there genuinely are no function units":
  // a failed request must not masquerade as an empty list (misleading empty state).
  const loadError = ref(false)
  const total = ref(0)
  const allTags = ref<string[]>([])

  // Tables, forms, actions for current function unit
  const tables = ref<TableDefinition[]>([])
  const forms = ref<FormDefinition[]>([])
  const actions = ref<ActionDefinition[]>([])
  const process = ref<ProcessDefinition | null>(null)
  const versions = ref<Version[]>([])

  // The list page paginates client-side over the Launchpad layout (a folder is one tile,
  // and the layout is a single global ordering), so the whole list has to be in hand.
  // Fetched in chunks rather than one huge request; the cap only guards against a runaway loop.
  const FETCH_ALL_CHUNK = 200
  const FETCH_ALL_MAX_CHUNKS = 25

  async function fetchAll(params: { name?: string; status?: string; tags?: string[] } = {}) {
    loading.value = true
    loadError.value = false
    try {
      const acc: FunctionUnitResponse[] = []
      let totalElements = 0
      for (let page = 0; page < FETCH_ALL_MAX_CHUNKS; page++) {
        // Default sort by name ascending (A→Z) at API level
        const res = await functionUnitApi.list({ sort: 'name,asc', ...params, page, size: FETCH_ALL_CHUNK })
        const pageData = res?.data
        const content = pageData?.content ?? []
        acc.push(...content)
        totalElements = pageData?.totalElements ?? acc.length
        if (content.length === 0 || acc.length >= totalElements) break
      }
      if (acc.length < totalElements) {
        // Never pretend a truncated list is the whole list
        console.warn(
          `[functionUnit] loaded only ${acc.length}/${totalElements} function units ` +
          `(cap ${FETCH_ALL_CHUNK * FETCH_ALL_MAX_CHUNKS}); the list page is showing a partial layout`
        )
      }
      list.value = acc
      total.value = totalElements
    } catch (e) {
      // Surface the failure instead of silently showing an empty list — a transient
      // failure (e.g. token race on first load) previously looked like "no data".
      loadError.value = true
      list.value = []
      total.value = 0
      const t = i18n.global.t as (key: string) => string
      ElMessage.error(resolveUserFacingHttpMessage(e, t) || t('functionUnit.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  async function fetchAllTags() {
    try {
      const res = await functionUnitApi.getAllTags()
      allTags.value = res.data ?? []
    } catch {
      allTags.value = []
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

  async function restore(id: number) {
    const res = await functionUnitApi.restore(id)
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
    const updated = res.data
    if (updated) {
      const index = tables.value.findIndex(t => t.id === tableId)
      if (index >= 0) {
        tables.value[index] = updated
      } else {
        tables.value.push(updated)
      }
    }
    return updated
  }

  async function deleteTable(functionUnitId: number, tableId: number) {
    await functionUnitApi.deleteTable(functionUnitId, tableId)
  }

  // Form operations
  async function fetchForms(functionUnitId: number) {
    const res = await functionUnitApi.getForms(functionUnitId)
    forms.value = sortFormsByType(res.data ?? [])
    return forms.value
  }

  async function createForm(functionUnitId: number, data: Partial<FormDefinition>) {
    const res = await functionUnitApi.createForm(functionUnitId, data)
    return res.data
  }

  async function updateForm(functionUnitId: number, formId: number, data: Partial<FormDefinition>) {
    const res = await functionUnitApi.updateForm(functionUnitId, formId, data)
    const updated = res.data
    const index = forms.value.findIndex(form => form.id === formId)
    if (index >= 0) {
      forms.value[index] = updated
    }
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

  async function updateAction(functionUnitId: number, actionId: string | number, data: Partial<ActionDefinition>) {
    const res = await functionUnitApi.updateAction(functionUnitId, actionId, data)
    return res.data
  }

  async function deleteAction(functionUnitId: number, actionId: string | number) {
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

  async function saveProcess(
    functionUnitId: number,
    data: Partial<ProcessDefinition>,
    options?: { allowEmpty?: boolean }
  ) {
    const res = await functionUnitApi.saveProcess(functionUnitId, data, options)
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

  async function refreshAll(functionUnitId: number) {
    await Promise.all([
      fetchById(functionUnitId),
      fetchTables(functionUnitId),
      fetchForms(functionUnitId),
      fetchActions(functionUnitId),
      fetchProcess(functionUnitId)
    ])
  }

  return {
    list, current, loading, loadError, total, allTags, tables, forms, actions, process, versions,
    fetchAll, fetchById, create, update, remove, restore, clone, validate,
    fetchAllTags,
    fetchTables, createTable, updateTable, deleteTable,
    fetchForms, createForm, updateForm, deleteForm,
    fetchActions, createAction, updateAction, deleteAction,
    fetchProcess, saveProcess,
    fetchVersions, rollback, refreshAll
  }
})
