<template>
  <el-table
    :data="rows"
    border
    stripe
    :empty-text="emptyText"
  >
    <el-table-column
      :label="t('snapshotDiff.fieldName')"
      prop="label"
      min-width="150"
    />
    <el-table-column
      :label="t('snapshotDiff.snapshotValue')"
      min-width="200"
    >
      <template #default="{ row }">
        <span
          v-if="row.changed && showLiveValues"
          class="snapshot-value changed"
        >
          <del>{{ formatValue(row.snapshotValue, row.key) }}</del>
        </span>
        <span
          v-else
          class="snapshot-value"
        >{{ formatValue(row.snapshotValue, row.key) }}</span>
      </template>
    </el-table-column>
    <el-table-column
      v-if="showLiveValues"
      :label="t('snapshotDiff.liveValue')"
      min-width="200"
    >
      <template #default="{ row }">
        <span
          v-if="row.changed"
          class="live-value changed"
        >{{ formatValue(row.liveValue, row.key) }}</span>
        <span
          v-else
          class="live-value"
        >{{ formatValue(row.liveValue, row.key) }}</span>
      </template>
    </el-table-column>
    <el-table-column
      v-if="showLiveValues"
      :label="t('snapshotDiff.status')"
      width="110"
      align="center"
    >
      <template #default="{ row }">
        <el-tag
          v-if="row.changed"
          type="warning"
          size="small"
        >
          {{ t('snapshotDiff.changed') }}
        </el-tag>
        <el-tag
          v-else
          type="info"
          size="small"
        >
          {{ t('snapshotDiff.unchanged') }}
        </el-tag>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { DiffRow } from './snapshotDiffHelpers'

defineProps<{
  rows: DiffRow[]
  showLiveValues: boolean
  formatValue: (value: unknown, fieldKey?: string) => string
  emptyText?: string
}>()

const { t } = useI18n()
</script>

<style scoped lang="scss">
.snapshot-value.changed del {
  color: #f56c6c;
  text-decoration: line-through;
}

.live-value.changed {
  color: #67c23a;
  font-weight: 500;
}
</style>
