<template>
  <div class="member-shared-list">
    <div
      ref="gridScrollRef"
      class="list-data-grid-scroll"
    >
      <div
        class="list-data-grid-inner"
        :style="gridInnerStyle"
      >
        <el-table
          v-loading="loading === true"
          :data="pageRows"
          stripe
          :fit="false"
          table-layout="fixed"
          style="width: 100%;"
          class="list-data-grid"
          :class="{ 'list-data-grid--fit': gridFits }"
          scrollbar-always-on
          :height="gridTableHeight || '100%'"
        >
          <template #empty>
            <span>{{ emptyText }}</span>
          </template>
          <el-table-column
            v-for="(col, colIndex) in displayColumns"
            :key="col.field"
            :prop="col.field"
            :width="widthOf(col.field)"
            show-overflow-tooltip
          >
            <template #header>
              <ListColumnHeader
                :column="col"
                :sort="sort.field === col.field ? sort.direction : null"
                :filtered="!!columnFilters[col.field]"
                :width="widthOf(col.field)"
                :show-move="displayColumns.length > 1"
                :can-move-left="colIndex > 0"
                :can-move-right="colIndex < displayColumns.length - 1"
                @sort-change="(direction: 'ASC' | 'DESC') => applySort(col.field, direction)"
                @clear-sort="clearSort"
                @filter-open="openFilter(col.field)"
                @clear-filter="clearFilter(col.field)"
                @move="(direction: 'left' | 'right') => moveColumn(col.field, direction)"
                @width-change="(width: number) => setWidth(col.field, width)"
                @width-commit="persistWidths"
              />
            </template>
            <template #default="{ row }">
              <slot
                name="cell"
                :column="col"
                :row="row"
              >
                {{ cellText(row, col.field) }}
              </slot>
            </template>
          </el-table-column>
          <slot name="action-column" />
        </el-table>
      </div>
    </div>
    <ListPagination
      v-model:page="pagination.page"
      v-model:size="pagination.size"
      :total="pageTotal"
      :loading="loading"
    />
    <ListFilterDialog
      v-model:visible="filterDialog.visible"
      :column="activeFilterColumn"
      :filter="activeFilter"
      @apply="onFilterApply"
      @clear="onFilterClear"
    />
  </div>
</template>

<script setup lang="ts" generic="T extends object">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ListColumnHeader from '@platform-shared/list/ListColumnHeader.vue'
import ListFilterDialog from '@platform-shared/list/ListFilterDialog.vue'
import ListPagination from '@platform-shared/list/ListPagination.vue'
import type { ListColumnFilter, ListColumnMeta } from '@platform-shared/list/columnMeta'
import { useListColumnLayout } from '@platform-shared/list/useListColumnLayout'
import { applyClientListQuery } from '@platform-shared/list/clientListQuery'

const props = defineProps<{
  columns: ListColumnMeta[]
  rows: T[]
  loading?: boolean
  storageKey: string
  extraWidth?: number
  emptyText: string
  getValue: (row: T, field: string) => unknown
}>()

const { t } = useI18n()
const columnOrder = ref<string[]>([])
const columnFilters = ref<Record<string, ListColumnFilter>>({})
const sort = reactive<{ field: string | null; direction: 'ASC' | 'DESC' | null }>({
  field: null,
  direction: null,
})
const filterDialog = reactive({ visible: false, field: '' })
const pagination = reactive({ page: 1, size: 20 })

const displayColumns = computed<ListColumnMeta[]>(() => {
  const localized = props.columns.map((col) => ({
    ...col,
    label: t(col.label),
  }))
  if (columnOrder.value.length === 0) return localized
  const byField = new Map(localized.map((col) => [col.field, col]))
  const ordered: ListColumnMeta[] = []
  for (const field of columnOrder.value) {
    const col = byField.get(field)
    if (col) ordered.push(col)
  }
  for (const col of localized) {
    if (!columnOrder.value.includes(col.field)) ordered.push(col)
  }
  return ordered
})

const layoutFields = computed(() => displayColumns.value.map((col) => col.field))
const { gridScrollRef, gridFits, gridTableHeight, gridInnerStyle, widthOf, setWidth, persistWidths } =
  useListColumnLayout({
    storageKey: () => props.storageKey,
    fields: layoutFields,
    extraWidth: () => props.extraWidth ?? 0,
    labelOf: (field) => displayColumns.value.find((col) => col.field === field)?.label ?? field,
    kindOf: (field) => displayColumns.value.find((col) => col.field === field)?.kind,
  })

const queried = computed(() => applyClientListQuery({
  rows: props.rows,
  columns: displayColumns.value,
  getValue: props.getValue,
  filters: columnFilters.value,
  sort,
  page: pagination.page,
  size: pagination.size,
}))

const pageRows = computed(() => queried.value.content)
const pageTotal = computed(() => queried.value.totalElements)

const activeFilterColumn = computed(
  () => displayColumns.value.find((col) => col.field === filterDialog.field) ?? null,
)
const activeFilter = computed(() => columnFilters.value[filterDialog.field] ?? null)

function cellText(row: T, field: string): string {
  const value = props.getValue(row, field)
  if (value == null || value === '') return '-'
  return String(value)
}

function syncOrder() {
  const fields = props.columns.map((col) => col.field)
  const next = columnOrder.value.filter((field) => fields.includes(field))
  for (const field of fields) {
    if (!next.includes(field)) next.push(field)
  }
  columnOrder.value = next
}

function moveColumn(field: string, direction: 'left' | 'right') {
  const order = [...columnOrder.value]
  const index = order.indexOf(field)
  if (index < 0) return
  const swapWith = direction === 'left' ? index - 1 : index + 1
  if (swapWith < 0 || swapWith >= order.length) return
  ;[order[index], order[swapWith]] = [order[swapWith], order[index]]
  columnOrder.value = order
}

function openFilter(field: string) {
  filterDialog.field = field
  filterDialog.visible = true
}

function applySort(field: string, direction: 'ASC' | 'DESC') {
  sort.field = field
  sort.direction = direction
  pagination.page = 1
}

function clearSort() {
  sort.field = null
  sort.direction = null
  pagination.page = 1
}

function clearFilter(field: string) {
  const next = { ...columnFilters.value }
  delete next[field]
  columnFilters.value = next
  filterDialog.visible = false
  pagination.page = 1
}

function onFilterApply(filter: ListColumnFilter) {
  columnFilters.value = { ...columnFilters.value, [filterDialog.field]: filter }
  filterDialog.visible = false
  pagination.page = 1
}

function onFilterClear() {
  clearFilter(filterDialog.field)
}

watch(() => props.columns.map((col) => col.field).join(','), syncOrder, { immediate: true })
watch(() => props.rows, () => { pagination.page = 1 }, { deep: false })
</script>

<style lang="scss">
@import '@/styles/listDataGrid.scss';
</style>
