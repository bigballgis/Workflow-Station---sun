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
        :sub-table-bindings="subTableBindings"
        :linked-sub-table-bindings="linkedSubTableBindings ?? subTableBindings"
        :native-sub-table-binding-ids="nativeSubTableBindingIds"
        :form-config="formConfig"
        :view-context="viewContext"
        :show-link-form-dialog-footer="!readonly"
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
import type { PortalViewContext } from '@/components/formRendererHelpers'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    width: string
    fields: FormField[]
    tabs: FormTab[]
    formData: Record<string, any>
    labelWidth: string
    readonly: boolean
    submitting: boolean
    /** Sub-table bindings built by the host (parity with main FormRenderer). */
    subTableBindings?: any[]
    /** Link-form target bindings reachable from the popup form. */
    linkedSubTableBindings?: any[] | null
    /** Native (non link-form-target) binding ids declared on the popup form. */
    nativeSubTableBindingIds?: number[]
    /** Designer configJson — drives Link Form target detection via subListViews. */
    formConfig?: Record<string, unknown>
    /** Portal view context — usually 'assigneeTodo' for FORM_POPUP. */
    viewContext?: PortalViewContext
  }>(),
  {
    subTableBindings: () => [],
    linkedSubTableBindings: null,
    nativeSubTableBindingIds: () => [],
    formConfig: () => ({}),
    viewContext: 'assigneeTodo',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'update:formData', value: Record<string, any>): void
  (e: 'update:subTableData', bindingId: number, rows: any[]): void
  (e: 'submit'): void
}>()

const visible = ref(props.modelValue)

watch(() => props.modelValue, (val) => { visible.value = val })
watch(visible, (val) => { emit('update:modelValue', val) })
</script>
