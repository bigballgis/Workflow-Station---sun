<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    append-to-body
  >
    <div
      v-if="fields.length > 0 || tabs.length > 0"
      class="form-popup-container"
    >
      <FormRenderer
        :fields="fields"
        :tabs="tabs"
        :model-value="formData"
        :label-width="labelWidth"
        :readonly="readonly"
        @update:model-value="emit('update:formData', $event)"
      />
    </div>
    <el-empty
      v-else
      :description="$t('task.noFormData')"
    />
    <template #footer>
      <el-button @click="visible = false">
        {{ $t('common.cancel') }}
      </el-button>
      <el-button
        v-if="!readonly"
        type="primary"
        :loading="submitting"
        @click="emit('submit')"
      >
        {{ $t('common.submit') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import FormRenderer, { type FormField, type FormTab } from '@/components/FormRenderer.vue'

const props = defineProps<{
  modelValue: boolean
  title: string
  width: string
  fields: FormField[]
  tabs: FormTab[]
  formData: Record<string, any>
  labelWidth: string
  readonly: boolean
  submitting: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'update:formData', value: Record<string, any>): void
  (e: 'submit'): void
}>()

const visible = ref(props.modelValue)

watch(() => props.modelValue, (val) => { visible.value = val })
watch(visible, (val) => { emit('update:modelValue', val) })
</script>
