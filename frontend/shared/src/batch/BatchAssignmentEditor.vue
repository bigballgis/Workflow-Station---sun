<template>
  <section class="batch-assignment-editor">
    <el-alert v-if="directory.error.value" :title="directory.error.value" type="error" :closable="false" />
    <BatchAssignmentModeFrame :config="config" :mode="uniform.mode" :disabled="assignmentLocked" @select="selectMode">
      <BatchDesignedForm :rules="designedRules" :options="options" :readonly-field="assignmentReadonly" :adapted-fields="ownedFields">
        <template #field="{ rule: fieldRule, update }">
          <BatchAssignmentInput :rule="fieldRule" :config="config" :uniform="uniform" :disabled="assignmentReadonly(fieldRule.field)"
            :directory="directory" :user-options="userOptionsFor('uniform', uniform.assigneeId)" :user-loading="userLoading.uniform === true"
            :role-options="uniformRoleOptions" :role-loading="uniformRoleLoading" @search="query => searchUsers('uniform', query)"
            @change="update" @business-unit="value => updateDesignedBusinessUnit(value, update)" />
        </template>
      </BatchDesignedForm>
    </BatchAssignmentModeFrame>
  </section>
</template>
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import formCreate from '@form-create/element-ui'
import { useI18n } from 'vue-i18n'
import type { BatchAssignmentCapability } from './workspaceTypes'
import type { BatchAssignmentDirectory } from './assignmentDirectoryTypes'
import { useSelectedAssignmentUsers } from './useSelectedAssignmentUsers'
import { collectBatchAssignments, initialBatchAssignment, type BatchUniformAssignmentState } from './assignmentDraft'
import BatchAssignmentModeFrame from './BatchAssignmentModeFrame.vue'
import BatchDesignedForm from './BatchDesignedForm.vue'
import BatchAssignmentInput from './BatchAssignmentInput.vue'
import { batchRuleChildren } from './batchFormRules'
import { assignmentControlRules } from './assignmentControlRules'
const props = defineProps<{ rule?: Record<string, any>; options?: Record<string, any>; disabled?: boolean; rows: Array<Record<string, unknown>>; capability: BatchAssignmentCapability; fieldWriteModes?: Record<string, 'EDITABLE' | 'READONLY'>; rowIdentity: (row: Record<string, unknown>) => string | null; rowScopeKey: (row: Record<string, unknown>) => Record<string, unknown> | null; createDirectory: (t: (key: string) => string) => BatchAssignmentDirectory }>()
const { t } = useI18n()
const config = props.capability.assignmentConfig!
const uniform = reactive<BatchUniformAssignmentState>({ mode: 'none', assigneeId: '', businessUnitId: '', businessUnitCode: '', roleCode: '' })
const ownedFields = [config.assigneeField, config.buField, config.roleField].filter((field): field is string => !!field)
const designedRules = computed(() => {
  const rules = assignmentControlRules(formCreate.copyRules(batchRuleChildren(props.rule ?? {})), config, t)
  function visit(items: Array<Record<string, any>>) {
    for (const rule of items) {
      const active = uniform.mode === 'person' ? rule.field === config.assigneeField
        : uniform.mode === 'role' && (rule.field === config.buField || rule.field === config.roleField)
      if (ownedFields.includes(rule.field) && !active) rule.hidden = true
      visit(batchRuleChildren(rule))
    }
  }
  visit(rules)
  return rules
})
const assignmentLocked = computed(() => props.disabled === true || ownedFields.some(field => props.fieldWriteModes?.[field] === 'READONLY'))
function assignmentReadonly(field: string) {
  return assignmentLocked.value || props.fieldWriteModes?.[field] === 'READONLY'
}
function selectMode(mode: 'person' | 'role') {
  if (assignmentLocked.value || (mode === 'person' ? !config.allowUser : !config.allowRole)) return
  uniform.mode = uniform.mode === mode ? 'none' : mode
  clearUniformInactive()
}
const directory = props.createDirectory(t)
const { userLoading, searchUsers } = directory
const uniformRoleOptions = ref<Array<{ label: string; value: string }>>([])
const uniformRoleLoading = ref(false)
let uniformGeneration = 0
function clearUniformInactive() {
  uniformGeneration++
  uniformRoleLoading.value = false
  uniform.assigneeId = ''; uniform.businessUnitId = ''; uniform.businessUnitCode = ''; uniform.roleCode = ''
  if (config.buField) uniformRoleOptions.value = []
}
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
async function updateDesignedBusinessUnit(value: string, update: (value: unknown) => void) {
  await selectUniformBusinessUnit(value)
  update(uniform.businessUnitCode)
}
const { userOptionsFor } = useSelectedAssignmentUsers(directory, computed(() => [uniform.assigneeId]))
async function loadDirectory() {
  if (!config.allowRole) return
  try {
    if (config.buField) await directory.loadBusinessUnits()
    else uniformRoleOptions.value = await directory.loadRoles()
  } catch (error) { directory.report(error) }
}
void loadDirectory()
function hasChanges() { return !assignmentLocked.value && uniform.mode !== 'none' }
function collect(rows: Array<Record<string, unknown>>) {
  if (assignmentLocked.value) return { rows }
  if (hasChanges() && directory.error.value) throw new Error(directory.error.value)
  const states = Object.fromEntries(rows.map(row => [props.rowIdentity(row)!, initialBatchAssignment(row, config)]))
  return collectBatchAssignments(rows, states, states, uniform, new Set(props.rows.map(row => props.rowIdentity(row)!)), props.capability, props.rowIdentity, props.rowScopeKey)
}
defineExpose({ collect, hasChanges })
</script>
