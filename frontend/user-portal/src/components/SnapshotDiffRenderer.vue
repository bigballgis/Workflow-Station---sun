<template>
  <div class="snapshot-diff-renderer">
    <el-table
      :data="diffRows"
      border
      stripe
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
            v-if="row.changed"
            class="snapshot-value changed"
          >
            <del>{{ formatValue(row.snapshotValue) }}</del>
          </span>
          <span
            v-else
            class="snapshot-value"
          >{{ formatValue(row.snapshotValue) }}</span>
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
          >{{ formatValue(row.liveValue) }}</span>
          <span
            v-else
            class="live-value"
          >{{ formatValue(row.liveValue) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        v-if="showLiveValues"
        :label="t('snapshotDiff.changed')"
        width="100"
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
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormField } from './formRendererHelpers'
import { computeDiffRows, type DiffRow } from './snapshotDiffHelpers'

const { t } = useI18n()

interface Props {
  snapshotValues: Record<string, unknown>
  liveValues: Record<string, unknown>
  fields: FormField[]
  showLiveValues: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showLiveValues: true,
})

const diffRows = computed<DiffRow[]>(() =>
  computeDiffRows(props.snapshotValues, props.liveValues, props.fields)
)

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
</script>

<style scoped lang="scss">
.snapshot-diff-renderer {
  width: 100%;

  .snapshot-value.changed del {
    color: #f56c6c;
    text-decoration: line-through;
  }

  .live-value.changed {
    color: #67c23a;
    font-weight: 500;
  }
}
</style>
