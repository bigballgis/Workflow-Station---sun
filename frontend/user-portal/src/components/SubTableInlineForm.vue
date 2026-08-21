<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import PortalFormFields, { type PortalSubTableBindingLite } from './PortalFormFields.vue'
import type { FormField } from './formRendererHelpers'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'

/**
 * Sub-table form rendered inline: the bound sub-table's designed form, laid out
 * in place (the `inlineSubForm` widget). Nested subTable widgets use
 * {@link PortalFormFields} so structure matches Developer Workstation preview.
 */

interface Props {
  title?: string
  fields: FormField[]
  currentRow?: Record<string, unknown> | null
  readonly?: boolean
  labelWidth?: string
  /**
   * Left-aligns by default to match the host FormRenderer's own default (`FormRenderer.vue`'s
   * `labelPosition` prop defaults to `'left'`). Element Plus's own default is `'right'` — left
   * unset here, this form's labels visually diverged from the rest of the page (each label's
   * right edge flush against its input, but no consistent left edge — read as "not aligned").
   */
  labelPosition?: 'left' | 'right' | 'top'
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
  /** Inline Form widget: rows persist with the host form, so it owns no Save button. */
  hideSaveButton?: boolean
  /** Inline Form widget: renders flush in the host layout, without the el-card chrome. */
  bordered?: boolean
  /**
   * Inline Form widget: draw a labelled frame around the block. Its fields come from a
   * DIFFERENT table than the host form, and without a boundary they read as ordinary host
   * fields — the author cannot tell which rows belong to the embedded sub-table.
   */
  framed?: boolean
  /**
   * Cycle-guard ancestry threaded through from the caller — passed straight to the inner
   * PortalFormFields so a nested inlineSubForm field (if `fields` itself contains one) resolves
   * its own fields with the full ancestor chain, not just its immediate parent.
   */
  visitedInlineSubFormBindingIds?: ReadonlySet<number>
  /**
   * Task-node field permissions, passed straight to the inner PortalFormFields so nested
   * inlineSubForm fields (if `fields` itself contains one) get the same READONLY enforcement
   * as this form's own top-level fields already do via SubTableField's Add/Edit dialog.
   */
  fieldPermissions?: Record<string, string> | null
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  readonly: false,
  labelWidth: '160px',
  labelPosition: 'left',
  suppressLinkOnlyStandaloneSubTables: false,
  hideSaveButton: false,
  bordered: true,
  framed: false,
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
  <component
    :is="bordered ? 'el-card' : 'div'"
    v-bind="bordered ? { shadow: 'never' } : {}"
    class="sub-table-inline-form"
    :class="{ 'is-borderless': !bordered, 'is-framed': framed }"
  >
    <template
      v-if="bordered"
      #header
    >
      <span class="title">{{ cardTitle }}</span>
    </template>
    <!--
      Framed mode marks where the embedded sub-table's fields start and end: they belong to a
      different table than the host form, so without this boundary they read as host fields.
    -->
    <div
      v-if="framed"
      class="inline-form-frame-header"
    >
      <el-icon class="inline-form-frame-icon"><Document /></el-icon>
      <span class="inline-form-frame-title">{{ cardTitle }}</span>
    </div>
    <el-form
      :model="rowModel"
      :label-width="labelWidth"
      :label-position="labelPosition"
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
          :visited-inline-sub-form-binding-ids="visitedInlineSubFormBindingIds"
          :field-permissions="fieldPermissions"
          @update:field="handleFieldUpdate"
        />
      </el-row>
      <el-empty
        v-if="fields.length === 0"
        :description="t('subTable.formBelowTableEmpty')"
      />
      <div
        v-if="!readonly && fields.length > 0 && !hideSaveButton"
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
  </component>
</template>

<style scoped>
.sub-table-inline-form {
  margin-bottom: 16px;
}

/* Inline Form widget: sits flush in the host layout, no card frame or padding of its own. */
.sub-table-inline-form.is-borderless {
  margin-bottom: 0;
}

/*
 * Framed variant: a labelled boundary around fields that belong to the embedded sub-table
 * rather than the host form. Uses a tinted surface + accent left edge so the group is obvious
 * at a glance without competing with el-card sections already on the page.
 */
.sub-table-inline-form.is-framed {
  margin: 8px 0 16px;
  padding: 0 0 4px;
  /* Neutral grey only: the brand accent here is red, which reads as an error state on a
     block that is merely a grouping boundary. */
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  background: var(--el-fill-color-blank, #fff);
}

.sub-table-inline-form.is-framed .inline-form-frame-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 3px 3px 0 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular, #606266);
}

.sub-table-inline-form.is-framed .inline-form-frame-icon {
  color: var(--el-text-color-secondary, #909399);
}

/* Keep field rows clear of the frame edge. */
.sub-table-inline-form.is-framed :deep(.el-form) {
  padding: 0 12px;
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
