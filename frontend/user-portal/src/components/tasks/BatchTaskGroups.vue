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

    <table
      v-else
      class="batch-groups__table"
    >
      <thead>
        <tr>
          <th>{{ t('task.functionUnit') }}</th>
          <th>{{ t('task.currentStep') }}</th>
          <th>{{ t('task.batchGroupVersion') }}</th>
          <th>{{ t('task.batchGroupCount') }}</th>
          <th>{{ t('task.batchGroupActions') }}</th>
          <th />
        </tr>
      </thead>
      <tbody>
        <template
          v-for="fu in byFunctionUnit"
          :key="fu.key"
        >
          <!-- One row per node of one release; the Function Unit is named on its first row only. -->
          <tr
            v-for="(g, index) in fu.groups"
            :key="g.groupKey + '/' + g.taskDefinitionKey"
            class="batch-groups__row"
            :class="{ 'batch-groups__row--first': index === 0 }"
            data-test="batch-group-row"
          >
            <td class="batch-groups__fu">
              <template v-if="index === 0">
                <div class="batch-groups__fu-name">
                  {{ fu.name }}
                </div>
                <div class="batch-groups__fu-count">
                  {{ t('task.batchPendingCount', { count: fu.count }) }}
                </div>
              </template>
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
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight } from '@element-plus/icons-vue'
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

  // Same editorial table as the To Do grid (ws-theme.scss): quiet uppercase header,
  // hairline rows, cool-grey hover across the whole row.
  &__table {
    width: 100%;
    border-collapse: collapse;
    font-size: 14px;
    color: var(--text-primary);

    th {
      padding: 10px 16px;
      text-align: left;
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: #8f8f8a;
      border-bottom: 1px solid var(--ws-line-strong);
      white-space: nowrap;
    }

    td {
      padding: 13px 16px;
      border-bottom: 1px solid var(--ws-line);
      vertical-align: middle;
    }
  }

  // A slightly firmer line where the next Function Unit begins.
  &__row--first:not(:first-child) td {
    border-top: 1px solid var(--ws-line-strong);
  }

  &__row:hover td {
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

  // Shrink to the link so the step and action columns get the room.
  &__open {
    width: 1%;
    text-align: right;
    white-space: nowrap;
  }
}
</style>
