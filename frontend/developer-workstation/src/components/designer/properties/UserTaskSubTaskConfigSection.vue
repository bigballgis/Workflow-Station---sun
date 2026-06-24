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
        allow-create
        default-first-option
        clearable
        :placeholder="t('properties.miProgressFieldSelectPlaceholder')"
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
        allow-create
        default-first-option
        clearable
        :placeholder="t('properties.miProgressFieldSelectPlaceholder')"
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
import { injectUserTaskPanel } from './userTaskPropertiesInject'

const { ctx, multiInstance } = injectUserTaskPanel()
const { t, updateExtProp } = ctx
const {
  elementSubTableId,
  elementSubTableName,
  assigneeField,
  rowIdVariable,
  subTables,
  loadingSubTables,
  miTaskStatusField,
  miTaskCurrentNodeField,
  miStatusFieldInvalid,
  miCurrentNodeFieldInvalid,
  formId,
  forms,
} = ctx
const {
  handleFormChange,
  handleSubTableChange,
  handleAssigneeFieldChange,
  assigneeFieldOptions,
  miProgressFieldOptions,
  assigneeFieldPlaceholder,
  handleMiTaskStatusFieldChange,
  handleMiTaskCurrentNodeFieldChange,
} = multiInstance
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
</style>
