<template>
  <div v-loading="loading" class="data-view-dashboard-panel">
    <div class="dashboard-help"><DesignerHelpLink path="/bi-data-view-assignment" :ariaLabel="t('mainTableView.dashboardScopeHint')" /></div>
    <el-alert :title="t('mainTableView.dashboardScopeHint')" type="info" :closable="false" show-icon />
    <el-tabs v-if="dashboards.length > 1" v-model="activeDashboardId" class="dashboard-tabs">
      <el-tab-pane v-for="dashboard in dashboards" :key="dashboard.dashboardId"
        :label="dashboard.dashboardTitle" :name="dashboard.dashboardId" />
    </el-tabs>
    <h3 v-else-if="activeDashboard" class="dashboard-title">{{ activeDashboard.dashboardTitle }}</h3>
    <div v-if="failed" class="dashboard-error" role="alert">
      <p>{{ t('mainTableView.dashboardLoadFailed') }}</p>
      <el-button @click="renderActiveDashboard">{{ t('mainTableView.retryDashboard') }}</el-button>
    </div>
    <div v-show="!failed" ref="mountPoint" class="superset-container" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import DesignerHelpLink from '@/components/DesignerHelpLink.vue'
import { biDashboardApi, type DataViewDashboardResponse } from '@/api/biDashboard'

const props = defineProps<{ viewId: number; dashboards: DataViewDashboardResponse[] }>()
const { t } = useI18n()
const activeDashboardId = ref('')
const activeDashboard = computed(() => props.dashboards.find(d => d.dashboardId === activeDashboardId.value))
const mountPoint = ref<HTMLElement>()
const loading = ref(false)
const failed = ref(false)
let instance: { unmount?: () => void } | undefined
let generation = 0

function cleanup(): void {
  generation++
  instance?.unmount?.()
  instance = undefined
}

async function renderActiveDashboard(): Promise<void> {
  cleanup()
  const current = generation
  const dashboard = activeDashboard.value
  const viewId = props.viewId
  failed.value = false
  if (!dashboard) return
  loading.value = true
  await nextTick()
  try {
    await embed(dashboard, viewId, current)
  } catch {
    if (current === generation) failed.value = true
  } finally {
    if (current === generation) loading.value = false
  }
}

async function embed(dashboard: DataViewDashboardResponse, viewId: number, current: number): Promise<void> {
  const { embedDashboard } = await import('@superset-ui/embedded-sdk')
  const initial = await biDashboardApi.getGuestToken({ dashboardId: dashboard.dashboardId, dataViewId: viewId })
  if (current !== generation || !mountPoint.value) return
  if (!initial.supersetDomain) throw new Error('Superset domain is missing')
  let first = true
  const created = await embedDashboard({
    id: dashboard.embedId,
    supersetDomain: initial.supersetDomain,
    mountPoint: mountPoint.value,
    fetchGuestToken: async () => {
      if (current !== generation) throw new Error('Dashboard context changed')
      if (first) { first = false; return initial.token }
      try {
        return (await biDashboardApi.getGuestToken({ dashboardId: dashboard.dashboardId, dataViewId: viewId })).token
      } catch (error) {
        if (current === generation) { failed.value = true; cleanup() }
        throw error
      }
    },
    dashboardUiConfig: { hideTitle: true, hideChartControls: false, hideTab: false },
  })
  if (current !== generation) { created.unmount(); return }
  instance = created
}

watch(() => [props.viewId, props.dashboards.map(d => d.dashboardId).join('|')], () => {
  cleanup()
  activeDashboardId.value = props.dashboards[0]?.dashboardId ?? ''
}, { immediate: true })
watch(() => [props.viewId, activeDashboardId.value, props.dashboards.map(d => d.dashboardId).join('|')],
  () => { void renderActiveDashboard() }, { immediate: true, flush: 'post' })
onBeforeUnmount(cleanup)
</script>

<style scoped lang="scss">
.data-view-dashboard-panel { flex: 1 1 auto; min-height: 0; display: flex; flex-direction: column; gap: 12px; }
.dashboard-title { margin: 0; font-size: 16px; }
.dashboard-tabs { flex: 0 0 auto; }
.superset-container { flex: 1 1 auto; width: 100%; min-height: 520px; height: 100%; }
.superset-container :deep(iframe) { width: 100%; height: 100%; min-height: 520px; border: none; }
.dashboard-error { padding: 40px; text-align: center; color: var(--el-text-color-secondary); }
</style>
