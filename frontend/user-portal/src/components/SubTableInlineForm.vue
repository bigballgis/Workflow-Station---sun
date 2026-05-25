<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import PortalFormFields, { type PortalSubTableBindingLite } from './PortalFormFields.vue'
import type { FormField } from './formRendererHelpers'

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
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  readonly: false,
  labelWidth: '160px',
})

const emit = defineEmits<{
  (e: 'update:row', row: Record<string, unknown>): void
  (e: 'change', key: string, value: unknown): void
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
          @update:field="handleFieldUpdate"
        />
      </el-row>
      <el-empty
        v-if="fields.length === 0"
        :description="t('subTable.formBelowTableEmpty')"
      />
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
</style>
