<!--
  Automation — DW first-level page (FR-B2).

  Flows are designed here (standalone, no longer inside a Function Unit tab); BPMN
  service tasks reference them by business key only: list / create / rename /
  publish / enable / delete, click-through to the embedded builder (/automation/:flowId).

  Run history, cross-environment flow migration and the piece catalog are NOT here:
  they are production operations, and the Developer Workstation is dev-only (it is
  absent from deploy/k8s/kustomization.yaml). All three live in the Admin Center
  instead (/automation-runs, /automation-flows, /automation-pieces).

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

    <div
      v-else-if="session"
      class="automation-page__panel"
    >
      <el-alert
        class="automation-page__moved"
        type="info"
        :closable="false"
        show-icon
        :title="t('automation.runsMovedHint')"
      />
      <FlowsPanel
        :session="session"
        @session-expired="loadSession"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchServiceTaskSession, type ServiceTaskSession } from '@/api/automation'
import FlowsPanel from './components/FlowsPanel.vue'

const { t } = useI18n()

const session = ref<ServiceTaskSession | null>(null)
const loadingSession = ref(false)
const sessionError = ref('')

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
  // .page-container 撑满内容区；卡片吃掉剩余高度并自行滚动
  .automation-page__panel {
    flex: 1;
    min-height: 320px;
    background: #fff;
    border-radius: 4px;
    padding: 16px 20px 20px;
    overflow-y: auto;
  }

  .automation-page__moved {
    margin-bottom: 14px;
  }
}
</style>
