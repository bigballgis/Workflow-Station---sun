<template>
  <section class="batch-assignment-editor">
    <h3>{{ t('subTable.batchAssign') }}</h3>
    <el-alert v-if="directory.error.value" :title="directory.error.value" type="error" :closable="false" />
    <div class="batch-assignment-editor__table">
      <el-table :data="displayedRows" border size="small" max-height="420" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="48" />
        <el-table-column type="index" width="58" />
        <el-table-column :label="t('subTable.assignMode')" min-width="180">
          <template #default="scope">
            <el-select v-model="rowIntent[rowKey(scope.row)].mode" @change="clearInactive(rowKey(scope.row))">
              <el-option v-if="config.allowUser" :label="t('subTable.assignByPerson')" value="person" />
              <el-option v-if="config.allowRole" :label="t('subTable.assignByRole')" value="role" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column v-if="config.allowUser" :label="t('subTable.assignByPerson')" min-width="250">
          <template #default="scope">
            <el-select v-if="rowIntent[rowKey(scope.row)].mode === 'person'" v-model="rowIntent[rowKey(scope.row)].assigneeId"
              filterable remote reserve-keyword clearable :remote-method="query => searchUsers(`row:${rowKey(scope.row)}`, query)" :loading="userLoading[`row:${rowKey(scope.row)}`] === true" style="width:100%">
              <el-option v-for="user in userOptions[`row:${rowKey(scope.row)}`]" :key="user.id" :label="user.name" :value="user.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column v-if="config.allowRole && config.buField" :label="t('subTable.selectBusinessUnit')" min-width="220">
          <template #default="scope">
            <el-cascader v-if="rowIntent[rowKey(scope.row)].mode === 'role'" v-model="rowIntent[rowKey(scope.row)].businessUnitId"
              :options="businessUnits" :props="buProps" clearable filterable style="width:100%"
              @change="value => selectBusinessUnit(rowKey(scope.row), value as string)" />
          </template>
        </el-table-column>
        <el-table-column v-if="config.allowRole" :label="t('subTable.selectRole')" min-width="220">
          <template #default="scope">
            <el-select v-if="rowIntent[rowKey(scope.row)].mode === 'role'" v-model="rowIntent[rowKey(scope.row)].roleCode"
              :disabled="!!config.buField && !rowIntent[rowKey(scope.row)].businessUnitId"
              :loading="roleLoading[rowKey(scope.row)] === true" clearable filterable style="width:100%">
              <el-option v-for="role in roleOptions[rowKey(scope.row)]" :key="role.value" :label="role.label" :value="role.value" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :page-size="20" :total="rows.length" layout="prev, pager, next" />
    </div>
    <el-divider />
    <el-form label-position="left" label-width="auto" class="batch-assignment-editor__uniform">
      <el-form-item :label="t('subTable.assignMode')">
        <el-select v-model="uniform.mode" @change="clearUniformInactive">
          <el-option :label="t('subTable.batchNoChange')" value="none" />
          <el-option v-if="config.allowUser" :label="t('subTable.assignByPerson')" value="person" />
          <el-option v-if="config.allowRole" :label="t('subTable.assignByRole')" value="role" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="uniform.mode === 'person'" :label="t('subTable.assignByPerson')">
        <el-select v-model="uniform.assigneeId" filterable remote reserve-keyword clearable :remote-method="query => searchUsers('uniform', query)" :loading="userLoading.uniform === true" style="width:100%">
          <el-option v-for="user in userOptions.uniform" :key="user.id" :label="user.name" :value="user.id" />
        </el-select>
      </el-form-item>
      <template v-if="uniform.mode === 'role'">
        <el-form-item v-if="config.buField" :label="t('subTable.selectBusinessUnit')">
          <el-cascader v-model="uniform.businessUnitId" :options="businessUnits" :props="buProps" clearable filterable style="width:100%" @change="value => selectUniformBusinessUnit(value as string)" />
        </el-form-item>
        <el-form-item :label="t('subTable.selectRole')">
          <el-select v-model="uniform.roleCode" :disabled="!!config.buField && !uniform.businessUnitId" :loading="uniformRoleLoading" clearable filterable style="width:100%">
            <el-option v-for="role in uniformRoleOptions" :key="role.value" :label="role.label" :value="role.value" />
          </el-select>
        </el-form-item>
      </template>
    </el-form>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BatchAssignmentCapability as SubTableBatchCapability } from './workspaceTypes'
import type { BatchAssignmentDirectory } from './assignmentDirectoryTypes'
import { assignmentStateChanged, collectBatchAssignments, initialBatchAssignment, type BatchAssignmentState, type BatchUniformAssignmentState } from './assignmentDraft'

type RoleOption = { label: string; value: string }
const props = defineProps<{ rows: Array<Record<string, unknown>>; capability: SubTableBatchCapability; rowIdentity: (row: Record<string, unknown>) => string | null; rowScopeKey: (row: Record<string, unknown>) => Record<string, unknown> | null; createDirectory: (t: (key: string) => string) => BatchAssignmentDirectory }>()
const { t } = useI18n()
const config = props.capability.assignmentConfig!
const page = ref(1)
const displayedRows = computed(() => props.rows.slice((page.value - 1) * 20, page.value * 20))
const selected = ref<Set<string>>(new Set())
const rowIntent = reactive<Record<string, BatchAssignmentState>>({})
const baselines: Record<string, BatchAssignmentState> = {}
const uniform = reactive<BatchUniformAssignmentState>({ mode: 'none', assigneeId: '', businessUnitId: '', businessUnitCode: '', roleCode: '' })
const directory = props.createDirectory(t)
const { userOptions, userLoading, searchUsers } = directory
const businessUnits = computed(() => directory.businessUnits.value)
const roleOptions = reactive<Record<string, RoleOption[]>>({})
const roleLoading = reactive<Record<string, boolean>>({})
const uniformRoleOptions = ref<RoleOption[]>([])
const uniformRoleLoading = ref(false)
const roleGenerations = new Map<string, number>()
const buProps = { value: 'id', label: 'name', children: 'children', checkStrictly: true, emitPath: false }

function rowKey(row: Record<string, unknown>) { return props.rowIdentity(row) ?? '' }
function clearInactive(key: string) {
  roleGenerations.set(key, (roleGenerations.get(key) ?? 0) + 1)
  roleLoading[key] = false
  const state = rowIntent[key]!
  if (state.mode === 'person') { state.businessUnitId = ''; state.businessUnitCode = ''; state.roleCode = '' }
  else state.assigneeId = ''
}
function clearUniformInactive() {
  uniformGeneration++
  uniformRoleLoading.value = false
  if (uniform.mode === 'person') { uniform.businessUnitId = ''; uniform.businessUnitCode = ''; uniform.roleCode = '' }
  else if (uniform.mode === 'role') uniform.assigneeId = ''
}
async function selectBusinessUnit(key: string, value: string) {
  const generation = (roleGenerations.get(key) ?? 0) + 1
  roleGenerations.set(key, generation)
  const state = rowIntent[key]!
  state.businessUnitId = value || ''; state.businessUnitCode = ''; state.roleCode = ''; roleOptions[key] = []
  if (!value) { roleLoading[key] = false; return }
  roleLoading[key] = true
  try {
    state.businessUnitCode = directory.requireBusinessUnitCode(String(value))
    const options = await directory.loadRoles(String(value))
    if (roleGenerations.get(key) === generation) roleOptions[key] = options
  } catch (error) { if (roleGenerations.get(key) === generation) directory.report(error) }
  finally { if (roleGenerations.get(key) === generation) roleLoading[key] = false }
}
let uniformGeneration = 0
async function selectUniformBusinessUnit(value: string) {
  const generation = ++uniformGeneration
  uniform.businessUnitId = value || ''; uniform.businessUnitCode = ''; uniform.roleCode = ''; uniformRoleOptions.value = []
  if (!value) { uniformRoleLoading.value = false; return }
  uniformRoleLoading.value = true
  try {
    uniform.businessUnitCode = directory.requireBusinessUnitCode(String(value))
    const options = await directory.loadRoles(String(value))
    if (uniformGeneration === generation) uniformRoleOptions.value = options
  } catch (error) { if (uniformGeneration === generation) directory.report(error) }
  finally { if (uniformGeneration === generation) uniformRoleLoading.value = false }
}
function onSelectionChange(rows: Array<Record<string, unknown>>) { selected.value = new Set(rows.map(rowKey).filter(Boolean)) }
watch(() => props.rows.map(rowKey), () => {
  for (const row of props.rows) {
    const key = rowKey(row)
    if (rowIntent[key]) continue
    baselines[key] = initialBatchAssignment(row, config)
    rowIntent[key] = { ...baselines[key] }
    roleOptions[key] = []
  }
  page.value = Math.min(page.value, Math.max(1, Math.ceil(props.rows.length / 20)))
}, { immediate: true })
async function loadDirectory() {
  if (!config.allowRole) return
  try {
    if (config.buField) {
      await directory.loadBusinessUnits()
      const pendingRoles = new Map<string, Promise<RoleOption[]>>()
      await Promise.all(props.rows.map(async row => {
        const key = rowKey(row)
        const state = rowIntent[key]!
        if (state.mode !== 'role' || !state.businessUnitCode || roleGenerations.has(key)) return
        const generation = 1
        roleGenerations.set(key, generation)
        state.businessUnitId = directory.requireBusinessUnitId(state.businessUnitCode)
        roleLoading[key] = true
        try {
          let pending = pendingRoles.get(state.businessUnitId)
          if (!pending) { pending = directory.loadRoles(state.businessUnitId); pendingRoles.set(state.businessUnitId, pending) }
          const options = await pending
          if (roleGenerations.get(key) === generation) roleOptions[key] = options
        } finally { if (roleGenerations.get(key) === generation) roleLoading[key] = false }
      }))
    }
    else {
      const options = await directory.loadRoles()
      uniformRoleOptions.value = options
      for (const row of props.rows) roleOptions[rowKey(row)] = [...options]
    }
  } catch (error) { directory.report(error) }
}
void loadDirectory()
function hasChanges() {
  return uniform.mode !== 'none' || props.rows.some(row => {
    const key = rowKey(row)
    return rowIntent[key] && baselines[key] && assignmentStateChanged(rowIntent[key]!, baselines[key]!)
  })
}
function collect(rows: Array<Record<string, unknown>>) {
  if (hasChanges() && directory.error.value) throw new Error(directory.error.value)
  return collectBatchAssignments(rows, rowIntent, baselines, uniform, selected.value, props.capability, props.rowIdentity, props.rowScopeKey)
}
defineExpose({ collect, hasChanges })
</script>

<style scoped>
.batch-assignment-editor__table { overflow-x: auto; }
.batch-assignment-editor__uniform { max-width: 680px; }
</style>
