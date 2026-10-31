<template>
  <div class="assignment-exclusion" data-testid="assignment-exclusion-panel">
    <div class="assignment-exclusion__guide">
      <DesignerHelpLink path="/assignment-exclusion" v-bind="{ ariaLabel: t('assignmentExclusion.guide') }" />
    </div>
    <el-alert v-if="invalid" type="error" :closable="false" :title="t('assignmentExclusion.invalid')" show-icon />
    <el-button v-if="invalid" :disabled="disabled" size="small" @click="reset">
      {{ t('assignmentExclusion.reset') }}
    </el-button>
    <el-form v-else label-position="top" size="small" :disabled="disabled">
      <el-checkbox v-model="draft.excludeInitiator" @change="commit">
        {{ t('assignmentExclusion.initiator') }}
      </el-checkbox>
      <el-checkbox v-model="draft.excludePreviousHandlers" @change="commit">
        {{ t('assignmentExclusion.previous') }}
      </el-checkbox>
      <el-checkbox v-model="draft.excludeSelectedNodeHandlers" @change="commit">
        {{ t('assignmentExclusion.selected') }}
      </el-checkbox>
      <el-form-item v-if="draft.excludeSelectedNodeHandlers" :label="t('assignmentExclusion.nodes')">
        <el-select v-model="draft.selectedNodeKeys" multiple filterable @change="commit">
          <el-option v-for="node in nodes" :key="node.id" :value="node.id" :label="`${node.name} · ${node.id}`" />
        </el-select>
      </el-form-item>
      <p class="assignment-exclusion__hint">{{ t('assignmentExclusion.rulesHint') }}</p>
      <el-form-item :label="t('assignmentExclusion.backup')">
        <el-switch v-model="draft.backup.enabled" :disabled="disabled || !enabled" @change="commit" />
      </el-form-item>
      <template v-if="draft.backup.enabled">
        <p class="assignment-exclusion__hint">{{ t('assignmentExclusion.backupHint') }}</p>
        <el-form-item :label="t('assignmentExclusion.source')">
          <el-select v-model="draft.backup.mode" @change="commit">
            <el-option value="BU_ROLE" :label="t('assignmentExclusion.buRole')" />
            <el-option value="USERS" :label="t('assignmentExclusion.users')" />
          </el-select>
        </el-form-item>
        <template v-if="draft.backup.mode === 'BU_ROLE'">
          <el-form-item :label="t('assignmentExclusion.buSource')">
            <el-select v-model="draft.backup.buSource" @change="changeBu">
              <el-option value="CASE_START_BU" :label="t('assignmentExclusion.startBu')" />
              <el-option value="NODE_ASSIGNMENT_BU" :label="t('assignmentExclusion.nodeBu')" :disabled="!canUseNodeBu" />
              <el-option value="FIXED_BU" :label="t('assignmentExclusion.fixedBu')" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="draft.backup.buSource === 'FIXED_BU'" :label="t('assignmentExclusion.businessUnit')">
            <el-select v-model="draft.backup.businessUnitCode" filterable :loading="loading" @change="changeBu">
              <el-option v-for="unit in units" :key="unit.id" :value="unit.code" :label="`${unit.name} (${unit.code})`" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('assignmentExclusion.roles')">
            <el-select v-model="draft.backup.roleCodes" multiple filterable :loading="loading || loadingRoles" @change="commit">
              <el-option v-for="role in roleOptions" :key="role.code" :value="role.code" :label="`${role.name} (${role.code})`" />
            </el-select>
          </el-form-item>
          <el-alert v-if="catalogError || roleError" type="error" :closable="false" :title="t('assignmentExclusion.catalogError')" />
        </template>
        <el-form-item v-else :label="t('assignmentExclusion.users')">
          <el-select v-model="draft.backup.userRefs" multiple filterable remote :remote-method="searchUsers" :loading="loadingUsers"
            @focus="searchUsers('')" @change="commit">
            <el-option v-for="user in users" :key="user.id" :value="user.username" :label="`${user.displayName} (${user.username})`" />
          </el-select>
          <el-alert v-if="userError" type="error" :closable="false" :title="t('assignmentExclusion.catalogError')" />
        </el-form-item>
      </template>
      <el-alert v-if="validationMessage" type="warning" :closable="false" :title="validationMessage" show-icon />
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { useAssignmentExclusion } from '@/composables/userTaskProperties/useAssignmentExclusion'
import { useAssignmentExclusionCatalogs } from '@/composables/userTaskProperties/useAssignmentExclusionCatalogs'
import { assignmentExclusionProblem } from '@/utils/assignmentExclusion'
import DesignerHelpLink from '../DesignerHelpLink.vue'

const props = withDefaults(defineProps<{ modeler: BpmnModeler; element: BpmnElement; disabled?: boolean }>(), { disabled: false })
const { t } = useI18n()
const { draft, invalid, nodes, enabled, canUseNodeBu, commit, reset } = useAssignmentExclusion(props)
const { units, roleOptions, users, loading, loadingUsers, catalogError, userError, roleError, loadingRoles, load, searchUsers }
  = useAssignmentExclusionCatalogs(draft)
const validationMessage = computed(() => {
  const problem = assignmentExclusionProblem(draft.value, new Set(nodes.value.map(node => node.id)), canUseNodeBu.value)
  return problem ? t(`assignmentExclusion.${problem}`) : ''
})
function changeBu(): void { draft.value.backup.roleCodes = []; commit() }
onMounted(() => { void load() })
</script>

<style scoped lang="scss">
.assignment-exclusion {
  .el-checkbox { display: flex; height: auto; min-height: 30px; margin-right: 0; }
  :deep(.el-checkbox__label) { white-space: normal; line-height: 1.5; }
  .el-select { width: 100%; }
  &__guide { display: flex; justify-content: flex-end; margin-bottom: 6px; }
  &__hint { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; margin: 8px 0 12px; }
}
</style>
