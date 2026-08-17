<script setup lang="ts">
import { computed, nextTick } from 'vue'
import {
  PORTAL_LIST_PAGE_SIZES,
  PORTAL_LIST_PAGINATION_LAYOUT,
} from '@/constants/portalListPagination'

const props = withDefaults(
  defineProps<{
    currentPage: number
    pageSize: number
    total: number
    disabled?: boolean
    /**
     * Drop the pager when there is nothing to page through. Default keeps it
     * mounted: an unmounted pager cannot report the page correction below, so a
     * list that empties out would leave its caller stuck on a page that is gone.
     */
    hideWhenEmpty?: boolean
  }>(),
  {
    disabled: false,
    hideWhenEmpty: false,
  },
)

const emit = defineEmits<{
  'update:currentPage': [page: number]
  'update:pageSize': [size: number]
  /** Emitted once per user-visible page change — callers refetch here. */
  change: []
}>()

const pageSizes = [...PORTAL_LIST_PAGE_SIZES]

const lastPage = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize) || 1))

// el-pagination clamps its own current page when the size grows, so a single
// size change reaches us as size-change *and* current-change. Emitting `change`
// for both makes every caller fire two list requests for one click.
let withinSizeChange = false

function onCurrentChange(page: number) {
  if (withinSizeChange) return
  // el-pagination owns the "total shrank past my page" correction and reports it
  // through this same event; clamping here only guards against a caller asking
  // the backend for a page that cannot exist.
  emit('update:currentPage', Math.min(Math.max(page, 1), lastPage.value))
  emit('change')
}

function onSizeChange(size: number) {
  withinSizeChange = true
  emit('update:pageSize', size)
  emit('update:currentPage', 1)
  emit('change')
  nextTick(() => {
    withinSizeChange = false
  })
}
</script>

<template>
  <div
    v-if="!hideWhenEmpty || total > 0"
    class="portal-list-pagination"
  >
    <el-pagination
      background
      :current-page="currentPage"
      :page-size="pageSize"
      :total="total"
      :disabled="disabled"
      :page-sizes="pageSizes"
      :layout="PORTAL_LIST_PAGINATION_LAYOUT"
      @current-change="onCurrentChange"
      @size-change="onSizeChange"
    />
  </div>
</template>

<style scoped lang="scss">
.portal-list-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
