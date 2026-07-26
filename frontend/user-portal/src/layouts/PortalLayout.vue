<template>
  <div class="portal-layout">
    <!-- 左侧通高侧栏：品牌区 + 菜单 + 底部折叠（与 admin-center 同构） -->
    <aside
      class="portal-aside"
      :class="{ 'is-collapsed': isCollapsed }"
    >
      <div class="brand">
        <img
          class="brand-mark"
          :src="brandMarkUrl"
          alt=""
        >
        <span class="brand-name">{{ t('app.name') }}</span>
      </div>
      <el-scrollbar class="aside-scroll">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapsed"
          :router="true"
          class="portal-menu"
        >
          <el-menu-item
            v-if="showFullPortal"
            index="/dashboard"
          >
            <el-icon><HomeFilled /></el-icon>
            <template #title>
              {{ t('menu.dashboard') }}
            </template>
          </el-menu-item>
          <el-menu-item
            v-if="showFullPortal && hasBiDashboards"
            index="/bi-dashboard"
          >
            <el-icon><DataAnalysis /></el-icon>
            <template #title>
              BI Dashboard
            </template>
          </el-menu-item>
          <el-menu-item
            v-if="showFullPortal"
            index="/tasks"
            class="menu-item-tasks"
          >
            <el-badge
              :value="pendingTaskCount"
              :max="99"
              :hidden="pendingTaskCount === 0"
              type="danger"
              class="task-menu-badge-icon"
            >
              <el-icon><List /></el-icon>
            </el-badge>
            <template #title>
              <span class="task-menu-title-with-badge">
                <span class="task-menu-title-text">{{ t('menu.tasks') }}</span>
                <el-badge
                  :value="pendingTaskCount"
                  :max="99"
                  :hidden="pendingTaskCount === 0"
                  type="danger"
                  class="task-menu-badge-text"
                />
              </span>
            </template>
          </el-menu-item>
          <el-menu-item
            v-if="showFullPortal"
            index="/tasks/completed"
          >
            <el-icon><Finished /></el-icon>
            <template #title>
              {{ t('menu.completedTasks') }}
            </template>
          </el-menu-item>
          <el-menu-item
            v-if="showFullPortal"
            index="/processes"
          >
            <el-icon><Plus /></el-icon>
            <template #title>
              {{ t('menu.processes') }}
            </template>
          </el-menu-item>
          <el-menu-item
            v-if="showFullPortal"
            index="/my-applications"
          >
            <el-icon><Document /></el-icon>
            <template #title>
              {{ t('menu.myApplications') }}
            </template>
          </el-menu-item>
          <el-menu-item
            v-if="showFullPortal"
            index="/delegations"
          >
            <el-icon><Share /></el-icon>
            <template #title>
              {{ t('menu.delegations') }}
            </template>
          </el-menu-item>
          <el-menu-item
            index="/permissions"
            class="menu-item-permissions"
          >
            <el-badge
              :value="pendingApprovalCount"
              :max="99"
              :hidden="pendingApprovalCount === 0"
              type="danger"
              class="perm-menu-badge-icon"
            >
              <el-icon><Key /></el-icon>
            </el-badge>
            <template #title>
              <span class="perm-menu-title-with-badge">
                <span class="perm-menu-title-text">{{ t('menu.permissions') }}</span>
                <el-badge
                  :value="pendingApprovalCount"
                  :max="99"
                  :hidden="pendingApprovalCount === 0"
                  type="danger"
                  class="perm-menu-badge-text"
                />
              </span>
            </template>
          </el-menu-item>
          <el-menu-item
            v-if="showFullPortal"
            index="/relation-tables"
          >
            <el-icon><Grid /></el-icon>
            <template #title>
              {{ t('menu.relationTables') }}
            </template>
          </el-menu-item>
          <el-sub-menu
            v-if="showFullPortal"
            index="views-group"
          >
            <template #title>
              <el-icon><ViewIcon /></el-icon>
              <span>{{ t('menu.views') }}</span>
            </template>
            <el-menu-item
              v-for="fu in viewFunctionUnits"
              :key="fu.functionUnitCode"
              :index="`/views/${fu.functionUnitCode}`"
            >
              {{ fu.functionUnitName }}
            </el-menu-item>
            <el-menu-item
              v-if="!viewFuLoading && viewFunctionUnits.length === 0"
              index="/views"
              disabled
            >
              {{ t('mainTableView.noPublishedFu') }}
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-scrollbar>
      <div
        class="collapse-btn"
        @click="toggleCollapse"
      >
        <el-icon :size="20">
          <Fold v-if="!isCollapsed" />
          <Expand v-else />
        </el-icon>
      </div>
    </aside>

    <!-- 右侧主体：红色顶栏（面包屑 + 通知/工作台/用户）+ 内容画布 -->
    <div class="portal-body">
      <header class="portal-header">
        <nav class="breadcrumb">
          <router-link
            to="/dashboard"
            class="crumb-home"
          >
            {{ t('menu.dashboard') }}
          </router-link>
          <template v-if="currentTitle && route.path !== '/dashboard'">
            <span class="crumb-sep">/</span>
            <span class="crumb-current">{{ currentTitle }}</span>
          </template>
        </nav>
        <div class="header-right">
          <NotificationBadge />
          <WorkspaceContextBar />
          <UserProfileDropdown />
        </div>
      </header>

      <SelfServiceBanner
        :portal-access-mode="portalAccessMode"
        :workspace-context-count="workspaceContextCount"
      />

      <main class="portal-content">
        <router-view v-slot="{ Component }">
          <transition
            name="fade"
            mode="out-in"
          >
            <keep-alive :include="cachedViews">
              <component :is="Component" />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import {
  HomeFilled, List, Plus, Document, Share, Key,
  Fold, Expand, Finished, DataAnalysis, Grid, View as ViewIcon
} from '@element-plus/icons-vue'
import SelfServiceBanner from '@/components/SelfServiceBanner.vue'
import WorkspaceContextBar from '@/components/WorkspaceContextBar.vue'
import {
  applyWorkspaceAwarePortalAccess,
  getCurrentUser,
  getStoredUser,
  listWorkspaceContexts,
  reconcilePortalWorkspaceSession,
  saveUser,
  USER_ID_KEY
} from '@/api/auth'
import UserProfileDropdown from '@/components/UserProfileDropdown.vue'
import NotificationBadge from '@/components/NotificationBadge.vue'
import { biDashboardApi } from '@/api/biDashboard'
import { usePendingApprovalStore } from '@/stores/pendingApproval'
import { usePendingTaskStore } from '@/stores/pendingTask'
import { mainTableViewApi, type FunctionUnitViewMenuItem } from '@/api/mainTableView'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const pendingApprovalStore = usePendingApprovalStore()
const { count: pendingApprovalCount } = storeToRefs(pendingApprovalStore)
const pendingTaskStore = usePendingTaskStore()
const { count: pendingTaskCount } = storeToRefs(pendingTaskStore)

const brandMarkUrl = `${import.meta.env.BASE_URL}hermes-mark.svg`

const isCollapsed = ref(false)
const cachedViews = ref(['Dashboard', 'Tasks', 'MyApplications'])
const hasBiDashboards = ref(false)
const viewFunctionUnits = ref<FunctionUnitViewMenuItem[]>([])
const viewFuLoading = ref(false)

const activeMenu = computed(() => route.path)

// 顶栏面包屑：当前页标题取路由 meta.titleKey（与 admin-center 同构）
const currentTitle = computed(() => {
  const key = route.meta.titleKey as string | undefined
  return key ? t(key) : ''
})

/** 与 localStorage 解耦：路由守卫 saveUser 后 computed(getStoredUser) 不会重算，需 ref + /me 显式同步 */
const portalAccessMode = ref<string | undefined>(getStoredUser()?.portalAccessMode)
/** null=未拉取；与 /workspace-contexts 一致，用于横幅：有 UBR 时不应仅因 portalAccessMode 滞后仍显示「无工作台」 */
const workspaceContextCount = ref<number | null>(null)
const showFullPortal = computed(
  () => portalAccessMode.value !== 'PERMISSION_SELF_SERVICE_ONLY'
)

// Check if user has BI dashboards assigned
const checkBiDashboards = async () => {
  try {
    const storedUser = getStoredUser()
    const userId = storedUser?.userId || localStorage.getItem(USER_ID_KEY)
    if (userId) {
      const dashboards = await biDashboardApi.getUserDashboards(
        userId,
        storedUser?.activeBusinessUnitId
      )
      hasBiDashboards.value = Array.isArray(dashboards) && dashboards.length > 0
    }
  } catch (e) {
    console.error('Failed to check BI dashboards:', e)
    hasBiDashboards.value = false
  }
}

async function syncPortalAccessFromServer() {
  await reconcilePortalWorkspaceSession()
  try {
    const contexts = await listWorkspaceContexts()
    workspaceContextCount.value = Array.isArray(contexts) ? contexts.length : 0
  } catch {
    workspaceContextCount.value = 0
  }
  void pendingApprovalStore.fetchPendingCount()
  // 待办列表、首页概览会各自用一次 query/overview 更新角标，避免 layout 再打满 queryTasks。
  if (route.path !== '/tasks' && route.path !== '/dashboard') {
    void pendingTaskStore.fetchPendingCount()
  }
  try {
    const u = await getCurrentUser()
    const hasCtx = (workspaceContextCount.value ?? 0) > 0
    const merged = applyWorkspaceAwarePortalAccess(u, hasCtx)
    saveUser(merged)
    localStorage.setItem(USER_ID_KEY, merged.userId)
    portalAccessMode.value = merged.portalAccessMode
  } catch {
    portalAccessMode.value = getStoredUser()?.portalAccessMode
  }
}

async function loadViewFunctionUnits() {
  viewFuLoading.value = true
  try {
    const res = await mainTableViewApi.listFunctionUnits()
    viewFunctionUnits.value = res.data || []
    if (route.path === '/views' && viewFunctionUnits.value.length) {
      await router.replace(`/views/${viewFunctionUnits.value[0].functionUnitCode}`)
    }
  } catch {
    viewFunctionUnits.value = []
  } finally {
    viewFuLoading.value = false
  }
}

onMounted(() => {
  void (async () => {
    await syncPortalAccessFromServer()
    if (showFullPortal.value) {
      await Promise.all([checkBiDashboards(), loadViewFunctionUnits()])
    }
  })()
})

watch(
  () => route.path,
  () => {
    void pendingApprovalStore.fetchPendingCount()
  }
)

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}
</script>

<style lang="scss" scoped>
$aside-width: 248px; // 与 admin-center / developer-workstation 侧栏同宽
$aside-collapsed-width: 64px;

.portal-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background-color: var(--ws-canvas);
}

// ==================== 右侧主体 ====================
.portal-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.portal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, var(--hsbc-red) 0%, #8B0000 100%);
  color: white;
  padding: 0 24px;
  height: var(--header-height);
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;
  }
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;

  .crumb-home {
    color: #fff;
    font-weight: 700;
    text-decoration: none;

    &:hover {
      opacity: 0.85;
    }
  }

  .crumb-sep {
    color: rgba(255, 255, 255, 0.65);
  }

  .crumb-current {
    color: #fff;
    font-weight: 700;
  }
}

.portal-content {
  flex: 1;
  min-height: 0;
  min-width: 0;
  background-color: var(--background-light);
  padding: 20px;
  overflow-y: auto;
}

// ==================== 白色通高侧栏 ====================
.portal-aside {
  display: flex;
  flex-direction: column;
  width: $aside-width;
  flex-shrink: 0;
  background: white;
  border-right: 1px solid var(--border-color);
  transition: width 0.3s;
  overflow: hidden;

  &.is-collapsed {
    width: $aside-collapsed-width;

    .brand {
      justify-content: center;
      padding: 0;
    }

    .brand-name {
      display: none;
    }
  }

  .brand {
    display: flex;
    align-items: center;
    gap: 12px;
    height: var(--header-height);
    padding: 0 20px;
    flex-shrink: 0;
    border-bottom: 1px solid #f0f0f0;

    .brand-mark {
      display: block;
      width: 28px;
      height: 28px;
      flex-shrink: 0;
    }

    .brand-name {
      color: var(--ws-text);
      font-size: 16px;
      font-weight: 700;
      letter-spacing: 0.5px;
      white-space: nowrap;
    }
  }

  .aside-scroll {
    flex: 1;
    min-height: 0;
  }

  .portal-menu {
    flex: 1;
    border-right: none;
    padding: 8px;

    /* 与 admin-center 一致：圆角菜单条目 + 红色激活左条 */
    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      height: 44px;
      line-height: 44px;
      margin: 2px 0;
      border-radius: 10px;
    }

    :deep(.el-menu-item.is-active) {
      font-weight: 600;
      background-color: rgba(219, 0, 17, 0.1);
      color: var(--hsbc-red);

      &::before {
        content: '';
        position: absolute;
        left: -8px; // 贴到侧栏最左缘（抵消菜单容器 padding）
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 22px;
        background-color: var(--hsbc-red);
        border-radius: 0 3px 3px 0;
      }
    }

    /* 展开：徽标在菜单文字右侧；收起：徽标在图标上 */
    &:not(.el-menu--collapse) .menu-item-tasks .task-menu-badge-icon :deep(.el-badge__content) {
      display: none !important;
    }
    &.el-menu--collapse .menu-item-tasks .task-menu-badge-text {
      display: none !important;
    }

    &:not(.el-menu--collapse) .menu-item-permissions .perm-menu-badge-icon :deep(.el-badge__content) {
      display: none !important;
    }
    &.el-menu--collapse .menu-item-permissions .perm-menu-badge-text {
      display: none !important;
    }

    /* el-menu-item 全局有 * { vertical-align: bottom }，会把徽标压到偏下；此处拉回垂直居中 */
    .menu-item-tasks {
      .task-menu-title-with-badge,
      .task-menu-title-with-badge :deep(*) {
        vertical-align: middle !important;
      }

      .task-menu-badge-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
      }

      .task-menu-badge-icon :deep(.el-badge__content.is-fixed) {
        top: 50%;
        transform: translateY(-50%) translateX(100%);
      }
    }

    .menu-item-tasks .task-menu-title-with-badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      width: 100%;
      min-width: 0;
    }
    .menu-item-tasks .task-menu-title-text {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .menu-item-permissions {
      .perm-menu-title-with-badge,
      .perm-menu-title-with-badge :deep(*) {
        vertical-align: middle !important;
      }

      .perm-menu-badge-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
      }

      /* 角标相对图标垂直居中（默认 top:0 + translateY(-50%) 会贴在图标上沿） */
      .perm-menu-badge-icon :deep(.el-badge__content.is-fixed) {
        top: 50%;
        transform: translateY(-50%) translateX(100%);
      }
    }

    .menu-item-permissions .perm-menu-title-with-badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      width: 100%;
      min-width: 0;
    }
    .menu-item-permissions .perm-menu-title-text {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    
  }

  .collapse-btn {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 48px;
    cursor: pointer;
    border-top: 1px solid var(--border-color);
    
    &:hover {
      background-color: var(--background-light);
    }
  }
}

</style>
