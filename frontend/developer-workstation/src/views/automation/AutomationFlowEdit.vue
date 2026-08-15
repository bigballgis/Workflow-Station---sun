<!--
  Automation flow editor (/automation/:flowId) — mounts the vendored Activepieces
  builder (lib-mode ESM + Shadow DOM via ServiceTaskBuilderCanvas, NOT an iframe)
  on one flow, full-height. Same wiring the old FU tab used: bridge session +
  Kong /api/ap prefix + DW-served bundle (/dev/service-task-builder).
-->
<template>
  <div
    v-loading="loading"
    class="page-container automation-flow-edit"
  >
    <div class="automation-flow-edit__header">
      <el-button
        link
        @click="goBack"
      >
        <el-icon><ArrowLeft /></el-icon>
        {{ t('automation.backToList') }}
      </el-button>
      <span
        v-if="flowName"
        class="automation-flow-edit__title"
      >{{ flowName }}</span>
      <code
        v-if="flowKeyLabel"
        class="automation-flow-edit__key"
        :title="t('automation.colKey')"
      >{{ flowKeyLabel }}</code>
    </div>

    <el-result
      v-if="errorMessage"
      icon="warning"
      :title="errorMessage"
    >
      <template #extra>
        <el-button
          type="primary"
          @click="load"
        >
          {{ t('common.retry') }}
        </el-button>
        <el-button @click="goBack">
          {{ t('automation.backToList') }}
        </el-button>
      </template>
    </el-result>

    <ServiceTaskBuilderCanvas
      v-else-if="session && flowId"
      :key="`${flowId}-${sessionEpoch}`"
      class="automation-flow-edit__canvas"
      :flow-id="flowId"
      :project-id="session.projectId"
      :token="session.token"
      :api-url="apiUrl"
      :socket-base-url="socketBaseUrl"
      socket-path="/api/ap/socket.io"
      :bundle-url="bundleUrl"
      :css-url="cssUrl"
      @unauthorized="onUnauthorized"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  fetchServiceTaskSession,
  getAutomationFlow,
  type ServiceTaskSession,
} from '@/api/automation'
import ServiceTaskBuilderCanvas from '@/components/serviceTask/ServiceTaskBuilderCanvas.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const flowId = computed(() => String(route.params.flowId || ''))

const session = ref<ServiceTaskSession | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const flowName = ref('')
const flowKeyLabel = ref('')
/** Bump to remount the canvas after a session re-issue (401 recovery). */
const sessionEpoch = ref(0)

// The builder bundle is served from DW's own origin, so REST/socket.io go to the
// same origin too and Kong routes them on via the /api/ap prefix.
const origin = window.location.origin
const apiUrl = `${origin}/api/ap`
const socketBaseUrl = origin
const bundleUrl = `${import.meta.env.BASE_URL}service-task-builder/ap-builder.mjs`
const cssUrl = `${import.meta.env.BASE_URL}service-task-builder/web.css`

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    session.value = await fetchServiceTaskSession()
    // Header metadata; a 404 here means the flow is gone — show recovery, not a dead builder
    const flow = await getAutomationFlow(flowId.value, session.value.token)
    flowName.value = flow.version?.displayName || flow.id
    flowKeyLabel.value = flow.metadata?.hermesFlowKey || ''
    sessionEpoch.value += 1
  } catch (error) {
    const status = (error as { response?: { status?: number } })?.response?.status
    errorMessage.value =
      status !== undefined && status >= 400 && status < 500
        ? t('automation.flowMissing', { id: flowId.value })
        : t('automation.loadFailed')
    console.error('[AutomationFlowEdit] load failed', error)
  } finally {
    loading.value = false
  }
}

// The host owns the session: on 401 re-issue it instead of letting the builder
// navigate DW away to AP's own sign-in page.
function onUnauthorized() {
  session.value = null
  void load()
}

function goBack() {
  router.push('/automation')
}

onMounted(load)
</script>

<style scoped lang="scss">
.automation-flow-edit {
  // .page-container 已是撑满内容区的纵向 flex；编排器吃掉剩余高度
  gap: 10px;
  min-height: 480px;

  .automation-flow-edit__header {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-shrink: 0;
  }

  .automation-flow-edit__title {
    font-size: 15px;
    font-weight: 600;
    color: var(--ws-text, #1f1f1f);
  }

  .automation-flow-edit__key {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-lighter);
    border-radius: 4px;
    padding: 2px 8px;
  }

  .automation-flow-edit__canvas {
    flex: 1;
    min-height: 0;
    border: 1px solid var(--el-border-color-light);
    border-radius: 4px;
    overflow: hidden;
    background: #fff;
  }
}
</style>
