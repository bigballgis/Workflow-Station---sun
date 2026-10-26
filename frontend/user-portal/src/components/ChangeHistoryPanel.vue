<template>
  <el-collapse
    v-model="sectionExpandedNames"
    class="ch-root-collapse"
    :class="{ 'ch-root-collapse--headerless': !showHeader }"
  >
    <el-collapse-item name="history">
      <template #title>
        <div class="ch-collapse-title">
          <el-icon class="ch-title-icon">
            <Document />
          </el-icon>
          <span class="ch-collapse-title-text">{{ t('changeHistory.title') }}</span>
        </div>
      </template>

      <div class="change-history-panel-inner">
        <div class="ch-history-tools">
          <el-input
            v-model="keyword"
            class="ch-history-search"
            clearable
            :prefix-icon="Search"
            :placeholder="t('changeHistory.searchPlaceholder')"
            :aria-label="t('changeHistory.searchLabel')"
          />
          <el-select
            v-model="timeOrder"
            class="ch-history-order"
            :aria-label="t('changeHistory.timeOrder')"
          >
            <el-option
              value="asc"
              :label="t('changeHistory.oldestFirst')"
            />
            <el-option
              value="desc"
              :label="t('changeHistory.newestFirst')"
            />
          </el-select>
          <span
            class="ch-history-results"
            role="status"
            aria-live="polite"
          >
            {{ t('changeHistory.searchSummary', { count: matchedCount, total: totalCount }) }}
          </span>
          <PortalHelpLink v-bind="{ path: '/up-tasks-to-claim#change-history', ariaLabel: t('changeHistory.guideLinkAria') }" />
        </div>
        <div
          v-if="loading"
          class="loading-state"
        >
          <el-skeleton
            animated
            :count="3"
          >
            <template #template>
              <el-skeleton-item
                variant="text"
                style="width: 80%; margin-bottom: 12px;"
              />
            </template>
          </el-skeleton>
        </div>
        <div
          v-else-if="error"
          class="error-state"
        >
          <el-alert
            :title="error"
            type="warning"
            show-icon
            :closable="false"
          />
        </div>
        <div
          v-else-if="records.length === 0"
          class="empty-state"
        >
          <el-empty
            :description="t('changeHistory.noRecords')"
            :image-size="80"
          />
        </div>
        <el-empty
          v-else-if="displayBatches.length === 0"
          :description="t('changeHistory.noMatches')"
          :image-size="80"
        />
        <div
          v-else
          class="ch-batches"
        >
          <section
            v-for="batch in displayBatches"
            :key="batch.key"
            class="ch-batch"
          >
            <button
              type="button"
              class="ch-batch-header"
              :aria-expanded="isBatchTableOpen(batch.key)"
              @click="toggleBatchTable(batch.key)"
            >
              <el-icon
                class="ch-batch-chevron"
                :class="{ 'is-open': isBatchTableOpen(batch.key) }"
              >
                <ArrowRight />
              </el-icon>
              <strong :title="batch.stageTooltip ?? undefined">{{ batch.displayStage || (batch.eventType?.startsWith('RECORD_NOTE_') ? t('changeHistory.recordNote') : batch.eventType ? getChangeTypeLabel(batch.eventType) : '—') }}</strong>
              <span class="ch-batch-time">{{ batchTimeLabel(batch) }}</span>
              <span>{{ batch.displayOperator }}</span>
              <el-tag
                v-if="batch.eventType"
                size="small"
                :type="getChangeTypeTag(batch.eventType)"
              >
                {{ getChangeTypeLabel(batch.eventType) }}
              </el-tag>
              <span class="ch-batch-count">{{ t('changeHistory.batchChangeCount', { count: batch.rows.length }) }}</span>
              <el-icon
                v-if="batch.concurrent"
                class="concurrent-icon"
                :title="t('changeHistory.concurrentWarning')"
              >
                <Warning />
              </el-icon>
            </button>
            <ChangeHistoryDetails
              v-if="isBatchTableOpen(batch.key)"
              :rows="batch.rows"
              :formatting="formatting"
            />
          </section>
        </div>
      </div>
    </el-collapse-item>
  </el-collapse>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight, Document, Search, Warning } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import PortalHelpLink from './PortalHelpLink.vue'
import ChangeHistoryDetails from './ChangeHistoryDetails.vue'
import { RECORD_NOTE_CHANGED_EVENT } from './formRendererHelpers/recordNoteFields'
import { useChangeHistoryFormatting } from '@/composables/changeHistoryPanel/useChangeHistoryFormatting'
import { useChangeHistoryBatches, type ChangeBatch } from '@/composables/changeHistoryPanel/useChangeHistoryBatches'
import { useChangeHistoryPresentation } from '@/composables/changeHistoryPanel/useChangeHistoryPresentation'
import { useChangeHistoryExpansion } from '@/composables/changeHistoryPanel/useChangeHistoryExpansion'
import { useChangeHistoryLoader } from '@/composables/changeHistoryPanel/useChangeHistoryLoader'
import { getChangeHistorySensitiveMasks } from '@/api/processForm'
import type { SensitiveMaskConfig } from '@/utils/sensitiveMask'
import { emptySensitiveMaskLookup, type SensitiveMaskLookup } from '@/utils/sensitiveMaskLookup'

const { t } = useI18n()

interface Props {
  rowIdentifier?: string
  taskId?: string
  processInstanceId: string
  snapshotTime?: string
  taskInstanceId?: string
  showHeader?: boolean
  /** Field-name → mask config for display-only masking of old/new values. */
  sensitiveMaskLookup?: SensitiveMaskLookup | Record<string, SensitiveMaskConfig> | null
}

const props = withDefaults(defineProps<Props>(), {
  showHeader: true,
})

/** Server-side masks (same auth as CH); covers viewers without process-detail access. */
const apiMaskLookup = ref<SensitiveMaskLookup>(emptySensitiveMaskLookup())

const resolvedMaskLookup = computed((): SensitiveMaskLookup => {
  const merged = emptySensitiveMaskLookup()
  for (const [k, v] of apiMaskLookup.value) merged.set(k, v)
  const raw = props.sensitiveMaskLookup
  if (raw) {
    const entries = raw instanceof Map ? raw.entries() : Object.entries(raw)
    for (const [k, v] of entries) {
      if (!merged.has(k)) merged.set(k, v)
    }
  }
  return merged
})

// 展示格式化、字段标签与变更类型映射（纯函数）
const formatting = useChangeHistoryFormatting(t, dayjs, () => resolvedMaskLookup.value)
const {
  formatTimestamp,
  getChangeTypeLabel,
  getChangeTypeTag,
} = formatting

// 拉取历史并按快照时间/任务过滤
const { loading, error, records, loadHistory } = useChangeHistoryLoader(props, t, dayjs)

async function loadApiMasks() {
  if (!props.processInstanceId) {
    apiMaskLookup.value = emptySensitiveMaskLookup()
    return
  }
  try {
    const res = await getChangeHistorySensitiveMasks(props.processInstanceId) as {
      data?: Record<string, SensitiveMaskConfig>
    }
    const raw = res?.data ?? {}
    const next = emptySensitiveMaskLookup()
    for (const [k, v] of Object.entries(raw)) {
      if (v && typeof v === 'object') next.set(k, v)
    }
    apiMaskLookup.value = next
  } catch {
    // FALLBACK(ux): CH rows still render; mask enrichment skipped when API unavailable.
    apiMaskLookup.value = emptySensitiveMaskLookup()
  }
}

watch(() => props.processInstanceId, () => { void loadApiMasks() }, { immediate: true })

// Record Note 的增删改不经过表单保存，故由 RecordNoteField 广播后就地重取，
// 否则新写的备注要等整页刷新才出现在变更历史里。
function onRecordNoteChanged() {
  void loadHistory()
}
onMounted(() => window.addEventListener(RECORD_NOTE_CHANGED_EVENT, onRecordNoteChanged))
onBeforeUnmount(() => window.removeEventListener(RECORD_NOTE_CHANGED_EVENT, onRecordNoteChanged))

const { groupedBatches } = useChangeHistoryBatches(records, formatting, dayjs)
const { keyword, timeOrder, displayBatches, matchedCount, totalCount } = useChangeHistoryPresentation(groupedBatches, formatting, t)
const { sectionExpandedNames, batchTableOpen, toggleBatchTable, isBatchTableOpen } = useChangeHistoryExpansion(groupedBatches)
watch(() => props.processInstanceId, () => { batchTableOpen.value = {} })

function batchTimeLabel(batch: ChangeBatch): string {
  const start = formatTimestamp(batch.timestamp)
  const end = formatTimestamp(batch.endTimestamp)
  if (start === end) return start
  const endLabel = dayjs(batch.timestamp).isSame(dayjs(batch.endTimestamp), 'day')
    ? dayjs(batch.endTimestamp).format('HH:mm:ss') : end
  return `${start} – ${endLabel}`
}
</script>

<style scoped lang="scss">
.ch-history-tools { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 12px; }
.ch-history-search { flex: 1 1 280px; max-width: 420px; }
.ch-history-order { width: 170px; }
.ch-history-results { margin-left: auto; color: var(--text-secondary, #666); font-size: 13px; }
@media (max-width: 640px) { .ch-history-search { max-width: none; flex-basis: 100%; } }
.ch-batches { display: grid; gap: 14px; }
.ch-batch { min-width: 0; border: 1px solid var(--border-color, #e4e7ed); border-radius: 6px; overflow: hidden; }
.ch-batch-header {
  display: flex; align-items: center; flex-wrap: wrap; gap: 10px 20px;
  width: 100%; padding: 14px 16px; border: 0; text-align: left;
  color: var(--text-primary, #333); background: #f7f8fa; cursor: pointer; font: inherit; font-size: 13px;
  &:hover { background: #eef0f3; }
  &:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
}
.ch-batch-chevron { transition: transform 0.15s; &.is-open { transform: rotate(90deg); } }
.ch-batch-time, .ch-batch-count { color: var(--text-secondary, #666); }
.ch-batch-count { margin-left: auto; }
/* 与任务/申请详情页 .section .section-header（流转记录等）视觉一致 */
.ch-root-collapse {
  width: 100%;
  border: none;

  :deep(> .el-collapse-item > .el-collapse-item__header) {
    display: flex;
    align-items: center;
    font-size: 16px;
    font-weight: 500;
    line-height: 1.4;
    height: auto;
    min-height: unset;
    padding: 16px 20px;
    background: #fafafa;
    border: none;
    border-bottom: 1px solid var(--border-color, #e4e7ed);
    color: var(--text-primary, #333333);
  }

  :deep(.el-collapse-item__wrap) {
    border: none;
  }

  :deep(.el-collapse-item__content) {
    padding: 20px;
    padding-bottom: 20px;
  }

  :deep(.el-collapse-item__arrow) {
    margin: 0 8px 0 0;
    color: var(--text-secondary, #666666);
  }
}

.ch-root-collapse--headerless {
  // Embedded tabs already provide the content gutter; avoid a second empty inset.
  :deep(.el-collapse-item__content) { padding: 0; }
  :deep(> .el-collapse-item > .el-collapse-item__header) {
    display: none;
  }
}

.ch-collapse-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.ch-collapse-title-text {
  color: var(--text-primary, #333333);
  font-size: 16px;
  font-weight: 500;
}

.ch-title-icon {
  font-size: 18px;
  color: var(--hsbc-red, #db0011);
}

.change-history-panel-inner {
  width: 100%;
}


.concurrent-icon {
  font-size: 16px;

  color: var(--el-color-warning);

  margin-left: 6px;
  vertical-align: middle;
}

</style>
