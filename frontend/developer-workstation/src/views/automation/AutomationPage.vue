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

  Workspace: the bridge scopes that session to the selected dev team's own AP project,
  so this page always shows exactly one workspace's flows. Global-view users ("All
  teams" in the header) have no single project to talk to and pick one here instead.
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
      <div class="automation-page__workspace">
        <span class="automation-page__workspace-label">{{ t('automation.workspace') }}:</span>
        <el-select
          v-if="pickable"
          v-model="pickedWorkspaceId"
          class="automation-page__workspace-select"
          size="small"
          @change="onWorkspaceChange"
        >
          <el-option
            :label="t('automation.workspacePublic')"
            :value="PUBLIC_WORKSPACE"
          />
          <el-option
            v-for="group in teams"
            :key="group.id"
            :label="group.name"
            :value="group.id"
          />
        </el-select>
        <span
          v-else
          class="automation-page__workspace-name"
        >{{ workspaceName }}</span>
        <el-tag
          v-if="!canWrite"
          type="info"
          size="small"
          disable-transitions
        >
          {{ t('automation.workspaceReadOnly') }}
        </el-tag>
      </div>

      <!-- Read-only has two different causes and they need different wording: Public is
           "shared/legacy, admin-only", a team workspace is "you have no capability role".
           Explaining the latter with the Public text sends people to switch workspace for
           nothing. -->
      <el-alert
        v-if="!canWrite"
        class="automation-page__moved"
        type="info"
        :closable="false"
        show-icon
        :title="isPublicWorkspace ? t('automation.workspaceReadOnlyHint') : t('automation.workspaceMemberReadOnlyHint')"
      />
      <el-alert
        class="automation-page__moved"
        type="info"
        :closable="false"
        show-icon
        :title="t('automation.runsMovedHint')"
      />
      <FlowsPanel
        :key="session.projectId"
        :session="session"
        :can-write="canWrite"
        @session-expired="loadSession"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchServiceTaskSession, type ServiceTaskSession } from '@/api/automation'
import { functionUnitApi, type DevGroupOption } from '@/api/functionUnit'
import { PUBLIC_GROUP_ID } from '@/utils/devGroupContext'
import {
  getAutomationWorkspaceId,
  isAutomationWorkspacePickable,
  setAutomationWorkspaceId,
} from '@/utils/automationWorkspace'
import FlowsPanel from './components/FlowsPanel.vue'

const { t } = useI18n()

// The built-in Public group is a real group id, so the select shows its label rather than the
// "nothing selected" placeholder; the backend maps it to the shared/legacy workspace.
const PUBLIC_WORKSPACE = PUBLIC_GROUP_ID

const session = ref<ServiceTaskSession | null>(null)
const loadingSession = ref(false)
const sessionError = ref('')
const teams = ref<DevGroupOption[]>([])
const pickable = ref(false)
const pickedWorkspaceId = ref<string>(getAutomationWorkspaceId() || PUBLIC_WORKSPACE)

const workspaceName = computed(() => session.value?.workspace?.name || t('automation.workspacePublic'))
const isPublicWorkspace = computed(() => session.value?.workspace?.isPublic !== false)
// Absent workspace metadata means an older bridge: stay writable rather than locking the UI.
const canWrite = computed(() => session.value?.workspace?.canWrite !== false)

async function loadWorkspaceOptions() {
  pickable.value = isAutomationWorkspacePickable()
  if (!pickable.value) {
    return
  }
  try {
    const res = await functionUnitApi.getMyDevGroups()
    // Inactive teams have no workspace to enter — the backend rejects them with 403.
    teams.value = (res?.data?.groups ?? []).filter((g) => !g.status || g.status === 'ACTIVE')
  } catch (error) {
    teams.value = []
    console.error('[AutomationPage] dev group load failed', error)
  }
}

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
    } else if (status === 403) {
      // Not a member of the requested workspace. Say so plainly instead of quietly
      // showing another workspace's flows.
      sessionError.value = t('automation.sessionWorkspaceForbidden')
    } else {
      sessionError.value = t('automation.sessionLoadFailed')
    }
    console.error('[AutomationPage] session load failed', error)
  } finally {
    loadingSession.value = false
  }
}

function onWorkspaceChange(groupId: string) {
  setAutomationWorkspaceId(groupId)
  void loadSession()
}

onMounted(async () => {
  await loadWorkspaceOptions()
  await loadSession()
})
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

  .automation-page__workspace {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
  }

  .automation-page__workspace-label {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  .automation-page__workspace-name {
    font-weight: 600;
    font-size: 13px;
  }

  .automation-page__workspace-select {
    width: 220px;
  }

  .automation-page__moved {
    margin-bottom: 14px;
  }
}
</style>
