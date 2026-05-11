<template>
  <el-container class="portal-layout">
    <!-- 顶部导航栏 -->
    <el-header class="portal-header">
      <div class="header-left">
        <div class="logo">
          <img
            src="/logo.svg"
            alt="Logo"
            class="logo-img"
          >
          <span class="logo-text">{{ t('app.name') }}</span>
        </div>
      </div>
      <div class="header-right">
        <NotificationBadge />
        <WorkspaceContextBar />
        <UserProfileDropdown />
      </div>
    </el-header>

    <SelfServiceBanner
      :portal-access-mode="portalAccessMode"
      :workspace-context-count="workspaceContextCount"
    />

    <el-container class="portal-main">
      <!-- 左侧菜单 -->
      <el-aside
        :width="isCollapsed ? '64px' : '260px'"
        class="portal-aside"
      >
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
              Relation Tables
            </template>
          </el-menu-item>
        </el-menu>
        <div
          class="collapse-btn"
          @click="toggleCollapse"
        >
          <el-icon :size="20">
            <Fold v-if="!isCollapsed" />
            <Expand v-else />
          </el-icon>
        </div>
      </el-aside>

      <!-- 主内容区 -->
      <el-main class="portal-content">
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
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import {
  HomeFilled, List, Plus, Document, Share, Key,
  Fold, Expand, Finished, DataAnalysis, Grid
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

const { t } = useI18n()
const route = useRoute()
const pendingApprovalStore = usePendingApprovalStore()
const { count: pendingApprovalCount } = storeToRefs(pendingApprovalStore)
const pendingTaskStore = usePendingTaskStore()
const { count: pendingTaskCount } = storeToRefs(pendingTaskStore)

const isCollapsed = ref(false)
const cachedViews = ref(['Dashboard', 'Tasks', 'MyApplications'])
const hasBiDashboards = ref(false)

const activeMenu = computed(() => route.path)

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
  void pendingTaskStore.fetchPendingCount()
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

onMounted(() => {
  void (async () => {
    await syncPortalAccessFromServer()
    if (showFullPortal.value) {
      await checkBiDashboards()
    }
  })()
})

watch(
  () => route.path,
  () => {
    void pendingApprovalStore.fetchPendingCount()
    void pendingTaskStore.fetchPendingCount()
  }
)

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}
</script>

<style lang="scss" scoped>
.portal-layout {
  height: 100vh;
}

.portal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, var(--hsbc-red) 0%, #8B0000 100%);
  color: white;
  padding: 0 20px;
  height: var(--header-height);
  
  .header-left {
    display: flex;
    align-items: center;
    
    .logo {
      display: flex;
      align-items: center;
      gap: 10px;
      
      .logo-img {
        height: 32px;
      }
      
      .logo-text {
        font-size: 18px;
        font-weight: 600;
      }
    }
  }
  
  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;
  }
}

.portal-main {
  height: calc(100vh - var(--header-height));
}

.portal-aside {
  background: white;
  border-right: 1px solid var(--border-color);
  transition: width 0.3s;
  display: flex;
  flex-direction: column;
  
  .portal-menu {
    flex: 1;
    border-right: none;

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
    
    .el-menu-item.is-active {
      background-color: rgba(219, 0, 17, 0.1);
      color: var(--hsbc-red);
      
      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 0;
        bottom: 0;
        width: 3px;
        background-color: var(--hsbc-red);
      }
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

.portal-content {
  background-color: var(--background-light);
  padding: 20px;
  overflow-y: auto;
}
</style>
