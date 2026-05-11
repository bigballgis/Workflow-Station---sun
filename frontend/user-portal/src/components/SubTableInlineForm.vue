<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldRenderer from './FieldRenderer.vue'
import type { FormField } from './formRendererHelpers'

/**
 * Inline form rendered **below** a SubTableField when the designer chose
 * portalViews.assigneeTodo = 'formBelowTable'. It binds to a single "current row"
 * of the parent sub-table (matched by the parent via `currentRow` prop) and
 * surfaces a focused editing/reading surface for that row's fields.
 *
 * Data flow:
 *   parent → currentRow (read-only snapshot)
 *   user edits → emits 'update:row' with merged row object (parent merges back into binding data)
 *
 * Scope (this PR):
 *   - Supports the basic primitive field types provided by FieldRenderer.
 *   - Sub-table-inside-sub-form is intentionally NOT recursed — keeps the row editor flat.
 *   - Validation is delegated to the parent FormRenderer / submit flow.
 */

interface Props {
  /** Title displayed on the inline form card (optional). */
  title?: string
  /** Field definitions to render — typically the binding's subForm fields. */
  fields: FormField[]
  /** Current row data (the participant row for this MI task, or first row, or empty). */
  currentRow?: Record<string, any> | null
  /** Read-only mode (used for My Request / completed tasks). */
  readonly?: boolean
  /** Element Plus form label width. */
  labelWidth?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  currentRow: () => ({}),
  readonly: false,
  labelWidth: '160px'
})

const emit = defineEmits<{
  (e: 'update:row', row: Record<string, any>): void
  (e: 'change', key: string, value: any): void
}>()

const { t } = useI18n()

const rowModel = computed<Record<string, any>>(() => ({ ...(props.currentRow ?? {}) }))

function handleFieldUpdate(key: string, value: any) {
  const merged = { ...rowModel.value, [key]: value }
  emit('update:row', merged)
  emit('change', key, value)
}

const cardTitle = computed(() =>
  props.title?.trim() ? props.title : t('subTable.formBelowTableTitle')
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
        <el-col
          v-for="field in fields"
          :key="field.key"
          :span="field.span || 24"
        >
          <el-form-item
            :label="field.label"
            :prop="field.key"
            :required="field.required"
          >
            <FieldRenderer
              :field="field"
              :model-value="rowModel[field.key]"
              :form-data="rowModel"
              :readonly="readonly"
              @update:model-value="(val: any) => handleFieldUpdate(field.key, val)"
            />
          </el-form-item>
        </el-col>
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
