<template>
  <div
    v-loading="loading"
    class="batch-groups"
    data-test="batch-task-groups"
  >
    <div class="batch-groups__head">
      <span class="batch-groups__title">{{ t('task.batchGroups') }}</span>
      <span class="batch-groups__summary">
        {{ t('task.batchGroupsSummary', { groups: groups.length, count: total }) }}
      </span>
    </div>

    <div
      v-if="groups.length === 0 && !loading"
      class="batch-groups__empty"
    >
      {{ t('task.batchGroupsEmpty') }}
    </div>

    <!-- Same header + width drag as the To Do grid (shared ListColumnHeader / useListColumnLayout);
         a native table keeps the merged Function Unit cell. -->
    <div
      v-else
      ref="gridScrollRef"
      class="batch-groups__scroll"
    >
      <table class="batch-groups__table">
        <colgroup>
          <col
            v-for="col in columns"
            :key="col.field"
            :style="{ width: `${widthOf(col.field)}px` }"
          >
          <col :style="{ width: `${OPEN_COLUMN_WIDTH}px` }">
        </colgroup>
        <thead>
          <tr>
            <th
              v-for="col in columns"
              :key="col.field"
            >
              <ListColumnHeader
                :column="col"
                :width="widthOf(col.field)"
                @width-change="(width: number) => setWidth(col.field, width)"
                @width-commit="persistWidths"
              />
            </th>
            <th />
          </tr>
        </thead>
        <tbody>
          <template
            v-for="fu in byFunctionUnit"
            :key="fu.key"
          >
            <!-- One row per node of one release; the Function Unit cell spans all of its rows. -->
            <tr
              v-for="(g, index) in fu.groups"
              :key="g.groupKey + '/' + g.taskDefinitionKey"
              class="batch-groups__row"
              :class="{ 'batch-groups__row--first': index === 0 }"
              data-test="batch-group-row"
            >
              <td
                v-if="index === 0"
                class="batch-groups__fu"
                :rowspan="fu.groups.length"
              >
                <div class="batch-groups__fu-name">
                  {{ fu.name }}
                </div>
                <div class="batch-groups__fu-count">
                  {{ t('task.batchPendingCount', { count: fu.count }) }}
                </div>
              </td>
              <td class="batch-groups__step">
                {{ g.nodeName }}
              </td>
              <td class="batch-groups__muted">
                {{ g.version || '-' }}
              </td>
              <td class="batch-groups__count">
                {{ g.count }}
              </td>
              <td>
                <span class="batch-groups__actions">
                  <el-tag
                    v-for="a in g.actions"
                    :key="a.actionId"
                    size="small"
                    effect="plain"
                    :type="resolveActionButtonType(a.actionType, a.buttonColor) ?? 'info'"
                    :style="chipStyle(a.buttonColor)"
                  >
                    {{ a.actionName }}
                  </el-tag>
                </span>
              </td>
              <td class="batch-groups__open">
                <el-button
                  link
                  type="primary"
                  data-test="batch-group-open"
                  @click="emit('open', g)"
                >
                  {{ t('task.viewTasks') }}
                  <el-icon class="el-icon--right">
                    <ArrowRight />
                  </el-icon>
                </el-button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight } from '@element-plus/icons-vue'
import ListColumnHeader from '@platform-shared/list/ListColumnHeader.vue'
import type { ListColumnMeta } from '@platform-shared/list/columnMeta'
import { useListColumnLayout } from '@platform-shared/list/useListColumnLayout'
import type { BatchTaskGroup } from '@/api/task'
import { isCustomButtonColor, resolveActionButtonType } from '@/utils/actionButtonColor'

const props = defineProps<{
  groups: BatchTaskGroup[]
  total: number
  loading: boolean
}>()

const emit = defineEmits<{
  open: [group: BatchTaskGroup]
}>()

const { t } = useI18n()

/** The "View Tasks" link column: a fixed link, not a data column, so it is not resizable. */
const OPEN_COLUMN_WIDTH = 120

/** Display-only columns: no sort / filter menu, only the width drag. */
const columns = computed<ListColumnMeta[]>(() => ([
  ['functionUnit', t('task.functionUnit'), 'TEXT'],
  ['taskName', t('task.taskName'), 'TEXT'],
  ['version', t('task.batchGroupVersion'), 'TEXT'],
  ['count', t('task.batchGroupCount'), 'NUMBER'],
  ['actions', t('task.batchGroupActions'), 'TEXT'],
] as const).map(([field, label, kind]) => ({ field, label, kind, filterable: false, sortable: false, operators: [] })))

// Default widths follow the shared rule (header fit vs. kind floor, filled to the card); no per-page table.
const { gridScrollRef, widthOf, setWidth, persistWidths } = useListColumnLayout({
  storageKey: 'portal-list-layout:todo-batch-groups-v1',
  fields: () => columns.value.map((col) => col.field),
  extraWidth: OPEN_COLUMN_WIDTH,
  labelOf: (field) => columns.value.find((col) => col.field === field)?.label ?? field,
  kindOf: (field) => columns.value.find((col) => col.field === field)?.kind,
  fillViewport: false,
})

/** A designer hex colour tints the chip (soft pill, like the themed tags) the way it colours the task-page button. */
function chipStyle(color?: string | null): Record<string, string> | undefined {
  if (!isCustomButtonColor(color)) return undefined
  return { color: String(color), backgroundColor: `color-mix(in srgb, ${color} 12%, transparent)` }
}

/** Groups arrive per node of one release; rows of the same Function Unit sit together. */
const byFunctionUnit = computed(() => {
  const map = new Map<string, { key: string; name: string; count: number; groups: BatchTaskGroup[] }>()
  for (const g of props.groups) {
    const key = g.processDefinitionKey
    let fu = map.get(key)
    if (!fu) {
      fu = { key, name: g.functionUnitName || key, count: 0, groups: [] }
      map.set(key, fu)
    }
    fu.count += g.count
    fu.groups.push(g)
  }
  return [...map.values()]
})
</script>

<style lang="scss" scoped>
.batch-groups {
  flex: 1;
  min-height: 0;
  overflow: auto;

  &__head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  &__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-primary);
  }

  &__summary,
  &__empty {
    color: var(--text-secondary);
    font-size: 12px;
  }

  &__empty {
    padding: 48px 0;
    text-align: center;
    font-size: 14px;
  }

  &__scroll {
    overflow-x: auto;
  }

  // Same editorial table as the To Do grid (ws-theme.scss): quiet uppercase header,
  // hairline rows, cool-grey hover across the whole row. Fixed layout so the dragged
  // <col> widths hold; the table grows past 100% (and scrolls) when the columns do.
  &__table {
    width: 100%;
    table-layout: fixed;
    border-collapse: collapse;
    font-size: 14px;
    color: var(--text-primary);

    th {
      // The shared resize handle sits absolutely on the header cell's right edge.
      position: relative;
      padding: 10px 16px;
      text-align: left;
      color: #8f8f8a;
      border-bottom: 1px solid var(--ws-line-strong);
      white-space: nowrap;
    }

    td {
      padding: 13px 16px;
      border-bottom: 1px solid var(--ws-line);
      vertical-align: middle;
      overflow: hidden;
      overflow-wrap: anywhere;
    }
  }

  // A slightly firmer line where the next Function Unit begins.
  &__row--first:not(:first-child) td {
    border-top: 1px solid var(--ws-line-strong);
  }

  // The merged Function Unit cell belongs to several rows, so it does not follow one row's hover.
  &__row:hover td:not(.batch-groups__fu) {
    background: #f7f8fa;
  }

  &__fu-name {
    font-weight: 600;
  }

  &__fu-count {
    margin-top: 2px;
    font-size: 12px;
    color: var(--text-secondary);
  }

  &__muted {
    color: var(--text-secondary);
  }

  &__count {
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__open {
    text-align: right;
    white-space: nowrap;
  }
}
</style>
