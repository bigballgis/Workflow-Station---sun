<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="500px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      label-width="auto"
      label-position="left"
    >
      <el-form-item
        :label="$t('form.formNameLabel')"
        required
      >
        <el-input
          :model-value="formName"
          :placeholder="$t('form.enterFormName')"
          @update:model-value="$emit('update:formName', $event)"
          @keyup.enter="$emit('confirm')"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        {{ $t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="loading"
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
  formName: string
  loading: boolean
  title?: string
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
  'update:formName': [value: string]
  confirm: []
}>()
</script>
