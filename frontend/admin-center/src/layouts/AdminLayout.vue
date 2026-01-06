<template>
  <el-container class="admin-layout">
    <!-- 顶部导航栏 -->
    <el-header class="admin-header">
      <div class="header-left">
        <div class="logo">
          <span class="logo-icon">🛡️</span>
          <span class="logo-text" v-if="!isCollapse">管理员中心</span>
        </div>
        <el-icon class="collapse-btn" @click="toggleCollapse">
          <Fold v-if="!isCollapse" />
          <Expand v-else />
        </el-icon>
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
        <el-dropdown @command="handleUserCommand">
          <span class="user-info">
            <el-avatar :size="36" class="user-avatar">
              {{ displayName.charAt(0) }}
            </el-avatar>
            <span class="user-name">{{ displayName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><User /></el-icon>个人信息
              </el-dropdown-item>
              <el-dropdown-item command="settings">
                <el-icon><Setting /></el-icon>系统设置
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container class="admin-body">
      <!-- 左侧菜单 -->
      <el-aside :width="isCollapse ? '64px' : '240px'" class="admin-aside">
        <el-scrollbar>
          <el-menu
            :default-active="activeMenu"
            :collapse="isCollapse"
            :collapse-transition="false"
            class="admin-menu"
            router
          >
            <el-menu-item index="/dashboard">
              <el-icon><Odometer /></el-icon>
              <template #title>{{ t('menu.dashboard') }}</template>
            </el-menu-item>
            
            <el-sub-menu index="user">
              <template #title>
                <el-icon><User /></el-icon>
                <span>{{ t('menu.userManagement') }}</span>
              </template>
              <el-menu-item index="/user/list">{{ t('menu.userList') }}</el-menu-item>
              <el-menu-item index="/user/import">{{ t('menu.userImport') }}</el-menu-item>
            </el-sub-menu>
            
            <el-sub-menu index="organization">
              <template #title>
                <el-icon><OfficeBuilding /></el-icon>
                <span>{{ t('menu.organization') }}</span>
              </template>
              <el-menu-item index="/organization/department">{{ t('menu.department') }}</el-menu-item>
            </el-sub-menu>
            
            <el-sub-menu index="role">
              <template #title>
                <el-icon><Key /></el-icon>
                <span>{{ t('menu.roleManagement') }}</span>
              </template>
              <el-menu-item index="/role/list">{{ t('menu.roleList') }}</el-menu-item>
              <el-menu-item index="/role/permission">{{ t('menu.permissionConfig') }}</el-menu-item>
            </el-sub-menu>
            
            <el-menu-item index="/virtual-group">
              <el-icon><Connection /></el-icon>
              <template #title>{{ t('menu.virtualGroup') }}</template>
            </el-menu-item>
            
            <el-menu-item index="/function-unit">
              <el-icon><Box /></el-icon>
              <template #title>{{ t('menu.functionUnit') }}</template>
            </el-menu-item>
            
            <el-menu-item index="/dictionary">
              <el-icon><Collection /></el-icon>
              <template #title>{{ t('menu.dictionary') }}</template>
            </el-menu-item>
            
            <el-menu-item index="/monitor">
              <el-icon><Monitor /></el-icon>
              <template #title>{{ t('menu.monitor') }}</template>
            </el-menu-item>
            
            <el-menu-item index="/audit">
              <el-icon><Document /></el-icon>
              <template #title>{{ t('menu.audit') }}</template>
            </el-menu-item>
            
            <el-menu-item index="/config">
              <el-icon><Setting /></el-icon>
              <template #title>{{ t('menu.config') }}</template>
            </el-menu-item>
          </el-menu>
        </el-scrollbar>
      </el-aside>

      <!-- 主内容区 -->
      <el-main class="admin-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
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
  Fold, Expand, ArrowDown, User, Setting, SwitchButton,
  Odometer, OfficeBuilding, Key, Connection, Box, Collection, 
  Monitor, Document, Location
} from '@element-plus/icons-vue'
import { logout as authLogout, clearAuth, getUser } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()

const isCollapse = ref(false)
const activeMenu = computed(() => route.path)

// Get current user info
const currentUser = computed(() => getUser())
const displayName = computed(() => currentUser.value?.displayName || currentUser.value?.username || 'Admin')

const langMap: Record<string, string> = { 'zh-CN': '简体中文', 'zh-TW': '繁體中文', 'en': 'English' }
const currentLang = computed(() => langMap[locale.value] || '简体中文')

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

const handleLanguage = (lang: string) => {
  locale.value = lang
  localStorage.setItem('locale', lang)
}

const handleUserCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await authLogout()
      ElMessage.success('已退出登录')
    } catch (error) {
      console.error('Logout API error:', error)
    } finally {
      clearAuth()
      router.push('/login')
    }
  } else if (command === 'settings') {
    router.push('/config')
  } else if (command === 'profile') {
    router.push('/profile')
  }
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

    .collapse-btn {
      font-size: 20px;
      cursor: pointer;
      padding: 8px;
      border-radius: 6px;
      transition: background-color 0.3s;

      &:hover {
        background-color: rgba(255, 255, 255, 0.15);
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 24px;

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

    .user-info {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 6px 12px;
      border-radius: 8px;
      cursor: pointer;
      transition: background-color 0.3s;
      color: white;

      &:hover {
        background-color: rgba(255, 255, 255, 0.15);
      }

      .user-avatar {
        background: rgba(255, 255, 255, 0.2);
        color: white;
        font-weight: 600;
      }

      .user-name {
        font-size: 14px;
        font-weight: 500;
      }
    }
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
}

.admin-main {
  background-color: $main-bg;
  padding: 20px;
  overflow-y: auto;
}

// 页面切换动画
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
