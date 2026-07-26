<template>
  <div class="admin-layout">
    <!-- Left dark sidebar: brand on top, nav, collapse toggle at bottom -->
    <aside
      class="admin-aside"
      :class="{ 'is-collapsed': isCollapse }"
    >
      <div class="brand">
        <span class="brand-mark" />
        <span class="brand-name">{{ t('app.name') }}</span>
      </div>

      <el-scrollbar class="aside-scroll">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :collapse-transition="false"
          class="admin-menu"
          router
        >
          <!-- Dashboard - everyone -->
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <template #title>
              {{ t('menu.dashboard') }}
            </template>
          </el-menu-item>

          <!-- User Management - requires user:read -->
          <el-menu-item
            v-if="canReadUser"
            index="/user/list"
          >
            <el-icon><User /></el-icon>
            <template #title>
              {{ t('menu.userManagement') }}
            </template>
          </el-menu-item>

          <!-- Entitlement Management - sub-menu -->
          <el-sub-menu
            v-if="canReadUser || canReadRole"
            index="entitlement"
          >
            <template #title>
              <el-icon><Lock /></el-icon>
              <span>{{ t('menu.entitlementManagement') }}</span>
            </template>
            <el-menu-item
              v-if="canReadUser"
              index="/organization"
            >
              {{ t('menu.organization') }}
            </el-menu-item>
            <el-menu-item
              v-if="canReadUser"
              index="/virtual-group"
            >
              {{ t('menu.virtualGroup') }}
            </el-menu-item>
            <el-menu-item
              v-if="canReadRole"
              index="/role"
            >
              {{ t('menu.roleManagement') }}
            </el-menu-item>
          </el-sub-menu>

          <!-- Function Unit - requires system:admin -->
          <el-menu-item
            v-if="isSystemAdmin"
            index="/function-unit"
          >
            <el-icon><Box /></el-icon>
            <template #title>
              {{ t('menu.functionUnit') }}
            </template>
          </el-menu-item>

          <!-- BI Management - requires system:admin -->
          <el-sub-menu
            v-if="isSystemAdmin"
            index="bi-management"
          >
            <template #title>
              <el-icon><DataAnalysis /></el-icon>
              <span>{{ t('menu.biManagement') }}</span>
            </template>
            <el-menu-item index="/bi-management/dashboard-registry">
              {{ t('menu.biDashboardRegistry') }}
            </el-menu-item>
            <el-menu-item index="/bi-management/dashboard-assignment">
              {{ t('menu.biDashboardAssignment') }}
            </el-menu-item>
            <el-menu-item index="/bi-management/rbac-mapping">
              {{ t('menu.biRbacMapping') }}
            </el-menu-item>
          </el-sub-menu>

          <!-- Audit Log - requires audit:read or log:read -->
          <el-menu-item
            v-if="canReadAudit"
            index="/audit"
          >
            <el-icon><Document /></el-icon>
            <template #title>
              {{ t('menu.audit') }}
            </template>
          </el-menu-item>

          <!-- Relation Tables - requires system:admin -->
          <el-sub-menu
            v-if="isSystemAdmin"
            index="relation-tables"
          >
            <template #title>
              <el-icon><Grid /></el-icon>
              <span>{{ t('menu.relationTables') }}</span>
            </template>
            <el-menu-item index="/relation-tables/structure">
              {{ t('menu.tableStructure') }}
            </el-menu-item>
            <el-menu-item index="/relation-tables/data">
              {{ t('menu.tableData') }}
            </el-menu-item>
          </el-sub-menu>

          <!-- ServiceTask - external tool (non-prod), opens the :8085 login bridge
               in a new tab. el-menu is in router mode, so bind :route to the current
               path (no-op navigation) and do the real action in @click. -->
          <el-menu-item
            v-if="isSystemAdmin && apBridgeUrl"
            index="service-task-launch"
            :route="route.path"
            @click="openServiceTask"
          >
            <el-icon><Connection /></el-icon>
            <template #title>
              {{ t('menu.serviceTask') }}
            </template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>

      <div
        class="collapse-btn"
        role="button"
        @click="toggleCollapse"
      >
        <el-icon :size="18">
          <Fold v-if="!isCollapse" />
          <Expand v-else />
        </el-icon>
      </div>
    </aside>

    <!-- Right column: white top bar + canvas -->
    <div class="admin-body">
      <header class="admin-header">
        <nav class="breadcrumb">
          <router-link
            to="/dashboard"
            class="crumb-home"
          >
            {{ t('menu.home') }}
          </router-link>
          <template v-if="currentTitle && route.path !== '/dashboard'">
            <span class="crumb-sep">/</span>
            <span class="crumb-current">{{ currentTitle }}</span>
          </template>
        </nav>
        <div class="header-right">
          <UserProfileDropdown />
        </div>
      </header>

      <main class="admin-main">
        <router-view v-slot="{ Component, route }">
          <transition
            name="fade"
            mode="out-in"
          >
            <keep-alive>
              <component
                :is="Component"
                :key="route.path"
              />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  Fold, Expand,
  Odometer, Box, User, Lock, Document, DataAnalysis, Grid, Connection
} from '@element-plus/icons-vue'
import UserProfileDropdown from '@/components/UserProfileDropdown.vue'
import { hasPermission, PERMISSIONS } from '@/utils/permission'
import { launchServiceTask } from '@/api/serviceTask'

const route = useRoute()
const { t } = useI18n()

const isCollapse = ref(false)
const activeMenu = computed(() => route.path)

// 顶栏面包屑：当前页标题取路由 meta.titleKey
const currentTitle = computed(() => {
  const key = route.meta.titleKey
  return key ? t(key) : ''
})

// ServiceTask launcher (non-prod only). The menu visibility is gated on RUNTIME config
// (window.__APP_CONFIG__.AP_BRIDGE_URL, injected per-environment at container start) — the
// frontend image is built once and promoted to uat/sit/prod, so this can't be a build-time
// value. Non-prod sets it -> the entry shows; prod leaves it empty -> hidden (AP is
// runtime-only there). Empty or an un-substituted "${...}" placeholder falls back to the
// dev default. Here the value is used ONLY as the on/off flag — the real bridge URL is
// minted server-side by /launch (cross-domain SSO handshake), not navigated to directly.
const apBridgeUrl = computed(() => {
  const rt = window.__APP_CONFIG__?.AP_BRIDGE_URL
  if (rt && !rt.includes('${')) return rt
  return import.meta.env.DEV ? 'http://localhost:8085/__ap/bridge' : ''
})

const openServiceTask = async () => {
  if (!apBridgeUrl.value) return
  // Cross-domain SSO handshake (plan B): ask the admin-domain /launch endpoint (where the
  // platform JWT cookie is valid) to sign into AP with the shared account and mint a
  // one-time nonce, returning the AP bridge URL carrying it. Then navigate THIS tab there.
  // The AP domain needs no platform cookie, so admin and AP may live on different parent
  // domains. Same-tab navigation (not a new tab) keeps the bridge's localStorage['token']
  // write in the same storage partition as the AP app; the user returns via browser back.
  try {
    const bridgeUrl = await launchServiceTask()
    if (bridgeUrl) window.location.assign(bridgeUrl)
  } catch {
    // Error toast is already surfaced by the request response interceptor
    // (401 goes through refresh/login; 502/others show a toast).
  }
}

// Permission checks
const isSystemAdmin = computed(() => hasPermission(PERMISSIONS.SYSTEM_ADMIN))
const canReadUser = computed(() => hasPermission(PERMISSIONS.USER_READ))
const canReadRole = computed(() => hasPermission(PERMISSIONS.ROLE_READ))
const canReadAudit = computed(() => hasPermission(PERMISSIONS.AUDIT_READ) || hasPermission(PERMISSIONS.LOG_READ))

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}
</script>

<style scoped lang="scss">
$header-height: 64px;
$aside-width: 248px;
$aside-collapsed-width: 64px;
$primary-color: #db0011;
$primary-dark: #8b0000;

.admin-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background-color: var(--ws-canvas);
}

// ==================== 白色侧栏（回归原配色） ====================
.admin-aside {
  display: flex;
  flex-direction: column;
  width: $aside-width;
  flex-shrink: 0;
  background: #ffffff;
  border-right: 1px solid #e6e8eb;
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
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: $header-height;
  padding: 0 20px;
  flex-shrink: 0;
  border-bottom: 1px solid #f0f0f0;

  .brand-mark {
    width: 28px;
    height: 28px;
    border-radius: 8px;
    background: var(--primary-color);
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

.admin-menu {
  border-right: none;
  padding: 8px;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #4a4a4a;
  --el-menu-active-color: #{$primary-color};
  --el-menu-hover-bg-color: rgba(219, 0, 17, 0.06);
  --el-menu-hover-text-color: #{$primary-color};

  &:not(.el-menu--collapse) {
    width: 100%;
  }

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    height: 44px;
    line-height: 44px;
    margin: 2px 0;
    border-radius: 10px;
  }

  :deep(.el-menu-item.is-active) {
    color: $primary-color;
    font-weight: 600;
    background-color: rgba(219, 0, 17, 0.1);

    &::before {
      content: '';
      position: absolute;
      left: -8px; // 贴到侧栏最左缘（抵消菜单容器 padding）
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 22px;
      background-color: $primary-color;
      border-radius: 0 3px 3px 0;
    }
  }

  :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
    color: $primary-color;
  }

  :deep(.el-menu) {
    background: transparent;
  }
}

.collapse-btn {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 48px;
  flex-shrink: 0;
  cursor: pointer;
  color: var(--ws-text-secondary);
  border-top: 1px solid #e6e8eb;

  &:hover {
    color: $primary-color;
    background-color: rgba(219, 0, 17, 0.06);
  }
}

// ==================== 右侧主体 ====================
.admin-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.admin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: $header-height;
  flex-shrink: 0;
  padding: 0 24px;
  background: linear-gradient(135deg, $primary-color 0%, $primary-dark 100%);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
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

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.admin-main {
  flex: 1;
  min-height: 0;
  background-color: var(--ws-canvas);
  padding: 24px;
  overflow-y: auto;
}

// Page transition animation
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
