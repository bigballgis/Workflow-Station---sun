<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    page: number
    size: number
    total: number
    loading?: boolean
    pageSizes?: number[]
  }>(),
  {
    loading: false,
    // Uniform default across apps (code-quality standard: consistent per-page count).
    pageSizes: () => [10, 20, 50, 100],
  },
)

const emit = defineEmits<{
  'update:page': [page: number]
  'update:size': [size: number]
  /** Single reload trigger so consumers don't double-fetch on size changes. */
  change: [payload: { page: number; size: number }]
}>()

// A size change makes el-pagination clamp/emit current-change in the same tick;
// swallow that follow-up so `change` fires exactly once per user action.
let sizeChangeInFlight = false

function onSizeChange(size: number) {
  sizeChangeInFlight = true
  queueMicrotask(() => {
    sizeChangeInFlight = false
  })
  emit('update:size', size)
  if (props.page !== 1) {
    emit('update:page', 1)
  }
  emit('change', { page: 1, size })
}

function onPageChange(page: number) {
  if (sizeChangeInFlight) return
  emit('update:page', page)
  emit('change', { page, size: props.size })
}
</script>

<template>
  <el-pagination
    class="list-pagination"
    :current-page="page"
    :page-size="size"
    :total="total"
    :page-sizes="pageSizes"
    :disabled="loading"
    layout="total, sizes, prev, pager, next, jumper"
    @size-change="onSizeChange"
    @current-change="onPageChange"
  />
</template>

<style scoped>
.list-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
