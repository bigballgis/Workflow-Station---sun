<template>
  <el-container class="portal-layout">
    <!-- 顶部导航栏 -->
    <el-header class="portal-header">
      <div class="header-left">
        <div class="logo">
          <img src="/logo.svg" alt="Logo" class="logo-img" />
          <span class="logo-text">工作流平台</span>
        </div>
      </div>
      <div class="header-right">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-badge">
          <el-button :icon="Bell" circle @click="goToNotifications" />
        </el-badge>
        <el-dropdown @command="handleCommand">
          <div class="user-info">
            <el-avatar :size="32" :src="userAvatar">{{ userName.charAt(0) }}</el-avatar>
            <span class="user-name">{{ userName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="settings">
                <el-icon><Setting /></el-icon>
                {{ t('menu.settings') }}
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container class="portal-main">
      <!-- 左侧菜单 -->
      <el-aside :width="isCollapsed ? '64px' : '240px'" class="portal-aside">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapsed"
          :router="true"
          class="portal-menu"
        >
          <el-menu-item index="/dashboard">
            <el-icon><HomeFilled /></el-icon>
            <template #title>{{ t('menu.dashboard') }}</template>
          </el-menu-item>
          <el-menu-item index="/tasks">
            <el-icon><List /></el-icon>
            <template #title>{{ t('menu.tasks') }}</template>
          </el-menu-item>
          <el-menu-item index="/processes">
            <el-icon><Plus /></el-icon>
            <template #title>{{ t('menu.processes') }}</template>
          </el-menu-item>
          <el-menu-item index="/my-applications">
            <el-icon><Document /></el-icon>
            <template #title>{{ t('menu.myApplications') }}</template>
          </el-menu-item>
          <el-menu-item index="/delegations">
            <el-icon><Share /></el-icon>
            <template #title>{{ t('menu.delegations') }}</template>
          </el-menu-item>
          <el-menu-item index="/permissions">
            <el-icon><Key /></el-icon>
            <template #title>{{ t('menu.permissions') }}</template>
          </el-menu-item>
          <el-menu-item index="/notifications">
            <el-icon><Bell /></el-icon>
            <template #title>{{ t('menu.notifications') }}</template>
          </el-menu-item>
          <el-menu-item index="/settings">
            <el-icon><Setting /></el-icon>
            <template #title>{{ t('menu.settings') }}</template>
          </el-menu-item>
        </el-menu>
        <div class="collapse-btn" @click="toggleCollapse">
          <el-icon :size="20">
            <Fold v-if="!isCollapsed" />
            <Expand v-else />
          </el-icon>
        </div>
      </el-aside>

      <!-- 主内容区 -->
      <el-main class="portal-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
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
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  HomeFilled, List, Plus, Document, Share, Key, Bell, Setting,
  ArrowDown, SwitchButton, Fold, Expand
} from '@element-plus/icons-vue'
import { logout as authLogout, clearAuth, getUser } from '@/api/auth'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const isCollapsed = ref(false)
const unreadCount = ref(5)
const cachedViews = ref(['Dashboard', 'Tasks', 'MyApplications'])

// Get current user info
const currentUser = computed(() => getUser())
const userName = computed(() => currentUser.value?.displayName || currentUser.value?.username || '用户')
const userAvatar = ref('')

const activeMenu = computed(() => route.path)

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

const goToNotifications = () => {
  router.push('/notifications')
}

const handleCommand = async (command: string) => {
  if (command === 'settings') {
    router.push('/settings')
  } else if (command === 'logout') {
    try {
      await authLogout()
      ElMessage.success('已退出登录')
    } catch (error) {
      // Even if API fails, still clear local auth
      console.error('Logout API error:', error)
    } finally {
      clearAuth()
      router.push('/login')
    }
  }
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
  background-color: var(--hsbc-red);
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
    
    .notification-badge {
      :deep(.el-button) {
        background: transparent;
        border: none;
        color: white;
        
        &:hover {
          background: rgba(255, 255, 255, 0.1);
        }
      }
    }
    
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      padding: 4px 8px;
      border-radius: 4px;
      
      &:hover {
        background: rgba(255, 255, 255, 0.1);
      }
      
      .user-name {
        font-size: 14px;
      }
    }
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
