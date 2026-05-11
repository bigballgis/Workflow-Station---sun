<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('form.createFormTitle')"
    width="500px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      :model="createForm"
      label-width="100px"
      label-position="left"
    >
      <el-form-item
        :label="$t('form.formNameLabel')"
        required
      >
        <el-input
          v-model="createForm.formName"
          :placeholder="$t('form.enterFormName')"
        />
      </el-form-item>
      <el-form-item :label="$t('form.formTypeLabel')">
        <div
          v-if="forms.some((f: any) => f.formType === 'PROCESS')"
          class="form-item-tip"
          style="margin-bottom: 8px;"
        >
          {{ $t('form.processFormLimitHint') }}
        </div>
        <el-select
          v-model="createForm.formType"
          style="width: 100%"
          @change="handleCreateFormTypeChange"
        >
          <el-option
            :label="$t('form.processForm')"
            value="PROCESS"
            :disabled="forms.some((f: any) => f.formType === 'PROCESS')"
          />
          <el-option
            :label="$t('form.taskForm')"
            value="TASK"
          />
          <el-option
            :label="$t('form.actionForm')"
            value="ACTION"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="createForm.formType === 'TASK'"
        :label="$t('form.stageBinding')"
        required
      >
        <el-select
          :model-value="stageIds"
          multiple
          :placeholder="$t('form.stageBindingPlaceholder')"
          style="width: 100%"
          @update:model-value="$emit('update:stageIds', $event)"
        >
          <el-option
            v-for="node in createDialogProcessNodes"
            :key="node.id"
            :label="node.name"
            :value="node.id"
          />
        </el-select>
        <div class="form-item-tip">
          {{ $t('form.stageBindingHint') }}
        </div>
      </el-form-item>
      <el-form-item :label="$t('form.bindTableLabel')">
        <el-select
          v-model="createForm.boundTableId"
          :placeholder="$t('form.selectTableToBind')"
          style="width: 100%"
          clearable
        >
          <el-option 
            v-for="table in tables" 
            :key="table.id" 
            :label="`${table.tableName} (${tableTypeLabel(table.tableType)})`" 
            :value="table.id" 
          />
        </el-select>
        <div class="form-item-tip">
          {{ $t('form.bindTableHint') }}
        </div>
      </el-form-item>
      <el-form-item :label="$t('form.descriptionLabel')">
        <el-input
          v-model="createForm.description"
          type="textarea"
          :rows="3"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        {{ $t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        @click="$emit('confirm')"
      >
        {{ $t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: boolean
  createForm: any
  forms: any[]
  tables: any[]
  createDialogProcessNodes: any[]
  stageIds: any[]
  tableTypeLabel: (type: string) => string
  handleCreateFormTypeChange: () => void
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
  'update:stageIds': [value: any[]]
  confirm: []
}>()
</script>
