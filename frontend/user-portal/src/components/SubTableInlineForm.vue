<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import PortalFormFields, { type PortalSubTableBindingLite } from './PortalFormFields.vue'
import type { FormField } from './formRendererHelpers'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'

/**
 * Inline form rendered **below** a SubTableField when the designer chose
 * portalViews.assigneeTodo = 'formBelowTable'. Nested subTable widgets use
 * {@link PortalFormFields} so structure matches Developer Workstation preview.
 */

interface Props {
  title?: string
  fields: FormField[]
  currentRow?: Record<string, unknown> | null
  readonly?: boolean
  labelWidth?: string
  subTableBindings?: PortalSubTableBindingLite[]
  linkedSubTableBindings?: PortalSubTableBindingLite[]
  suppressLinkOnlyStandaloneSubTables?: boolean
  /** FK/PK runtime context of the sub-table row this form edits — needed by nested sub-tables. */
  hostTableId?: number | null
  hostFieldDefinitions?: BindingFieldDefinition[]
  hostFunctionUnitId?: string
  hostTaskId?: string
  hostPrimaryFormData?: Record<string, unknown>
  hostPrimaryTableId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  readonly: false,
  labelWidth: '160px',
  suppressLinkOnlyStandaloneSubTables: false,
})

const emit = defineEmits<{
  (e: 'update:row', row: Record<string, unknown>): void
  (e: 'change', key: string, value: unknown): void
  (e: 'save'): void
}>()

const { t } = useI18n()

const rowModel = ref<Record<string, unknown>>({})

watch(
  () => props.currentRow,
  r => {
    rowModel.value = r != null && typeof r === 'object' ? { ...r } : {}
  },
  { immediate: true, deep: true },
)

function handleFieldUpdate(key: string, value: unknown) {
  const merged = { ...rowModel.value, [key]: value }
  rowModel.value = merged
  emit('update:row', merged)
  emit('change', key, value)
}

/** Flush row model into bindings before persist so Save allocates PK on the latest inline edits. */
function handleSaveClick() {
  const merged = { ...rowModel.value }
  rowModel.value = merged
  emit('update:row', merged)
  emit('save')
}

const cardTitle = computed(() =>
  props.title?.trim() ? props.title : t('subTable.formBelowTableTitle'),
)
</script>

<template>
  <el-card
    shadow="never"
    class="sub-table-inline-form"
  >
    <template #header>
      <span class="title">{{ cardTitle }}</span>
    </template>
    <el-form
      :model="rowModel"
      :label-width="labelWidth"
      :disabled="readonly"
    >
      <el-row :gutter="20">
        <PortalFormFields
          :fields="fields"
          :model="rowModel"
          :readonly="readonly"
          :editable="!readonly"
          :sub-table-bindings="subTableBindings"
          :linked-sub-table-bindings="linkedSubTableBindings"
          :parent-row="currentRow"
          :suppress-link-only-standalone-sub-tables="suppressLinkOnlyStandaloneSubTables"
          :host-table-id="hostTableId ?? null"
          :host-field-definitions="hostFieldDefinitions"
          :host-function-unit-id="hostFunctionUnitId"
          :host-task-id="hostTaskId"
          :host-primary-form-data="hostPrimaryFormData"
          :host-primary-table-id="hostPrimaryTableId ?? null"
          @update:field="handleFieldUpdate"
        />
      </el-row>
      <el-empty
        v-if="fields.length === 0"
        :description="t('subTable.formBelowTableEmpty')"
      />
      <div
        v-if="!readonly && fields.length > 0"
        class="inline-form-actions"
      >
        <el-button
          type="primary"
          @click="handleSaveClick"
        >
          {{ t('common.save') }}
        </el-button>
      </div>
    </el-form>
  </el-card>
</template>

<style scoped>
.sub-table-inline-form {
  margin-bottom: 16px;
}

.sub-table-inline-form .title {
  font-weight: 600;
  font-size: 14px;
}

.inline-form-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
