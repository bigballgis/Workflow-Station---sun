<template>
  <el-dialog :model-value="visible" :title="t('subTable.batchEdit')" width="min(760px, calc(100vw - 32px))" append-to-body :close-on-click-modal="false" @update:model-value="emit('update:visible', $event)">
    <p class="batch-delete-dialog__hint">{{ t('subTable.batchDeleteHint') }}</p>
    <el-table :data="pageRows" border size="small" max-height="420" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="48" />
      <el-table-column type="index" width="58" />
      <el-table-column v-for="column in columns" :key="column.field" :prop="column.field" :label="column.label" min-width="150" show-overflow-tooltip />
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="20" :total="rows.length" layout="prev, pager, next" />
    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="danger" :disabled="!selected.size" @click="apply">{{ t('subTable.delete') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { Column } from '@/composables/subTableField/subTableFieldTypes'
import type { SubTableBatchChange } from '@/utils/subTableBatchChanges'

const props = defineProps<{ visible: boolean; rows: Array<Record<string, unknown>>; columns: Column[]; batchControlId: string; bindingId: string; rowIdentity: (row: Record<string, unknown>) => string | null; rowScopeKey: (row: Record<string, unknown>) => Record<string, unknown> | null }>()
const emit = defineEmits<{ (event: 'update:visible', value: boolean): void; (event: 'apply', rows: Array<Record<string, unknown>>, change: SubTableBatchChange): void }>()
const { t } = useI18n()
const selected = ref<Map<string, Record<string, unknown>>>(new Map())
const baseline = ref('')
const applying = ref(false)
const page = ref(1)
const pageRows = computed(() => props.rows.slice((page.value - 1) * 20, page.value * 20))
watch(() => props.visible, visible => {
  if (!visible) return
  selected.value = new Map()
  page.value = 1
  baseline.value = JSON.stringify(props.rows)
}, { immediate: true })
function onSelectionChange(rows: Array<Record<string, unknown>>) { selected.value = new Map(rows.flatMap(row => { const id = props.rowIdentity(row); return id ? [[id, row] as const] : [] })) }
async function apply() {
  if (!selected.value.size || applying.value) return
  if (selected.value.size > 200) { ElMessage.error(t('subTable.batchLimit')); return }
  applying.value = true
  try { await ElMessageBox.confirm(t('subTable.batchDeleteConfirm', { count: selected.value.size }), t('subTable.deleteConfirm'), { type: 'warning' }) }
  catch { applying.value = false; return }
  if (JSON.stringify(props.rows) !== baseline.value) { applying.value = false; ElMessage.error(t('subTable.batchStaleDraft')); return }
  const patches = Array.from(selected.value, ([rowId, row]) => { const rowKey = props.rowScopeKey(row); return rowKey ? { rowId, rowKey } : null }).filter((patch): patch is { rowId: string; rowKey: Record<string, unknown> } => patch !== null)
  if (patches.length !== selected.value.size) { applying.value = false; ElMessage.error(t('common.operationFailed')); return }
  emit('apply', props.rows.filter(row => !selected.value.has(props.rowIdentity(row) ?? '')), { batchControlId: props.batchControlId, bindingId: props.bindingId, operation: 'DELETE', rowPatches: patches })
  emit('update:visible', false)
  applying.value = false
}
</script>

<style scoped>.batch-delete-dialog__hint { margin-top: 0; color: var(--el-text-color-secondary); }</style>
