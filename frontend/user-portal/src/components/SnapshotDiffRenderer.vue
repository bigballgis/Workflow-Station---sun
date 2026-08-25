<template>
  <div class="snapshot-diff-renderer">
    <SnapshotDiffTable
      :rows="diffRows"
      :show-live-values="showLiveValues"
      :format-value="formatValue"
    />
    <div
      v-for="group in subTableGroups"
      :key="group.bindingId"
      class="snapshot-sub-table"
    >
      <div class="snapshot-sub-table-title">
        {{ group.tableLabel }}
      </div>
      <div
        v-for="block in group.blocks"
        :key="`${group.bindingId}:${block.rowIndex}`"
        class="snapshot-sub-table-block"
      >
        <div
          v-if="group.blocks.length > 1"
          class="snapshot-sub-table-row-title"
        >
          {{ block.preview || t('snapshotDiff.subTableRow', { n: block.rowIndex + 1 }) }}
        </div>
        <SnapshotDiffTable
          :rows="block.rows"
          :show-live-values="showLiveValues"
          :format-value="formatValue"
          :empty-text="t('snapshotDiff.noSubTableRows')"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormField, FormTab } from './formRendererHelpers'
import SnapshotDiffTable from './SnapshotDiffTable.vue'
import {
  collectSnapshotDiffFields,
  computeDiffRows,
  formatSnapshotDisplayValue,
  type DiffRow,
} from './snapshotDiffHelpers'
import {
  buildSnapshotSubTableDiffGroups,
} from './snapshotDiffSubTableGroups'
import type { SnapshotSubTableBindingSource } from './snapshotDiffSubTables'
import {
  applySensitiveMask,
  isSensitiveMaskActive,
} from '@/utils/sensitiveMask'

const { t } = useI18n()

interface Props {
  snapshotValues: Record<string, unknown>
  liveValues: Record<string, unknown>
  fields: FormField[]
  tabs?: FormTab[]
  fieldsAfterTabs?: FormField[]
  subTableBindings?: SnapshotSubTableBindingSource[]
  showLiveValues: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showLiveValues: true,
  subTableBindings: () => [],
})

const comparableFields = computed(() =>
  collectSnapshotDiffFields(props.fields, props.tabs, props.fieldsAfterTabs)
)

const diffRows = computed<DiffRow[]>(() =>
  computeDiffRows(
    props.snapshotValues,
    props.liveValues,
    props.fields,
    props.tabs,
    props.fieldsAfterTabs,
  )
)

const subTableGroups = computed(() =>
  buildSnapshotSubTableDiffGroups(
    props.fields,
    props.snapshotValues,
    props.liveValues,
    props.subTableBindings,
    props.tabs,
    props.fieldsAfterTabs,
  )
)

const fieldByKey = computed(() => {
  const map = new Map<string, FormField>()
  for (const f of comparableFields.value) {
    map.set(f.key, f)
  }
  return map
})

const maskByKey = computed(() => {
  const map = new Map<string, NonNullable<FormField['sensitiveMask']>>()
  for (const f of comparableFields.value) {
    if (f.sensitiveMask?.enabled) map.set(f.key, f.sensitiveMask)
  }
  return map
})

function formatValue(value: unknown, fieldKey?: string): string {
  const field = fieldKey ? fieldByKey.value.get(fieldKey) : undefined
  const s = formatSnapshotDisplayValue(value, field)
  const cfg = fieldKey ? maskByKey.value.get(fieldKey) : undefined
  if (isSensitiveMaskActive(cfg) && s !== '-') return applySensitiveMask(s, cfg!)
  return s
}
</script>

<style scoped lang="scss">
.snapshot-diff-renderer {
  width: 100%;

  .snapshot-sub-table {
    margin-top: 16px;
  }

  .snapshot-sub-table-title {
    margin-bottom: 8px;
    font-weight: 500;
    color: var(--text-primary, #303133);
  }

  .snapshot-sub-table-block + .snapshot-sub-table-block {
    margin-top: 12px;
  }

  .snapshot-sub-table-row-title {
    margin-bottom: 8px;
    font-size: 13px;
    color: var(--text-secondary, #606266);
  }
}
</style>
