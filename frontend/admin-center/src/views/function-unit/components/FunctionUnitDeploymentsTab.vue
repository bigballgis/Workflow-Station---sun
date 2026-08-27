<template>
  <div>
    <el-card
      v-loading="loading"
      class="table-card"
    >
      <div
        :ref="bindScrollRef"
        class="list-data-grid-scroll"
      >
        <div
          class="list-data-grid-inner"
          :style="gridInnerStyle"
        >
          <el-table
            :data="displayRows"
            stripe
            border
            :fit="false"
            table-layout="fixed"
            style="width: 100%"
            class="list-data-grid"
            :class="{ 'list-data-grid--fit': gridFits }"
          >
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
                  @sort-change="(direction: 'ASC' | 'DESC') => onSort(col.field, direction)"
                  @clear-sort="onClearSort"
                  @filter-open="openFilter(col.field)"
                  @clear-filter="onClearFilter(col.field)"
                  @move="(direction: 'left' | 'right') => moveColumn(col.field, direction)"
                  @width-change="(width: number) => setWidth(col.field, width)"
                  @width-commit="persistWidths"
                />
              </template>
              <template #default="{ row }">
<el-tag
                  v-if="col.field === 'status'"
                  :type="deployStatusType(row.status)"
                >
                  {{ row.status }}
                </el-tag>
                <template v-else>
                  {{ row[col.field as keyof typeof row] ?? '-' }}
                </template>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <ListPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        :loading="loading"
        @change="emit('fetch')"
      />
    </el-card>

    <ListFilterDialog
      v-model:visible="filterDialog.visible"
      :column="activeFilterColumn"
      :filter="activeFilter"
      :remote-search="searchListFilterUsers"
      @apply="onFilterApply"
      @clear="onFilterClear"
    />
  </div>
</template>

<script setup lang="ts">
import type { ComponentPublicInstance, Ref } from 'vue'
import ListColumnHeader from '@platform-shared/list/ListColumnHeader.vue'
import ListFilterDialog from '@platform-shared/list/ListFilterDialog.vue'
import ListPagination from '@platform-shared/list/ListPagination.vue'
import type { ListColumnFilter } from '@platform-shared/list/columnMeta'
import { searchListFilterUsers } from '@/composables/list/searchListFilterUsers'
import { deployStatusType } from '@/utils/format'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'
import type { Deployment } from '@/api/functionUnit'

type ListGrid = ReturnType<typeof useAdminListGrid<Deployment>>

const props = defineProps<{
  grid: ListGrid
  loading: boolean
}>()

const {
  displayColumns,
  displayRows,
  columnFilters,
 
  gridFits,
  gridInnerStyle,
  activeFilterColumn,
  activeFilter,
  sort,
  filterDialog,
  pagination,
  widthOf,
  setWidth,
  persistWidths,
  moveColumn,
  openFilter,
  applySort,
  clearSort,
  applyFilter,
  clearFilter,
} = props.grid

const emit = defineEmits<{ fetch: [] }>()

function bindScrollRef(el: Element | ComponentPublicInstance | null) {
  const node = el instanceof Element ? el : el?.$el ?? null
  ;(props.grid.gridScrollRef as Ref<Element | null>).value =
    node instanceof Element ? node : null
}

function onSort(field: string, direction: 'ASC' | 'DESC') {
  applySort(field, direction)
  emit('fetch')
}
function onClearSort() {
  clearSort()
  emit('fetch')
}
function onClearFilter(field: string) {
  clearFilter(field)
  emit('fetch')
}
function onFilterApply(filter: ListColumnFilter) {
  applyFilter(filter)
  emit('fetch')
}
function onFilterClear() {
  onClearFilter(filterDialog.field)
}
</script>
