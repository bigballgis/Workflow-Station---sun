<template>
  <div class="section history-section">
    <div
      v-if="showHeader"
      class="section-header"
    >
      <el-icon><Clock /></el-icon>
      <span>{{ $t('task.flowHistory') }}</span>
    </div>
    <div class="section-content">
      <el-alert
        v-if="historyError"
        :title="historyError"
        type="warning"
        show-icon
        :closable="false"
      />
      <ProcessHistory
        v-else-if="historyRecords.length > 0"
        :records="filteredRecords"
        :show-header="false"
        :show-refresh="false"
        collapsible
        :default-visible-count="1"
      />
      <el-empty
        v-else
        :description="$t('task.noFlowHistory')"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Clock } from '@element-plus/icons-vue'
import ProcessHistory from '@/components/ProcessHistory.vue'
import type { HistoryRecord } from '@/types/historyRecord'

const props = defineProps<{
  historyRecords: HistoryRecord[]
  historyError: string | null
  showHeader?: boolean
}>()

const showHeader = computed(() => props.showHeader !== false)

const filteredRecords = computed(() =>
  props.historyRecords.filter(r => !r.activityType?.includes('Gateway'))
)
</script>

<style lang="scss" scoped>
.section {
  background: white;
  border-radius: 8px;
  border: 1px solid var(--border-color, #e4e7ed);

  .section-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 16px 20px;
    border-bottom: 1px solid var(--border-color, #e4e7ed);
    font-weight: 500;
    font-size: 16px;
    color: var(--text-primary);
  }

  .section-content {
    padding: 20px;
  }
}
</style>
