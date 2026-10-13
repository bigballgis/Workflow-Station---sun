<template>
  <el-dialog
    :model-value="visible"
    :title="t('subTable.batchEdit')"
    width="min(1120px, calc(100vw - 32px))"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <p class="batch-assign-dialog__hint">{{ t('subTable.batchEditDraftHint') }}</p>
    <el-table :data="draftRows" border size="small" max-height="420" @selection-change="onSelectionChange">
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
          <el-select
            v-if="rowIntent[rowKey(scope.row)].mode === 'person'"
            v-model="rowIntent[rowKey(scope.row)].assigneeId"
            filterable remote reserve-keyword clearable
            :remote-method="searchUsers"
            :loading="userLoading"
            style="width:100%"
          >
            <el-option v-for="user in userOptions" :key="user.id" :label="user.name" :value="user.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column v-if="config.allowRole" :label="t('subTable.selectBusinessUnit')" min-width="220">
        <template #default="scope">
          <el-cascader
            v-if="rowIntent[rowKey(scope.row)].mode === 'role' && config.buField"
            v-model="rowIntent[rowKey(scope.row)].businessUnitId"
            :options="businessUnits"
            :props="buProps"
            clearable filterable style="width:100%"
            @change="value => selectBusinessUnit(rowKey(scope.row), value as string)"
          />
        </template>
      </el-table-column>
      <el-table-column v-if="config.allowRole" :label="t('subTable.selectRole')" min-width="220">
        <template #default="scope">
          <el-select
            v-if="rowIntent[rowKey(scope.row)].mode === 'role'"
            v-model="rowIntent[rowKey(scope.row)].roleCode"
            :disabled="!!config.buField && !rowIntent[rowKey(scope.row)].businessUnitId"
            :loading="roleLoading[rowKey(scope.row)] === true"
            clearable filterable style="width:100%"
          >
            <el-option v-for="role in roleOptions[rowKey(scope.row)] || []" :key="role.value" :label="role.label" :value="role.value" />
          </el-select>
        </template>
      </el-table-column>
    </el-table>

    <el-divider />
    <el-form label-position="left" label-width="140px" class="batch-assign-dialog__uniform">
      <el-form-item :label="t('subTable.assignMode')">
        <el-select v-model="uniform.mode" @change="clearUniformInactive">
          <el-option :label="t('subTable.batchNoChange')" value="none" />
          <el-option v-if="config.allowUser" :label="t('subTable.assignByPerson')" value="person" />
          <el-option v-if="config.allowRole" :label="t('subTable.assignByRole')" value="role" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="uniform.mode === 'person'" :label="t('subTable.assignByPerson')">
        <el-select v-model="uniform.assigneeId" filterable remote reserve-keyword clearable :remote-method="searchUsers" :loading="userLoading" style="width:100%">
          <el-option v-for="user in userOptions" :key="user.id" :label="user.name" :value="user.id" />
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

    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="apply">{{ t('subTable.batchApply') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { userApi, type UserOption } from '@/api/user'
import { permissionApi, type BusinessUnit } from '@/api/permission'
import type { AssignmentConfig } from '@/utils/miAssignmentConfig'
import { applyBatchAssignment, type BatchAssignmentIntent } from '@/utils/subTableBatchDraft'
import type { SubTableBatchCapability } from '@/utils/subTableBatchCapability'

type IntentState = { mode: 'person' | 'role'; assigneeId: string; businessUnitId: string; businessUnitCode: string; roleCode: string }
type RoleOption = { label: string; value: string }
const props = defineProps<{ visible: boolean; rows: Array<Record<string, unknown>>; capability: SubTableBatchCapability; rowIdentity: (row: Record<string, unknown>) => string | null; rowScopeKey: (row: Record<string, unknown>) => Record<string, unknown> | null }>()
const emit = defineEmits<{ (event: 'update:visible', value: boolean): void; (event: 'apply', rows: Array<Record<string, unknown>>, change: unknown): void }>()
const { t } = useI18n()
const config = props.capability.assignmentConfig as AssignmentConfig
const draftRows = ref<Array<Record<string, unknown>>>([])
const selected = ref<Set<string>>(new Set())
const rowIntent = reactive<Record<string, IntentState>>({})
const uniform = reactive<{ mode: 'none' | 'person' | 'role'; assigneeId: string; businessUnitId: string; businessUnitCode: string; roleCode: string }>({ mode: 'none', assigneeId: '', businessUnitId: '', businessUnitCode: '', roleCode: '' })
const userOptions = ref<UserOption[]>([])
const userLoading = ref(false)
const businessUnits = ref<BusinessUnit[]>([])
const roleOptions = reactive<Record<string, RoleOption[]>>({})
const roleLoading = reactive<Record<string, boolean>>({})
const uniformRoleOptions = ref<RoleOption[]>([])
const uniformRoleLoading = ref(false)
const buIdToCode = new Map<string, string>()
const buProps = { value: 'id', label: 'name', children: 'children', checkStrictly: true, emitPath: false }

function rowKey(row: Record<string, unknown>) { return props.rowIdentity(row) ?? '' }
function configuredMode(row: Record<string, unknown>): 'person' | 'role' { return config.allowRole && config.roleField && row[config.roleField] ? 'role' : 'person' }
function clearInactive(key: string) { const value = rowIntent[key]; if (!value) return; if (value.mode === 'person') { value.businessUnitId = ''; value.businessUnitCode = ''; value.roleCode = '' } else value.assigneeId = '' }
function clearUniformInactive() { if (uniform.mode === 'person') { uniform.businessUnitId = ''; uniform.businessUnitCode = ''; uniform.roleCode = '' } else if (uniform.mode === 'role') uniform.assigneeId = '' }
function indexBusinessUnits(items: BusinessUnit[]) { for (const item of items) { buIdToCode.set(String(item.id), item.code?.trim() || String(item.id)); if (item.children) indexBusinessUnits(item.children) } }
async function loadBusinessUnits() { if (businessUnits.value.length || !config.allowRole || !config.buField) return; const result = await permissionApi.getBusinessUnitsTree({ skipGlobalErrorHandler: true }) as unknown as { data?: BusinessUnit[] } | BusinessUnit[]; businessUnits.value = Array.isArray(result) ? result : result.data ?? []; indexBusinessUnits(businessUnits.value) }
async function loadUnboundRoles() { if (!config.allowRole || config.buField || uniformRoleOptions.value.length) return; const result = await permissionApi.getAvailableRoles() as unknown as { data?: Array<{ code: string; name?: string }> } | Array<{ code: string; name?: string }>; const roles = Array.isArray(result) ? result : result.data ?? []; const options = roles.filter(role => !!role.code).map(role => ({ value: role.code, label: role.name || role.code })); uniformRoleOptions.value = options; for (const row of draftRows.value) roleOptions[rowKey(row)] = [...options] }
async function loadRoles(buId: string, target: RoleOption[], loading: { value: boolean } | null) { if (!buId) return; if (loading) loading.value = true; const result = await permissionApi.getBusinessUnitRoles(buId, { skipGlobalErrorHandler: true }) as unknown as { data?: Array<{ code: string; name?: string }> } | Array<{ code: string; name?: string }>; const roles = Array.isArray(result) ? result : result.data ?? []; target.splice(0, target.length, ...roles.filter(role => !!role.code).map(role => ({ value: role.code, label: role.name || role.code }))); if (loading) loading.value = false }
async function selectBusinessUnit(key: string, value: string) { const state = rowIntent[key]; state.businessUnitId = value || ''; state.businessUnitCode = value ? buIdToCode.get(String(value)) || String(value) : ''; state.roleCode = ''; roleOptions[key] = []; if (!value) return; roleLoading[key] = true; try { await loadRoles(String(value), roleOptions[key], null) } finally { roleLoading[key] = false } }
async function selectUniformBusinessUnit(value: string) { uniform.businessUnitId = value || ''; uniform.businessUnitCode = value ? buIdToCode.get(String(value)) || String(value) : ''; uniform.roleCode = ''; uniformRoleOptions.value = []; if (!value) return; uniformRoleLoading.value = true; try { await loadRoles(String(value), uniformRoleOptions.value, null) } finally { uniformRoleLoading.value = false } }
let latestSearch = 0
async function searchUsers(query: string) { const sequence = ++latestSearch; userLoading.value = true; try { const users = await userApi.searchUsers(query); if (sequence === latestSearch) userOptions.value = users } finally { if (sequence === latestSearch) userLoading.value = false } }
function intentFor(value: IntentState): BatchAssignmentIntent | null { if (value.mode === 'person') return value.assigneeId ? { mode: 'person', assigneeId: value.assigneeId } : null; return value.roleCode ? { mode: 'role', roleCode: value.roleCode, ...(config.buField ? { businessUnitCode: value.businessUnitCode } : {}) } : null }
function fieldsFor() { return [config.assigneeField, config.roleField, config.buField].filter((field): field is string => !!field) }
function onSelectionChange(rows: Array<Record<string, unknown>>) { selected.value = new Set(rows.map(rowKey).filter(Boolean)) }
function reset() { draftRows.value = props.rows.map(row => ({ ...row })); for (const key of Object.keys(rowIntent)) delete rowIntent[key]; for (const row of draftRows.value) { const key = rowKey(row); rowIntent[key] = { mode: configuredMode(row), assigneeId: config.assigneeField ? String(row[config.assigneeField] ?? '') : '', businessUnitId: '', businessUnitCode: config.buField ? String(row[config.buField] ?? '') : '', roleCode: config.roleField ? String(row[config.roleField] ?? '') : '' } } selected.value = new Set(); uniform.mode = 'none'; uniform.assigneeId = ''; uniform.businessUnitId = ''; uniform.businessUnitCode = ''; uniform.roleCode = ''; loadBusinessUnits(); loadUnboundRoles() }
watch(() => props.visible, visible => { if (visible) reset() })
function apply() { const intents = new Map<string, BatchAssignmentIntent>(); for (const row of draftRows.value) { const key = rowKey(row); const intent = intentFor(rowIntent[key]); if (intent) intents.set(key, intent) } if (uniform.mode !== 'none') { const intent = intentFor(uniform as IntentState); if (!intent) { ElMessage.error(t('subTable.requiredField')); return } for (const key of selected.value) intents.set(key, intent) } if (!intents.size) return; const nextRows = props.rows.map(row => { const intent = intents.get(rowKey(row)); return intent ? applyBatchAssignment(row, config, intent) : row }); const rowPatches = nextRows.flatMap((row, index) => { const rowId = rowKey(props.rows[index]); const intent = intents.get(rowId); const rowKeyValue = props.rowScopeKey(props.rows[index]); if (!intent || !rowId || !rowKeyValue) return []; return [{ rowId, rowKey: rowKeyValue, fields: fieldsFor(), assignment: intent.mode === 'person' ? { mode: 'PERSON', assigneeId: intent.assigneeId } : { mode: 'ROLE', roleCode: intent.roleCode, ...(intent.businessUnitCode ? { businessUnitCode: intent.businessUnitCode } : {}) } }]; }); if (!rowPatches.length) { ElMessage.error(t('subTable.batchEditDraftHint')); return } emit('apply', nextRows, { batchControlId: props.capability.batchControlId, bindingId: props.capability.bindingId, operation: 'ASSIGN', rowPatches }); emit('update:visible', false) }
</script>

<style scoped>
.batch-assign-dialog__hint { margin-top: 0; color: var(--el-text-color-secondary); }
.batch-assign-dialog__uniform { max-width: 680px; }
</style>
