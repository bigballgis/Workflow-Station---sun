<!--
  Automation run history — the AP flow-runs API through the per-user session.
  Optional per-flow filter; row click opens a drawer with the populated run
  (per-step outputs rendered as raw JSON — the deliberate "从简" detail view).
-->
<template>
  <div class="runs-panel">
    <div class="runs-panel__toolbar">
      <el-select
        v-model="flowFilter"
        class="runs-panel__filter"
        :placeholder="t('automation.runsAllFlows')"
        clearable
        :loading="loadingFlows"
        @visible-change="loadFlowOptions"
        @change="reload"
      >
        <el-option
          v-for="flow in flowOptions"
          :key="flow.id"
          :label="flow.name"
          :value="flow.id"
        />
      </el-select>
      <el-button @click="reload">
        <el-icon><Refresh /></el-icon>{{ t('common.refresh') }}
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="runs"
      stripe
      style="width: 100%"
      @row-click="openRunDetail"
    >
      <el-table-column
        :label="t('automation.runsFlow')"
        min-width="180"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.flowVersion?.displayName || row.flowId }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('automation.runsStatus')"
        width="150"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="statusTag(row.status)"
            size="small"
            disable-transitions
          >
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('automation.runsStarted')"
        width="160"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.startTime) }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('automation.runsDuration')"
        width="120"
      >
        <template #default="{ row }">
          {{ formatDuration(row.startTime, row.finishTime) }}
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="!loading && runs.length === 0"
      :description="t('automation.runsEmpty')"
    />

    <div
      v-if="nextCursor"
      class="runs-panel__more"
    >
      <el-button
        :loading="loadingMore"
        @click="loadMore"
      >
        {{ t('automation.loadMore') }}
      </el-button>
    </div>

    <el-drawer
      v-model="detailVisible"
      :title="t('automation.runsDetailTitle')"
      size="46%"
    >
      <div
        v-loading="loadingDetail"
        class="runs-panel__detail"
      >
        <pre v-if="detailJson">{{ detailJson }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getAutomationFlowRun,
  listAutomationFlowRuns,
  listAutomationFlows,
  type ApFlowRun,
  type ApFlowRunStatus,
  type ServiceTaskSession,
} from '@/api/automation'
import { formatDateTime, formatDuration } from '../automationUi'

const props = defineProps<{ session: ServiceTaskSession }>()

const { t } = useI18n()

const runs = ref<ApFlowRun[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const nextCursor = ref('')
const flowFilter = ref('')

function statusTag(status: ApFlowRunStatus): 'success' | 'danger' | 'warning' | 'info' {
  switch (status) {
    case 'SUCCEEDED':
      return 'success'
    case 'RUNNING':
    case 'QUEUED':
    case 'PAUSED':
      return 'info'
    case 'CANCELED':
      return 'warning'
    default:
      return 'danger'
  }
}

async function reload() {
  loading.value = true
  try {
    const page = await listAutomationFlowRuns({
      token: props.session.token,
      projectId: props.session.projectId,
      flowId: flowFilter.value || undefined,
      limit: 20,
    })
    runs.value = page.data || []
    nextCursor.value = page.next || ''
  } catch (error) {
    ElMessage.error(t('automation.runsLoadFailed'))
    console.error('[RunsPanel] load failed', error)
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!nextCursor.value) return
  loadingMore.value = true
  try {
    const page = await listAutomationFlowRuns({
      token: props.session.token,
      projectId: props.session.projectId,
      flowId: flowFilter.value || undefined,
      cursor: nextCursor.value,
      limit: 20,
    })
    runs.value = [...runs.value, ...(page.data || [])]
    nextCursor.value = page.next || ''
  } catch (error) {
    ElMessage.error(t('automation.runsLoadFailed'))
    console.error('[RunsPanel] load more failed', error)
  } finally {
    loadingMore.value = false
  }
}

/* ---- flow filter options (lazy) ---- */
interface FlowOption { id: string; name: string }
const flowOptions = ref<FlowOption[]>([])
const loadingFlows = ref(false)
let flowsLoaded = false

async function loadFlowOptions(visible: boolean) {
  if (!visible || flowsLoaded || loadingFlows.value) return
  loadingFlows.value = true
  try {
    const page = await listAutomationFlows({
      token: props.session.token,
      projectId: props.session.projectId,
      limit: 100,
    })
    flowOptions.value = (page.data || []).map((flow) => ({
      id: flow.id,
      name: flow.version?.displayName || flow.id,
    }))
    flowsLoaded = true
  } catch (error) {
    // 过滤器加载失败不阻塞列表本身
    console.error('[RunsPanel] flow options load failed', error)
  } finally {
    loadingFlows.value = false
  }
}

/* ---- run detail (raw JSON drawer) ---- */
const detailVisible = ref(false)
const loadingDetail = ref(false)
const detailJson = ref('')

async function openRunDetail(run: ApFlowRun) {
  detailVisible.value = true
  loadingDetail.value = true
  detailJson.value = ''
  try {
    const populated = await getAutomationFlowRun(run.id, props.session.token)
    detailJson.value = JSON.stringify(populated, null, 2)
  } catch (error) {
    ElMessage.error(t('automation.runsDetailFailed'))
    console.error('[RunsPanel] detail load failed', error)
  } finally {
    loadingDetail.value = false
  }
}

onMounted(reload)
</script>

<style scoped lang="scss">
.runs-panel {
  .runs-panel__toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
  }

  .runs-panel__filter {
    width: 280px;
  }

  .runs-panel__more {
    display: flex;
    justify-content: center;
    margin-top: 12px;
  }

  .runs-panel__detail {
    min-height: 200px;

    pre {
      margin: 0;
      font-size: 12px;
      line-height: 1.6;
      white-space: pre-wrap;
      word-break: break-word;
    }
  }

  :deep(.el-table__row) {
    cursor: pointer;
  }
}
</style>
