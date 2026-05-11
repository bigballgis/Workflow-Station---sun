<template>
  <div
    v-loading="loading"
    class="dashboard-landing"
  >
    <!-- Empty state -->
    <div
      v-if="!loading && dashboards.length === 0"
      class="empty-state"
    >
      <el-empty description="No dashboards available" />
    </div>

    <!-- SINGLE mode: full-screen single dashboard -->
    <div
      v-else-if="layoutMode === 'SINGLE' && dashboards.length > 0"
      class="single-layout"
    >
      <div
        :id="getContainerId(dashboards[0].dashboardId)"
        class="superset-container"
      />
    </div>

    <!-- MULTI mode: tabs -->
    <div
      v-else-if="layoutMode === 'MULTI'"
      class="multi-layout"
    >
      <el-tabs
        v-model="activeTab"
        type="border-card"
        @tab-change="handleTabChange"
      >
        <el-tab-pane
          v-for="db in dashboards"
          :key="db.dashboardId"
          :label="db.dashboardTitle"
          :name="db.dashboardId"
        >
          <div
            :id="getContainerId(db.dashboardId)"
            class="superset-container"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- WIDGET mode: grid cards -->
    <div
      v-else-if="layoutMode === 'WIDGET'"
      class="widget-layout"
    >
      <div class="widget-grid">
        <div
          v-for="db in dashboards"
          :key="db.dashboardId"
          class="widget-card"
          @click="openFullscreen(db)"
        >
          <div class="widget-card-header">
            <span class="widget-title">{{ db.dashboardTitle }}</span>
          </div>
          <div class="widget-card-body">
            <p
              v-if="db.description"
              class="widget-desc"
            >
              {{ db.description }}
            </p>
            <el-icon
              :size="32"
              color="var(--text-placeholder)"
            >
              <DataAnalysis />
            </el-icon>
            <span class="widget-hint">Click to view full dashboard</span>
          </div>
        </div>
      </div>

      <!-- Fullscreen dialog for WIDGET mode -->
      <el-dialog
        v-model="fullscreenVisible"
        :title="fullscreenDashboard?.dashboardTitle || ''"
        fullscreen
        destroy-on-close
        @opened="onFullscreenOpened"
        @close="onFullscreenClosed"
      >
        <div
          id="superset-fullscreen-container"
          class="superset-container fullscreen-container"
        />
      </el-dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { DataAnalysis } from '@element-plus/icons-vue'
import {
  biDashboardApi,
  type UserDashboardResponse,
  type GuestTokenResponse,
} from '@/api/biDashboard'
import { getStoredUser, USER_ID_KEY, USER_KEY } from '@/api/auth'

// NOTE: Install required package: npm install @superset-ui/embedded-sdk
// import { embedDashboard } from '@superset-ui/embedded-sdk'

const loading = ref(true)
const dashboards = ref<UserDashboardResponse[]>([])
const layoutMode = ref<'SINGLE' | 'MULTI' | 'WIDGET'>('SINGLE')
const activeTab = ref('')
const fullscreenVisible = ref(false)
const fullscreenDashboard = ref<UserDashboardResponse | null>(null)
const supersetDomain = ref('')
const currentUserId = ref('')

// Track embedded dashboard instances for cleanup
const embeddedInstances = new Map<string, { unmount?: () => void }>()

function getContainerId(dashboardId: string): string {
  return `superset-embed-${dashboardId}`
}

function getUserId(): string {
  const userId = localStorage.getItem(USER_ID_KEY)
  if (userId) return userId
  const userStr = localStorage.getItem(USER_KEY)
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      return user.userId || user.id || ''
    } catch {
      return ''
    }
  }
  return ''
}

async function fetchGuestTokenPayload(dashboardId: string): Promise<GuestTokenResponse> {
  try {
    const res = await biDashboardApi.getGuestToken({ dashboardId }, currentUserId.value || undefined)
    // The response interceptor unwraps the data, so res should be GuestTokenResponse directly
    return ((res as any).data || res) as GuestTokenResponse
  } catch (err: any) {
    const backendMsg =
      err?.response?.data?.message ||
      err?.response?.data?.details ||
      err?.message ||
      'Unknown error'
    console.error(`Failed to fetch guest token for dashboard ${dashboardId}:`, err)
    console.error(`Guest token API error details: ${backendMsg}`)
    throw err
  }
}

async function embedSupersetDashboard(
  embedId: string,
  dashboardId: string,
  mountPointId: string
): Promise<void> {
  const mountPoint = document.getElementById(mountPointId)
  if (!mountPoint) return

  try {
    // Dynamic import to handle case where package is not installed
    const { embedDashboard } = await import('@superset-ui/embedded-sdk')
    const initialTokenPayload = await fetchGuestTokenPayload(dashboardId)
    if (!supersetDomain.value && initialTokenPayload.supersetDomain) {
      supersetDomain.value = initialTokenPayload.supersetDomain
    }
    const runtimeSupersetDomain =
      initialTokenPayload.supersetDomain || supersetDomain.value || 'http://localhost:8088'
    let isFirstTokenRequest = true

    const result = await embedDashboard({
      id: embedId,
      supersetDomain: runtimeSupersetDomain,
      mountPoint,
      fetchGuestToken: async () => {
        if (isFirstTokenRequest) {
          isFirstTokenRequest = false
          return initialTokenPayload.token
        }
        const tokenPayload = await fetchGuestTokenPayload(dashboardId)
        if (!supersetDomain.value && tokenPayload.supersetDomain) {
          supersetDomain.value = tokenPayload.supersetDomain
        }
        return tokenPayload.token
      },
      dashboardUiConfig: {
        hideTitle: true,
        hideChartControls: false,
        hideTab: false,
      },
    })

    embeddedInstances.set(dashboardId, result || {})

    // Ensure the iframe fills the container
    const iframe = mountPoint.querySelector('iframe')
    if (iframe) {
      iframe.style.width = '100%'
      iframe.style.height = '100%'
      iframe.style.border = 'none'
    }
  } catch (err) {
    console.error(`Failed to embed dashboard ${dashboardId}:`, err)
    if (mountPoint) {
      mountPoint.innerHTML =
        '<div style="display:flex;align-items:center;justify-content:center;height:100%;color:#999;">Failed to load dashboard. Please try again later.</div>'
    }
  }
}

function determineLayoutMode(list: UserDashboardResponse[]): 'SINGLE' | 'MULTI' | 'WIDGET' {
  type LayoutMode = UserDashboardResponse['layoutMode']

  if (list.length === 0) return 'SINGLE'
  if (list.length === 1) return 'SINGLE'

  const modeCount: Record<LayoutMode, number> = { SINGLE: 0, MULTI: 0, WIDGET: 0 }
  for (const m of list.map((d) => d.layoutMode)) {
    modeCount[m] += 1
  }

  let maxMode: LayoutMode = 'SINGLE'
  let maxCount = modeCount[maxMode]
  for (const mode of ['SINGLE', 'MULTI', 'WIDGET'] as const) {
    const count = modeCount[mode]
    if (count > maxCount) {
      maxCount = count
      maxMode = mode
    }
  }

  return maxMode
}

async function renderDashboards(): Promise<void> {
  await nextTick()

  if (layoutMode.value === 'SINGLE' && dashboards.value.length > 0) {
    const db = dashboards.value[0]
    await embedSupersetDashboard(
      db.embedId,
      db.dashboardId,
      getContainerId(db.dashboardId)
    )
  } else if (layoutMode.value === 'MULTI') {
    // Only render the active tab
    const activeDb = dashboards.value.find((d) => d.dashboardId === activeTab.value)
    if (activeDb) {
      await embedSupersetDashboard(
        activeDb.embedId,
        activeDb.dashboardId,
        getContainerId(activeDb.dashboardId)
      )
    }
  }
  // WIDGET mode renders on click (fullscreen dialog)
}

async function handleTabChange(tabName: string | number): Promise<void> {
  const db = dashboards.value.find((d) => d.dashboardId === String(tabName))
  if (!db) return

  const containerId = getContainerId(db.dashboardId)
  // Check if already embedded
  if (embeddedInstances.has(db.dashboardId)) return

  await nextTick()
  await embedSupersetDashboard(db.embedId, db.dashboardId, containerId)
}

function openFullscreen(db: UserDashboardResponse): void {
  fullscreenDashboard.value = db
  fullscreenVisible.value = true
}

async function onFullscreenOpened(): Promise<void> {
  if (!fullscreenDashboard.value) return
  const db = fullscreenDashboard.value
  await nextTick()
  await embedSupersetDashboard(
    db.embedId,
    db.dashboardId,
    'superset-fullscreen-container'
  )
}

function onFullscreenClosed(): void {
  if (fullscreenDashboard.value) {
    const instance = embeddedInstances.get(fullscreenDashboard.value.dashboardId)
    if (instance?.unmount) {
      instance.unmount()
    }
    embeddedInstances.delete(fullscreenDashboard.value.dashboardId)
  }
  fullscreenDashboard.value = null
}

onMounted(async () => {
  try {
    const storedUser = getStoredUser()
    const userId = storedUser?.userId || getUserId()
    if (!userId) {
      console.warn('No userId found, cannot load dashboards')
      loading.value = false
      return
    }
    currentUserId.value = userId

    const res = await biDashboardApi.getUserDashboards(userId, storedUser?.activeBusinessUnitId)
    // The response interceptor unwraps, so res could be the data directly or wrapped
    const list: UserDashboardResponse[] = (res as any).data || res || []
    dashboards.value = list

    if (list.length > 0) {
      layoutMode.value = determineLayoutMode(list)

      // Set default active tab for MULTI mode
      if (layoutMode.value === 'MULTI') {
        const defaultDb = list.find((d) => d.isDefault)
        activeTab.value = defaultDb ? defaultDb.dashboardId : list[0].dashboardId
      }

      await renderDashboards()
    }
  } catch (err) {
    console.error('Failed to load dashboards:', err)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  // Cleanup all embedded instances
  for (const [, instance] of embeddedInstances) {
    if (instance?.unmount) {
      instance.unmount()
    }
  }
  embeddedInstances.clear()
})
</script>

<style lang="scss" scoped>
.dashboard-landing {
  height: 100%;
  min-height: calc(100vh - var(--header-height) - 40px);
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 400px;
}

// SINGLE layout
.single-layout {
  height: 100%;

  .superset-container {
    width: 100%;
    height: calc(100vh - var(--header-height) - 40px);
  }
}

// MULTI layout (tabs)
.multi-layout {
  height: 100%;

  :deep(.el-tabs) {
    height: 100%;

    .el-tabs__content {
      height: calc(100vh - var(--header-height) - 100px);
    }

    .el-tab-pane {
      height: 100%;
    }
  }

  .superset-container {
    width: 100%;
    height: 100%;
    min-height: calc(100vh - var(--header-height) - 120px);
  }
}

// WIDGET layout (grid cards)
.widget-layout {
  .widget-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 20px;
    padding: 4px;
  }

  .widget-card {
    background: var(--background-white);
    border: 1px solid var(--border-color);
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    cursor: pointer;
    transition: box-shadow 0.2s, transform 0.2s;
    overflow: hidden;

    &:hover {
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
      transform: translateY(-2px);
    }

    .widget-card-header {
      padding: 16px 20px;
      border-bottom: 1px solid var(--border-color);
      background: var(--background-light);

      .widget-title {
        font-size: 16px;
        font-weight: 500;
        color: var(--text-primary);
      }
    }

    .widget-card-body {
      padding: 32px 20px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      min-height: 160px;
      justify-content: center;

      .widget-desc {
        font-size: 13px;
        color: var(--text-secondary);
        text-align: center;
        margin: 0;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }

      .widget-hint {
        font-size: 12px;
        color: var(--text-placeholder);
      }
    }
  }
}

// Fullscreen container in dialog
.fullscreen-container {
  width: 100%;
  height: calc(100vh - 80px);
}

// Ensure embedded iframes fill their containers
.superset-container {
  :deep(iframe) {
    width: 100% !important;
    height: 100% !important;
    border: none !important;
  }
}
</style>
