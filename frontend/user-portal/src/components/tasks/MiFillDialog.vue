<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="600px"
    destroy-on-close
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
        :readonly="computedReadOnly"
        :sub-table-bindings="subTableBindings"
        :preview-sub-tables="true"
        :suppress-link-form-initial-data="isMiSubTaskMode && !isCompletedTask"
        :show-link-form-dialog-footer="!isCompletedTask && !computedReadOnly"
        @update:model-value="emit('update:formData', $event)"
        @update:sub-table-data="(bindingId: number, rows: any[]) => emit('update:subTableData', bindingId, rows)"
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
        v-if="!computedReadOnly"
        type="primary"
        @click="emit('confirm')"
      >
        {{ $t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import FormRenderer, { type FormField, type FormTab } from '@/components/FormRenderer.vue'

const props = defineProps<{
  modelValue: boolean
  title: string
  fields: FormField[]
  tabs: FormTab[]
  formData: Record<string, any>
  labelWidth: string
  formReadOnly: boolean
  dialogReadOnly: boolean
  subTableBindings: any[]
  isMiSubTaskMode: boolean
  isCompletedTask: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'update:formData', value: Record<string, any>): void
  (e: 'update:subTableData', bindingId: number, rows: any[]): void
  (e: 'confirm'): void
}>()

const visible = ref(props.modelValue)
const computedReadOnly = computed(() => props.formReadOnly || props.dialogReadOnly)

watch(() => props.modelValue, (val) => { visible.value = val })
watch(visible, (val) => { emit('update:modelValue', val) })
</script>
