<template>
  <el-drawer
    :model-value="modelValue"
    :title="t('sla.detailTitle', { name: row?.functionUnitName || row?.functionUnitCode || '' })"
    size="65%"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div v-loading="loading">
      <div class="section-header">
        <h4>{{ t('sla.jobsTitle') }}</h4>
        <el-button
          size="small"
          @click="emit('refresh')"
        >
          {{ t('common.refresh') }}
        </el-button>
      </div>
      <el-table
        :data="jobs"
        size="small"
        highlight-current-row
        :empty-text="t('sla.noJobs')"
        @row-click="(job: SlaRecalcJob) => emit('select-job', job.id)"
      >
        <el-table-column
          :label="t('sla.submittedAt')"
          min-width="160"
        >
          <template #default="{ row: job }">
            {{ formatDateTime(job.submittedAt) }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('sla.colLatestJob')"
          width="120"
        >
          <template #default="{ row: job }">
            <el-tag
              size="small"
              :type="jobStatusTagType(job.status)"
            >
              {{ t(`sla.job.${job.status}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="leadTimeDays"
          :label="t('sla.colLeadTimeDays')"
          width="110"
        />
        <el-table-column
          :label="t('sla.counts')"
          min-width="260"
        >
          <template #default="{ row: job }">
            {{ t('sla.countsValue', {
              total: job.totalCount, updated: job.updatedCount, unchanged: job.unchangedCount,
              skipped: job.skippedCount, failed: job.failedCount,
            }) }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('sla.finishedAt')"
          min-width="160"
        >
          <template #default="{ row: job }">
            {{ job.finishedAt ? formatDateTime(job.finishedAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="errorMessage"
          :label="t('sla.error')"
          min-width="180"
          show-overflow-tooltip
        />
      </el-table>

      <template v-if="selectedJobId">
        <h4 class="section-title">
          {{ t('sla.itemsTitle') }}
        </h4>
        <el-table
          v-loading="itemsLoading"
          :data="items"
          size="small"
          :empty-text="t('sla.noItems')"
        >
          <el-table-column
            :label="t('sla.outcome')"
            width="110"
          >
            <template #default="{ row: item }">
              <el-tag
                size="small"
                :type="item.outcome === 'UPDATED' ? 'success' : item.outcome === 'FAILED' ? 'danger' : 'warning'"
              >
                {{ t(`sla.item.${item.outcome}`) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="processInstanceId"
            :label="t('sla.processInstance')"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column
            prop="oldDueDate"
            :label="t('sla.oldDueDate')"
            width="120"
          />
          <el-table-column
            prop="newDueDate"
            :label="t('sla.newDueDate')"
            width="120"
          />
          <el-table-column
            prop="reason"
            :label="t('sla.reason')"
            min-width="220"
            show-overflow-tooltip
          />
        </el-table>
      </template>

      <h4 class="section-title">
        {{ t('sla.historyTitle') }}
      </h4>
      <el-table
        :data="history"
        size="small"
        :empty-text="t('sla.noHistory')"
      >
        <el-table-column
          :label="t('sla.changedAt')"
          min-width="170"
        >
          <template #default="{ row: h }">
            {{ formatDateTime(h.changedAt) }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('sla.change')"
          width="140"
        >
          <template #default="{ row: h }">
            {{ h.oldLeadTimeDays ?? '-' }} → {{ h.newLeadTimeDays }}
          </template>
        </el-table-column>
        <el-table-column
          prop="newVersion"
          :label="t('sla.colVersion')"
          width="80"
        />
        <el-table-column
          prop="changedBy"
          :label="t('sla.colUpdatedBy')"
          min-width="120"
          show-overflow-tooltip
        />
        <el-table-column
          prop="changeReason"
          :label="t('sla.changeReason')"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          :label="t('sla.dispatch')"
          width="150"
        >
          <template #default="{ row: h }">
            <el-tooltip
              :disabled="h.dispatchStatus !== 'DISPATCH_FAILED'"
              :content="t('sla.dispatchFailedHint')"
            >
              <el-tag
                size="small"
                :type="dispatchTagType(h.dispatchStatus)"
              >
                {{ t(`sla.dispatchStatus.${h.dispatchStatus}`) }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { SlaPolicyHistory, SlaPolicyRow, SlaRecalcJob, SlaRecalcJobItem } from '@/api/slaPolicy'
import { dispatchTagType, jobStatusTagType } from './slaStatus'
import { formatDateTime } from '@/utils/format'

defineProps<{
  modelValue: boolean
  loading: boolean
  row: SlaPolicyRow | null
  history: SlaPolicyHistory[]
  jobs: SlaRecalcJob[]
  selectedJobId: string | null
  items: SlaRecalcJobItem[]
  itemsLoading: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  refresh: []
  'select-job': [jobId: string]
}>()

const { t } = useI18n()
</script>

<style scoped>
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-header h4,
.section-title {
  margin: 16px 0 8px;
}
</style>
