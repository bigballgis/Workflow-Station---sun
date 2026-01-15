<template>
  <el-container class="portal-layout">
    <!-- 顶部导航栏 -->
    <el-header class="portal-header">
      <div class="header-left">
        <div class="logo">
          <img src="/logo.svg" alt="Logo" class="logo-img" />
          <span class="logo-text">{{ t('app.name') }}</span>
        </div>
      </div>
      <div class="header-right">
        <el-dropdown @command="handleLanguage">
          <span class="header-action">
            <el-icon><Location /></el-icon>
            <span class="action-text">{{ currentLang }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="zh-CN">简体中文</el-dropdown-item>
              <el-dropdown-item command="zh-TW">繁體中文</el-dropdown-item>
              <el-dropdown-item command="en">English</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-badge">
          <el-button :icon="Bell" circle @click="goToNotifications" />
        </el-badge>
        <UserProfileDropdown />
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
          <el-menu-item v-if="isApprover" index="/approvals">
            <el-icon><Checked /></el-icon>
            <template #title>{{ t('menu.approvals') }}</template>
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
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  HomeFilled, List, Plus, Document, Share, Key, Bell, Setting,
  Fold, Expand, Location, Checked
} from '@element-plus/icons-vue'
import UserProfileDropdown from '@/components/UserProfileDropdown.vue'
import i18n from '@/i18n'
import { permissionApi } from '@/api/permission'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const isCollapsed = ref(false)
const unreadCount = ref(5)
const cachedViews = ref(['Dashboard', 'Tasks', 'MyApplications'])
const isApprover = ref(false)

const activeMenu = computed(() => route.path)

// Check if user is an approver
const checkApproverStatus = async () => {
  try {
    const res = await permissionApi.isApprover() as any
    if (res?.data?.isApprover !== undefined) {
      isApprover.value = res.data.isApprover
    } else if (res?.isApprover !== undefined) {
      isApprover.value = res.isApprover
    }
  } catch (e) {
    console.error('Failed to check approver status:', e)
    isApprover.value = false
  }
}

onMounted(() => {
  checkApproverStatus()
})

const langMap: Record<string, string> = { 'zh-CN': '简体中文', 'zh-TW': '繁體中文', 'en': 'English' }
const currentLang = computed(() => langMap[i18n.global.locale.value] || '简体中文')

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

const goToNotifications = () => {
  router.push('/notifications')
}

const handleLanguage = (lang: string) => {
  i18n.global.locale.value = lang as 'zh-CN' | 'zh-TW' | 'en'
  localStorage.setItem('language', lang)
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
    
    .header-action {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 8px 12px;
      border-radius: 6px;
      cursor: pointer;
      transition: background-color 0.3s;
      color: white;

      &:hover {
        background-color: rgba(255, 255, 255, 0.15);
      }

      .action-text {
        font-size: 14px;
      }
    }
    
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
