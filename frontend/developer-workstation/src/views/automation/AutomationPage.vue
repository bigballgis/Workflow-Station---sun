<!--
  Automation — DW first-level page (FR-B2).

  Flows are designed here (standalone, no longer inside a Function Unit tab); BPMN
  service tasks reference them by business key only. Two tabs:
    - Flows: list / create / rename / publish / enable / delete, click-through to
      the embedded builder (/automation/:flowId).
    - Runs: execution history from the AP runs API.

  Cross-environment flow migration and the piece catalog are NOT here: they are
  production operations, and the Developer Workstation is dev-only (it is absent
  from deploy/k8s/kustomization.yaml). Both live in the Admin Center instead
  (/automation-flows, /automation-pieces).

  Session chain: admin-center bridge mints {token, projectId}; all AP calls go
  through the Kong /api/ap prefix with that Bearer token.
-->
<template>
  <div
    v-loading="loadingSession"
    class="page-container automation-page"
  >
    <el-result
      v-if="sessionError"
      icon="warning"
      :title="t('automation.sessionErrorTitle')"
      :sub-title="sessionError"
    >
      <template #extra>
        <el-button
          type="primary"
          @click="loadSession"
        >
          {{ t('common.retry') }}
        </el-button>
      </template>
    </el-result>

    <el-tabs
      v-else-if="session"
      v-model="activeTab"
      class="automation-page__tabs"
    >
      <el-tab-pane
        :label="t('automation.tabFlows')"
        name="flows"
      >
        <FlowsPanel
          v-if="activeTab === 'flows'"
          :session="session"
          @session-expired="loadSession"
        />
      </el-tab-pane>
      <el-tab-pane
        :label="t('automation.tabRuns')"
        name="runs"
      >
        <RunsPanel
          v-if="activeTab === 'runs'"
          :session="session"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchServiceTaskSession, type ServiceTaskSession } from '@/api/automation'
import FlowsPanel from './components/FlowsPanel.vue'
import RunsPanel from './components/RunsPanel.vue'

const { t } = useI18n()

const session = ref<ServiceTaskSession | null>(null)
const loadingSession = ref(false)
const sessionError = ref('')
const activeTab = ref('flows')

async function loadSession() {
  loadingSession.value = true
  sessionError.value = ''
  session.value = null
  try {
    session.value = await fetchServiceTaskSession()
  } catch (error) {
    const status = (error as { response?: { status?: number } })?.response?.status
    if (status === 404) {
      // Bridge disabled in this environment (prod-like) — guide instead of a dead page
      sessionError.value = t('automation.sessionBridgeDisabled')
    } else if (status === 401) {
      sessionError.value = t('automation.sessionUnauthorized')
    } else {
      sessionError.value = t('automation.sessionLoadFailed')
    }
    console.error('[AutomationPage] session load failed', error)
  } finally {
    loadingSession.value = false
  }
}

onMounted(loadSession)
</script>

<style scoped lang="scss">
.automation-page {
  // .page-container 撑满内容区；tabs 卡片吃掉剩余高度并自行滚动
  .automation-page__tabs {
    flex: 1;
    min-height: 320px;
    background: #fff;
    border-radius: 4px;
    padding: 8px 20px 20px;
    overflow-y: auto;
  }
}
</style>
