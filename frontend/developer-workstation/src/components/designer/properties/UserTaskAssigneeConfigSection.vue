<template>
  <el-form
    label-position="top"
    size="small"
  >
    <el-form-item :label="t('properties.assigneeType')">
      <el-select
        v-model="assigneeType"
        @change="handleAssigneeTypeChange"
      >
        <el-option-group :label="t('properties.directAssignment')">
          <el-option
            :label="t('properties.initiator')"
            value="INITIATOR"
          />
          <el-option
            :label="t('properties.entityManager')"
            value="ENTITY_MANAGER"
          />
          <el-option
            :label="t('properties.functionManager')"
            value="FUNCTION_MANAGER"
          />
        </el-option-group>
        <el-option-group :label="t('properties.convergedAssignee')">
          <el-option
            :label="t('properties.hierarchyRole')"
            value="HIERARCHY_ROLE"
          />
          <el-option
            :label="t('properties.buRoleConverged')"
            value="BU_ROLE"
          />
          <el-option
            :label="t('properties.manualAssignType')"
            value="MANUAL_ASSIGN"
          />
          <el-option
            :label="t('properties.assigneeFromVariableType')"
            value="ASSIGNEE_FROM_VARIABLE"
          />
          <el-option
            :label="t('properties.elementVariableType')"
            value="ELEMENT_VARIABLE"
          />
        </el-option-group>
        <el-option-group :label="t('properties.legacyBpmnAssignee')">
          <el-option
            :label="t('properties.currentBuRole')"
            value="CURRENT_BU_ROLE"
          />
          <el-option
            :label="t('properties.currentParentBuRole')"
            value="CURRENT_PARENT_BU_ROLE"
          />
          <el-option
            :label="t('properties.initiatorBuRoleOption')"
            value="INITIATOR_BU_ROLE"
          />
          <el-option
            :label="t('properties.initiatorParentBuRole')"
            value="INITIATOR_PARENT_BU_ROLE"
          />
          <el-option
            :label="t('properties.fixedBuRole')"
            value="FIXED_BU_ROLE"
          />
        </el-option-group>
      </el-select>
    </el-form-item>

    <div
      v-if="assigneeType === 'BU_UNBOUNDED_ROLE'"
      class="claim-tip"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
      >
        <template #title>
          {{ t('properties.buUnboundedDeprecated') }}
        </template>
      </el-alert>
    </div>

    <div
      v-if="assigneeLabel"
      class="assignee-label"
    >
      <el-tag
        type="info"
        size="small"
      >
        {{ assigneeLabel }}
      </el-tag>
    </div>

    <el-form-item
      v-if="needsBuForRole"
      :label="t('properties.selectBusinessUnit')"
    >
      <el-tree-select
        v-model="businessUnitId"
        :data="businessUnits"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        :loading="loadingBusinessUnits"
        :placeholder="t('properties.selectBusinessUnit')"
        check-strictly
        filterable
        @change="handleBusinessUnitChange"
      />
      <div class="form-tip">
        {{ t('properties.selectBusinessUnitTip') }}
      </div>
    </el-form-item>

    <el-form-item
      v-if="showRoleSelector && needsMultiRoleSelect"
      :label="t('properties.selectRoles')"
    >
      <el-select
        v-model="roleIds"
        multiple
        collapse-tags
        collapse-tags-tooltip
        :loading="loadingRoles"
        :placeholder="roleSelectPlaceholder"
        :disabled="!businessUnitId"
        filterable
        style="width: 100%;"
        @change="handleRoleIdsChange"
      >
        <el-option
          v-for="role in roleSelectOptions"
          :key="role.id"
          :label="role.name"
          :value="role.id"
        >
          <span>{{ role.name }}</span>
          <span
            v-if="role.code"
            style="color: #909399; margin-left: 8px;"
          >({{ role.code }})</span>
        </el-option>
      </el-select>
      <div class="form-tip">
        {{ roleSelectTip }}
      </div>
    </el-form-item>

    <el-form-item
      v-else-if="showRoleSelector"
      :label="t('properties.selectRole')"
    >
      <el-select
        v-model="roleId"
        :loading="loadingRoles"
        :placeholder="roleSelectPlaceholder"
        filterable
        @change="handleRoleChange"
      >
        <el-option
          v-for="role in roleSelectOptions"
          :key="role.id"
          :label="role.name"
          :value="role.id"
        >
          <span>{{ role.name }}</span>
          <span style="color: #909399; margin-left: 8px;">({{ role.code }})</span>
        </el-option>
      </el-select>
      <div class="form-tip">
        {{ roleSelectTip }}
      </div>
    </el-form-item>

    <template v-if="assigneeType === 'MANUAL_ASSIGN'">
      <el-form-item :label="t('properties.manualAssignVariable')">
        <el-input
          v-model="manualAssignVariable"
          :placeholder="t('properties.manualAssignVariableHint')"
          @change="updateExtProp('manualAssignVariable', manualAssignVariable)"
        />
      </el-form-item>
      <el-form-item :label="t('properties.manualAssignBuVariable')">
        <el-input
          v-model="manualAssignBuVariable"
          @change="updateExtProp('manualAssignBuVariable', manualAssignBuVariable)"
        />
      </el-form-item>
      <el-form-item :label="t('properties.manualAssignRoleVariable')">
        <el-input
          v-model="manualAssignRoleVariable"
          @change="updateExtProp('manualAssignRoleVariable', manualAssignRoleVariable)"
        />
      </el-form-item>
    </template>

    <el-form-item
      v-if="assigneeType === 'ASSIGNEE_FROM_VARIABLE'"
      :label="t('properties.assigneeVariableField')"
    >
      <el-input
        v-model="assigneeVariableName"
        :placeholder="t('properties.assigneeVariableHint')"
        @change="updateExtProp('assigneeVariable', assigneeVariableName)"
      />
    </el-form-item>

    <template v-if="assigneeType === 'ELEMENT_VARIABLE'">
      <el-form-item :label="t('properties.subTableIdField')">
        <el-select
          v-model="elementSubTableId"
          :placeholder="t('properties.selectSubTable')"
          :loading="loadingSubTables"
          clearable
          filterable
          style="width: 100%"
          @change="handleSubTableChange"
        >
          <el-option
            v-for="table in subTables"
            :key="table.id"
            :label="`${table.tableDisplayName || table.tableName} (${table.tableName})`"
            :value="table.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('properties.subTableNameField')">
        <el-input
          v-model="elementSubTableName"
          disabled
        />
        <div class="form-tip">
          {{ t('properties.subTableNameAutoFilledTip') }}
        </div>
      </el-form-item>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 8px;"
      >
        <template #title>
          {{ t('properties.elementVariableRuntimeHint') }}
        </template>
      </el-alert>
      <el-form-item
        :label="t('properties.assigneeFieldLabel')"
        required
      >
        <el-select
          v-model="assigneeField"
          :placeholder="assigneeFieldPlaceholder"
          :loading="loadingSubTables"
          :disabled="!elementSubTableId"
          clearable
          filterable
          style="width: 100%"
          @change="handleAssigneeFieldChange"
        >
          <el-option
            v-for="field in assigneeFieldOptions"
            :key="field.fieldName"
            :label="`${field.displayName || field.fieldName} (${field.fieldName})`"
            :value="field.fieldName"
          />
        </el-select>
        <div class="form-tip">
          {{ t('properties.assigneeFieldTip') }}
        </div>
      </el-form-item>
      <el-form-item :label="t('properties.rowIdVariableLabel')">
        <el-input
          v-model="rowIdVariable"
          placeholder="currentItem.rowId"
          @change="updateExtProp('rowIdVariable', rowIdVariable)"
        />
        <div class="form-tip">
          {{ t('properties.rowIdVariableTip') }}
        </div>
      </el-form-item>
    </template>

    <div
      v-if="needsClaim"
      class="claim-tip"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
      >
        <template #title>
          {{ t('properties.claimRequired') }}
        </template>
      </el-alert>
    </div>

    <el-form-item :label="t('properties.candidateUsers')">
      <el-input
        v-model="candidateUsers"
        :placeholder="t('properties.candidateUsersPlaceholder')"
        @change="updateExtProp('candidateUsers', candidateUsers)"
      />
    </el-form-item>

    <el-form-item :label="t('properties.candidateGroups')">
      <el-input
        v-model="candidateGroups"
        :placeholder="t('properties.candidateGroupsPlaceholder')"
        @change="updateExtProp('candidateGroups', candidateGroups)"
      />
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { injectUserTaskPanel } from './userTaskPropertiesInject'

const { ctx, assignee, multiInstance } = injectUserTaskPanel()
const { t, updateExtProp } = ctx
const {
  assigneeType,
  roleId,
  roleIds,
  businessUnitId,
  assigneeLabel,
  candidateUsers,
  candidateGroups,
  manualAssignVariable,
  manualAssignBuVariable,
  manualAssignRoleVariable,
  assigneeVariableName,
  elementSubTableId,
  elementSubTableName,
  assigneeField,
  rowIdVariable,
  subTables,
  loadingSubTables,
  businessUnits,
  loadingBusinessUnits,
  loadingRoles,
} = ctx
const {
  needsBuForRole,
  needsMultiRoleSelect,
  showRoleSelector,
  roleSelectPlaceholder,
  needsClaim,
  roleSelectOptions,
  roleSelectTip,
  handleAssigneeTypeChange,
  handleRoleChange,
  handleRoleIdsChange,
  handleBusinessUnitChange,
} = assignee
const {
  handleSubTableChange,
  handleAssigneeFieldChange,
  assigneeFieldOptions,
  assigneeFieldPlaceholder,
} = multiInstance
</script>

<style lang="scss" scoped>
.form-tip {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}

.assignee-label {
  margin-bottom: 12px;
}

.claim-tip {
  margin-bottom: 12px;

  :deep(.el-alert) {
    padding: 8px 12px;

    .el-alert__title {
      font-size: 12px;
    }
  }
}
</style>
