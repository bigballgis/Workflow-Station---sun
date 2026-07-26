<template>
  <div
    class="lookup-preview-wrapper"
    @click="handleWrapperClick"
  >
    <div class="lookup-form-item">
      <label
        v-if="label"
        class="lookup-label-text"
      >
        <el-icon class="lookup-label-icon"><Search /></el-icon>
        {{ label }}
      </label>
      <div
        ref="fieldRef"
        class="lookup-field"
        :class="{ readonly }"
        @click="handleFieldClick"
      >
        <!-- Multi-select (Admin Center / Portal parity) -->
        <div
          v-if="multiple"
          class="lookup-selected-wrapper lookup-multi-wrapper"
        >
          <span
            v-for="(row, i) in selectedRows"
            :key="i"
            class="lookup-selected-tag"
          >
            <span class="lookup-selected-text">{{ getDisplayText(row) }}</span>
            <el-icon
              v-if="!readonly"
              class="lookup-selected-close"
              @click.stop="removeSelectedAt(i)"
            ><Close /></el-icon>
          </span>
          <input
            v-if="!readonly"
            v-model="searchKeyword"
            class="lookup-multi-input"
            :placeholder="selectedRows.length ? '' : (placeholder || 'Click to search')"
            @focus="onInputFocus"
          >
          <span
            v-if="readonly && !selectedRows.length"
            class="lookup-readonly-empty"
          >-</span>
        </div>
        <!-- Single-select: selected tag -->
        <div
          v-else-if="selectedRow"
          class="lookup-selected-wrapper"
        >
          <span class="lookup-selected-tag">
            <span class="lookup-selected-text">{{ searchKeyword }}</span>
            <el-icon
              v-if="!readonly"
              class="lookup-selected-close"
              @click.stop="handleClear"
            ><Close /></el-icon>
          </span>
        </div>
        <el-input
          v-else-if="readonly"
          model-value=""
          placeholder="-"
          class="lookup-input"
          disabled
        />
        <!-- Search input (hidden when a value is selected) -->
        <el-input
          v-else
          v-model="searchKeyword"
          :placeholder="placeholder"
          class="lookup-input"
          @focus="onInputFocus"
        />
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="dropdownVisible"
        ref="dropdownRef"
        class="lookup-dropdown-panel"
        :style="dropdownStyle"
        @mousedown.stop
        @click.stop
      >
        <div class="table-scroll-wrap">
          <el-table
            :data="filteredResults"
            size="small"
            highlight-current-row
            max-height="260"
            @row-click="handleSelect"
          >
            <el-table-column
              v-if="multiple"
              width="40"
              align="center"
            >
              <template #default="{ row }">
                <el-icon
                  v-if="isRowSelected(row)"
                  class="lookup-check"
                ><Check /></el-icon>
              </template>
            </el-table-column>
            <el-table-column
              v-for="col in visibleColumns"
              :key="col.prop"
              :prop="col.prop"
              :label="col.label"
              min-width="120"
            />
          </el-table>
        </div>
        <div
          v-if="filteredResults.length === 0"
          class="lookup-no-data"
        >
          No data
        </div>
      </div>
    </Teleport>

    <!-- View display after selection (single-select only; multi uses tags) -->
    <div
      v-if="showBackfillView && !multiple && selectedRow && displayViewFields.length > 0"
      class="lookup-view-display"
    >
      <el-descriptions
        :column="1"
        border
        size="small"
        direction="horizontal"
      >
        <el-descriptions-item
          v-for="field in displayViewFields"
          :key="field.fieldName"
          :label="field.displayLabel || field.fieldName"
          label-class-name="lookup-view-label"
          class-name="lookup-view-value"
        >
          {{ selectedRow[field.fieldName] ?? '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Search, Close, Check } from '@element-plus/icons-vue'
import type { LookupFilterCondition } from '@/utils/lookupFilterConditions'
import {
  useLookupPreview,
  type LookupPreviewFieldDef,
  type LookupPreviewViewField,
} from '@/composables/designer/useLookupPreview'

const props = withDefaults(defineProps<{
  modelValue?: unknown
  label: string
  placeholder?: string
  searchFields: string[]
  displayFields: string[]
  selectedDisplayField?: string
  filterConditions?: LookupFilterCondition[]
  viewFields: LookupPreviewViewField[]
  fieldDefs: LookupPreviewFieldDef[]
  showBackfillView?: boolean
  readonly?: boolean
  /** Parity with Admin Center / Portal LookupField — value is PK array when true. */
  multiple?: boolean
  /**
   * Extra mock columns for cascade joins (parent fromColumn / child toColumn) when they are
   * not in displayFields — otherwise derived filters never match preview rows.
   */
  ensureMockFields?: string[]
}>(), {
  showBackfillView: true,
  readonly: false,
  multiple: false,
  ensureMockFields: () => [],
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: unknown): void
  /** Fired with the toggled/selected row — used for LOOKUP cascade parent state (esp. multiple). */
  (e: 'select', row: Record<string, unknown>): void
  (e: 'clear'): void
}>()

const {
  dropdownRef,
  fieldRef,
  dropdownVisible,
  dropdownStyle,
  searchKeyword,
  selectedRow,
  selectedRows,
  visibleColumns,
  displayViewFields,
  filteredResults,
  getDisplayText,
  isRowSelected,
  handleWrapperClick,
  handleFieldClick,
  handleSelect,
  removeSelectedAt,
  handleClear,
  onInputFocus,
} = useLookupPreview(props, emit)
</script>

<style lang="scss" scoped>
.lookup-preview-wrapper {
  margin-bottom: 18px;
}

.lookup-form-item {
  display: flex;
  align-items: flex-start;
}

.lookup-label-text {
  white-space: nowrap;
  width: auto;
  min-width: fit-content;
  max-width: 200px;
  height: auto;
  line-height: 1.5;
  padding-top: 6px;
  padding-right: 12px;
  font-size: 14px;
  color: #606266;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 4px;
}

.lookup-label-icon {
  color: #409eff;
  font-size: 14px;
}

.lookup-field {
  flex: 1;
  min-width: 0;
  position: relative;

  &.readonly {
    cursor: not-allowed;
    pointer-events: none;

    .lookup-selected-wrapper {
      background: var(--el-disabled-bg-color, #f5f7fa);
      border-color: var(--el-disabled-border-color, #e4e7ed);
      cursor: not-allowed;
    }

    .lookup-input :deep(.el-input__wrapper) {
      background-color: var(--el-disabled-bg-color, #f5f7fa);
      box-shadow: 0 0 0 1px var(--el-disabled-border-color, #e4e7ed) inset;
      cursor: not-allowed;
      pointer-events: none;
    }
  }

  .lookup-input {
    width: 100%;
  }

  .lookup-selected-wrapper {
    display: flex;
    align-items: center;
    min-height: 32px;
    padding: 4px 8px;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    background: #fff;
  }

  .lookup-multi-wrapper {
    flex-wrap: wrap;
    gap: 4px;
  }

  .lookup-multi-input {
    flex: 1;
    min-width: 80px;
    border: none;
    outline: none;
    font-size: 13px;
    line-height: 24px;
    background: transparent;
  }

  .lookup-selected-tag {
    display: inline-flex;
    align-items: center;
    max-width: 100%;
    height: 24px;
    padding: 0 8px;
    border-radius: 4px;
    background: #f0f2f5;
    font-size: 13px;
    color: #909399;
    line-height: 24px;

    .lookup-selected-text {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .lookup-selected-close {
      flex-shrink: 0;
      margin-left: 4px;
      font-size: 13px;
      color: #909399;
      cursor: pointer;

      &:hover {
        color: #606266;
      }
    }
  }
}

.lookup-readonly-empty {
  color: #909399;
  line-height: 32px;
}

.lookup-check {
  color: var(--el-color-primary);
}

.lookup-dropdown-panel {
  z-index: 3000;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  overflow: hidden;

  .lookup-no-data {
    padding: 16px;
    text-align: center;
    color: #909399;
    font-size: 13px;
  }
}

.lookup-view-display {
  margin-top: 8px;

  :deep(.lookup-view-label) {
    width: 40%;
    font-weight: 500;
    color: #606266;
    background: #fafafa;
  }

  :deep(.lookup-view-value) {
    color: #303133;
  }
}
</style>
