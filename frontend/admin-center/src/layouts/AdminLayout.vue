<template>
  <el-container class="admin-layout">
    <!-- Top navigation bar -->
    <el-header class="admin-header">
      <div class="header-left">
        <div class="logo">
          <span class="logo-icon">🛡️</span>
          <span class="logo-text">{{ t('app.name') }}</span>
        </div>
      </div>
      <div class="header-right">
        <UserProfileDropdown />
      </div>
    </el-header>

    <el-container class="admin-body">
      <!-- Left sidebar menu -->
      <el-aside
        :width="isCollapse ? '64px' : '260px'"
        class="admin-aside"
      >
        <el-scrollbar>
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

            <!-- Activepieces - external tool (non-prod), opens the :8085 login bridge
                 in a new tab. el-menu is in router mode, so bind :route to the current
                 path (no-op navigation) and do the real action in @click. -->
            <el-menu-item
              v-if="isSystemAdmin && apBridgeUrl"
              index="activepieces-launch"
              :route="route.path"
              @click="openActivepieces"
            >
              <el-icon><Connection /></el-icon>
              <template #title>
                {{ t('menu.activepieces') }}
              </template>
            </el-menu-item>
          </el-menu>
        </el-scrollbar>
        <div
          class="collapse-btn"
          @click="toggleCollapse"
        >
          <el-icon :size="20">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
        </div>
      </el-aside>

      <!-- Main content area -->
      <el-main class="admin-main">
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
      </el-main>
    </el-container>
  </el-container>
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
import { launchActivepieces } from '@/api/ap'

const route = useRoute()
const { t } = useI18n()

const isCollapse = ref(false)
const activeMenu = computed(() => route.path)

// Activepieces launcher (non-prod only). The menu visibility is gated on RUNTIME config
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

const openActivepieces = async () => {
  if (!apBridgeUrl.value) return
  // Cross-domain SSO handshake (plan B): ask the admin-domain /launch endpoint (where the
  // platform JWT cookie is valid) to sign into AP with the shared account and mint a
  // one-time nonce, returning the AP bridge URL carrying it. Then navigate THIS tab there.
  // The AP domain needs no platform cookie, so admin and AP may live on different parent
  // domains. Same-tab navigation (not a new tab) keeps the bridge's localStorage['token']
  // write in the same storage partition as the AP app; the user returns via browser back.
  try {
    const bridgeUrl = await launchActivepieces()
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
$primary-color: #DB0011;
$primary-dark: #8B0000;
$header-height: 60px;
$aside-bg: #ffffff;
$main-bg: #f5f7fa;

.admin-layout {
  height: 100vh;
  overflow: hidden;
}

.admin-header {
  height: $header-height;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, $primary-color 0%, $primary-dark 100%);
  color: white;
  padding: 0 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 100;

  .header-left {
    display: flex;
    align-items: center;
    gap: 20px;

    .logo {
      display: flex;
      align-items: center;
      gap: 10px;

      .logo-icon {
        font-size: 24px;
      }

      .logo-text {
        font-size: 18px;
        font-weight: 600;
        letter-spacing: 1px;
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 24px;
  }
}

.admin-body {
  height: calc(100vh - $header-height);
}

.admin-aside {
  background: $aside-bg;
  border-right: 1px solid #e6e8eb;
  transition: width 0.3s;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  .admin-menu {
    border-right: none;
    height: 100%;

    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      height: 50px;
      line-height: 50px;
      margin: 4px 8px;
      border-radius: 8px;
      
      &:hover {
        background-color: rgba($primary-color, 0.08);
      }
    }

    :deep(.el-menu-item.is-active) {
      background-color: rgba($primary-color, 0.12);
      color: $primary-color;
      font-weight: 500;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 24px;
        background-color: $primary-color;
        border-radius: 0 3px 3px 0;
      }
    }

    :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
      color: $primary-color;
    }

    :deep(.el-menu--collapse) {
      .el-menu-item,
      .el-sub-menu__title {
        margin: 4px;
      }
    }
  }

  .el-scrollbar {
    flex: 1;
  }

  .collapse-btn {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 48px;
    cursor: pointer;
    border-top: 1px solid #e6e8eb;

    &:hover {
      background-color: rgba($primary-color, 0.08);
    }
  }
}

.admin-main {
  background-color: $main-bg;
  padding: 20px;
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
