<script setup lang="ts" generic="T">
import { computed, unref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import DesignerListColumnHeader from './DesignerListColumnHeader.vue'
import DesignerListFilterDialog from './DesignerListFilterDialog.vue'
import {
  useDesignerListGrid,
  type DesignerListTableColumn,
} from '@/composables/useDesignerListGrid'

const props = withDefaults(
  defineProps<{
    loading?: boolean
    storageKey: string | Ref<string>
    columns: DesignerListTableColumn<T>[]
    rows: Ref<T[]> | T[] | (() => T[])
    rowKey?: string
    stripe?: boolean
    showActions?: boolean
    actionsMinWidth?: number
    actionsWidth?: number | string
    actionsLabel?: string
    actionsFixed?: boolean | 'left' | 'right'
    actionsAlign?: 'left' | 'center' | 'right'
    tableStyle?: string
    tableClass?: string
  }>(),
  {
    loading: false,
    stripe: true,
    showActions: true,
    actionsMinWidth: 200,
    actionsFixed: 'right',
    actionsAlign: 'left',
    tableStyle: 'width: 100%',
  },
)

const emit = defineEmits<{
  rowClick: [row: T]
}>()

const { t } = useI18n()

const rowsSource = computed<T[]>(() => {
  const source = props.rows
  if (typeof source === 'function') return source()
  return unref(source) ?? []
})

const {
  displayRows,
  columnWidth,
  hasFilter,
  handleResize,
  handleResizeEnd,
  openColumnFilter,
  applyColumnFilter,
  clearColumnFilter,
  filterDialogVisible,
  filterColumnLabel,
  currentFilter,
} = useDesignerListGrid<T>({
  storageKey: computed(() => unref(props.storageKey)),
  rows: () => rowsSource.value,
  columns: props.columns,
})

function cellText(row: T, col: DesignerListTableColumn<T>): string {
  const value = col.getValue
    ? col.getValue(row)
    : (row as Record<string, unknown>)[col.prop ?? col.key]
  if (value == null) return ''
  return String(value)
}
</script>

<template>
  <el-table
    v-loading="loading"
    :data="displayRows"
    :row-key="rowKey"
    :stripe="stripe"
    :style="tableStyle"
    :class="['dwl-data-grid', tableClass]"
    @row-click="(row: T) => emit('rowClick', row)"
  >
    <el-table-column
      v-for="col in columns"
      :key="col.key"
      :prop="col.prop ?? col.key"
      :label="col.label"
      :min-width="columnWidth(col.key, col.defaultWidth)"
      :show-overflow-tooltip="col.showOverflowTooltip"
    >
      <template #header>
        <DesignerListColumnHeader
          :label="col.label"
          :has-filter="hasFilter(col.key)"
          :width="columnWidth(col.key, col.defaultWidth)"
          @filter="openColumnFilter(col.key, col.label)"
          @clear-filter="clearColumnFilter(col.key)"
          @resize="(w: number) => handleResize(col.key, w)"
          @resize-end="handleResizeEnd"
        />
      </template>
      <template #default="{ row }">
        <slot
          :name="`cell-${col.key}`"
          :row="row"
        >
          {{ cellText(row, col) }}
        </slot>
      </template>
    </el-table-column>
    <el-table-column
      v-if="showActions"
      :label="actionsLabel ?? t('common.actions')"
      :width="actionsWidth"
      :min-width="actionsMinWidth"
      :fixed="actionsFixed"
      :align="actionsAlign"
    >
      <template #default="{ row }">
        <slot
          name="actions"
          :row="row"
        />
      </template>
    </el-table-column>
  </el-table>

  <DesignerListFilterDialog
    v-model:visible="filterDialogVisible"
    :label="filterColumnLabel"
    :filter="currentFilter"
    @apply="applyColumnFilter"
    @clear="clearColumnFilter"
  />
</template>
