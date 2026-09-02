<template>
  <el-form
    label-position="top"
    size="small"
  >
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 8px;"
    >
      <template #title>
        {{ t('properties.subTaskConfigHint') }}
      </template>
    </el-alert>

    <el-form-item
      :label="t('properties.subTableIdField')"
      required
    >
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

    <!-- 分派方式：两个独立开关，可同时勾选（都勾=场景 C，运行时逐行二选一） -->
    <el-form-item :label="t('properties.assignmentModeLabel')">
      <el-checkbox
        v-model="allowUser"
        @change="handleAllowUserChange"
      >
        {{ t('properties.assignmentModeUser') }}
      </el-checkbox>
      <el-checkbox
        v-model="allowRole"
        @change="handleAllowRoleChange"
      >
        {{ t('properties.assignmentModeRole') }}
      </el-checkbox>
      <div class="form-tip">
        {{ t('properties.assignmentModeTip') }}
      </div>
    </el-form-item>

    <!-- 已启用的分派方式各占一个 tab 展示对应字段（person→Assignee；role→Role+BU） -->
    <el-tabs
      v-if="allowUser || allowRole"
      v-model="fieldTab"
      type="border-card"
      class="assign-field-tabs"
    >
      <!-- 允许个人：读某列的用户 id 直接指派该人 -->
      <el-tab-pane
        v-if="allowUser"
        :label="t('properties.assignmentModeUser')"
        name="user"
      >
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
            {{ t('properties.subTaskAssigneeFieldTip') }}
          </div>
        </el-form-item>
      </el-tab-pane>

      <!-- 允许角色：读某列的 role code + BU code 列 → BU 下该角色成员共享认领 -->
      <el-tab-pane
        v-if="allowRole"
        :label="t('properties.assignmentModeRole')"
        name="role"
      >
        <!-- 先选 BU（级联上游），再选 Role -->
        <el-form-item :label="t('properties.buFieldLabel')">
          <el-select
            v-model="buField"
            :placeholder="t('properties.selectBuField')"
            :loading="loadingSubTables"
            :disabled="!elementSubTableId"
            clearable
            filterable
            style="width: 100%"
            @change="handleBuFieldChange"
          >
            <el-option
              v-for="field in assigneeFieldOptions"
              :key="field.fieldName"
              :label="`${field.displayName || field.fieldName} (${field.fieldName})`"
              :value="field.fieldName"
            />
          </el-select>
          <div class="form-tip">
            {{ t('properties.buFieldTip') }}
          </div>
        </el-form-item>

        <el-form-item
          :label="t('properties.roleFieldLabel')"
          required
        >
          <el-select
            v-model="roleField"
            :placeholder="assigneeFieldPlaceholder"
            :loading="loadingSubTables"
            :disabled="!elementSubTableId"
            clearable
            filterable
            style="width: 100%"
            @change="handleRoleFieldChange"
          >
            <el-option
              v-for="field in assigneeFieldOptions"
              :key="field.fieldName"
              :label="`${field.displayName || field.fieldName} (${field.fieldName})`"
              :value="field.fieldName"
            />
          </el-select>
          <div class="form-tip">
            {{ t('properties.roleFieldTip') }}
          </div>
        </el-form-item>
      </el-tab-pane>
    </el-tabs>

    <el-form-item
      :label="t('properties.subTaskForm')"
      required
    >
      <el-select
        v-model="formId"
        :placeholder="t('properties.selectSubTaskForm')"
        clearable
        filterable
        style="width: 100%"
        @change="handleFormChange"
      >
        <el-option
          v-for="form in forms"
          :key="form.id"
          :label="form.formName"
          :value="form.id"
        />
      </el-select>
      <div class="form-tip">
        {{ t('properties.subTaskFormTip') }}
      </div>
    </el-form-item>

    <!--
      The same node can also carry a My Requests design (requestFormId/requestFormName
      ext props). Editable here in addition to Form Design > My Requests > row menu >
      Bound Node — both paths write the same BPMN fields, so either one works. Mirrors
      the regular UserTaskProperties panel.
    -->
    <el-form-item :label="t('properties.bindFormRequest')">
      <el-select
        v-model="requestFormId"
        :placeholder="t('properties.selectForm')"
        clearable
        filterable
        style="width: 100%"
        @change="handleRequestFormChange"
      >
        <el-option
          v-for="form in requestableForms"
          :key="form.id"
          :label="form.formName"
          :value="form.id"
        />
      </el-select>
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

    <el-divider content-position="left">
      {{ t('properties.miProgressFieldsDivider') }}
    </el-divider>
    <el-form-item :label="t('properties.miTaskStatusField')">
      <el-select
        v-model="miTaskStatusField"
        filterable
        clearable
        :disabled="!elementSubTableId"
        :loading="loadingSubTables"
        :placeholder="miProgressFieldPlaceholder"
        style="width: 100%"
        @change="handleMiTaskStatusFieldChange"
      >
        <el-option
          v-for="f in miProgressFieldOptions"
          :key="f"
          :label="f"
          :value="f"
        />
      </el-select>
      <div class="form-tip">
        {{ t('properties.miTaskStatusFieldTip') }}
      </div>
      <div
        v-if="miStatusFieldInvalid"
        class="form-error"
      >
        {{ t('properties.miProgressFieldInvalid') }}
      </div>
    </el-form-item>
    <el-form-item :label="t('properties.miTaskCurrentNodeField')">
      <el-select
        v-model="miTaskCurrentNodeField"
        filterable
        clearable
        :disabled="!elementSubTableId"
        :loading="loadingSubTables"
        :placeholder="miProgressFieldPlaceholder"
        style="width: 100%"
        @change="handleMiTaskCurrentNodeFieldChange"
      >
        <el-option
          v-for="f in miProgressFieldOptions"
          :key="f"
          :label="f"
          :value="f"
        />
      </el-select>
      <div class="form-tip">
        {{ t('properties.miTaskCurrentNodeFieldTip') }}
      </div>
      <div
        v-if="miCurrentNodeFieldInvalid"
        class="form-error"
      >
        {{ t('properties.miProgressFieldInvalid') }}
      </div>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { injectUserTaskPanel } from './userTaskPropertiesInject'

const { ctx, multiInstance, actions } = injectUserTaskPanel()
const { t, updateExtProp } = ctx
const {
  elementSubTableId,
  elementSubTableName,
  assigneeField,
  allowUser,
  allowRole,
  roleField,
  buField,
  rowIdVariable,
  subTables,
  loadingSubTables,
  miTaskStatusField,
  miTaskCurrentNodeField,
  miStatusFieldInvalid,
  miCurrentNodeFieldInvalid,
  formId,
  forms,
  requestFormId,
} = ctx
const {
  handleFormChange,
  handleSubTableChange,
  handleAssigneeFieldChange,
  handleAllowUserChange,
  handleAllowRoleChange,
  handleRoleFieldChange,
  handleBuFieldChange,
  assigneeFieldOptions,
  miProgressFieldOptions,
  assigneeFieldPlaceholder,
  miProgressFieldPlaceholder,
  handleMiTaskStatusFieldChange,
  handleMiTaskCurrentNodeFieldChange,
} = multiInstance
const { requestableForms, handleRequestFormChange } = actions

// 字段区当前 tab（纯 UI，不持久化）。保证 activeTab 始终指向一个已启用的 tab。
const fieldTab = ref<'user' | 'role'>('user')
watch(
  [allowUser, allowRole],
  ([u, r]) => {
    if (fieldTab.value === 'user' && !u && r) fieldTab.value = 'role'
    else if (fieldTab.value === 'role' && !r && u) fieldTab.value = 'user'
    else if (!u && r) fieldTab.value = 'role'
    else if (u && !r) fieldTab.value = 'user'
  },
  { immediate: true }
)
</script>

<style lang="scss" scoped>
.form-tip {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}

.form-error {
  font-size: 11px;
  color: #f56c6c;
  margin-top: 4px;
}

/* 分派字段 tab：窄属性面板里收紧内边距 */
.assign-field-tabs {
  margin-bottom: 12px;
}
.assign-field-tabs :deep(.el-tabs__content) {
  padding: 12px 10px 2px;
}
.assign-field-tabs :deep(.el-tabs__item) {
  font-size: 12px;
  height: 34px;
  line-height: 34px;
}
</style>
