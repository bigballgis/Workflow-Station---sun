<template>
  <el-container class="main-layout">
    <el-header class="header">
      <div class="logo">
        <span class="logo-text">开发者工作站</span>
      </div>
      <div class="header-right">
        <el-dropdown @command="handleLanguageChange">
          <span class="language-trigger">
            <el-icon><Connection /></el-icon>
            {{ currentLanguageLabel }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="zh-CN">简体中文</el-dropdown-item>
              <el-dropdown-item command="zh-TW">繁體中文</el-dropdown-item>
              <el-dropdown-item command="en">English</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown>
          <span class="user-trigger">
            <el-avatar :size="32" icon="User" />
            <span class="username">{{ displayName }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-container>
      <el-aside :width="sidebarWidth" class="sidebar">
        <el-menu :default-active="activeMenu" :collapse="isCollapsed" router>
          <el-menu-item index="/function-units">
            <el-icon><Folder /></el-icon>
            <span>{{ $t('functionUnit.title') }}</span>
          </el-menu-item>
          <el-menu-item index="/icons">
            <el-icon><Picture /></el-icon>
            <span>{{ $t('icon.title') }}</span>
          </el-menu-item>
        </el-menu>
        <div class="collapse-btn" @click="toggleSidebar">
          <el-icon>
            <DArrowLeft v-if="!isCollapsed" />
            <DArrowRight v-else />
          </el-icon>
        </div>
      </el-aside>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Folder, Picture, Connection, DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import { logout as authLogout, clearAuth, getUser, getCurrentUser } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const { locale } = useI18n()

const activeMenu = computed(() => route.path)

// Sidebar collapse state
const isCollapsed = ref(false)
const sidebarWidth = computed(() => isCollapsed.value ? '64px' : '240px')

// Get current user info
const currentUser = computed(() => getUser())
const displayName = computed(() => {
  if (currentUser.value?.displayName) {
    return currentUser.value.displayName
  }
  if (currentUser.value?.username) {
    return currentUser.value.username
  }
  // 如果没有用户信息，可能是未登录，返回空字符串或提示
  return '未登录'
})

const languageLabels: Record<string, string> = {
  'zh-CN': '简体中文',
  'zh-TW': '繁體中文',
  'en': 'English'
}

const currentLanguageLabel = computed(() => languageLabels[locale.value] || '简体中文')

function handleLanguageChange(lang: string) {
  locale.value = lang
  localStorage.setItem('locale', lang)
}

// Toggle sidebar collapse state
function toggleSidebar(): void {
  isCollapsed.value = !isCollapsed.value
  try {
    localStorage.setItem('sidebar-collapsed', String(isCollapsed.value))
  } catch (e) {
    // localStorage not available, ignore
  }
}

// Initialize sidebar state from localStorage
function initSidebarState(): void {
  try {
    const stored = localStorage.getItem('sidebar-collapsed')
    isCollapsed.value = stored === 'true'
  } catch (e) {
    // localStorage not available, use default
    isCollapsed.value = false
  }
}

onMounted(async () => {
  initSidebarState()
  
  // 如果用户信息不存在，尝试从 API 获取
  if (!currentUser.value) {
    try {
      const user = await getCurrentUser()
      if (user) {
        const { saveUser } = await import('@/api/auth')
        saveUser(user)
      }
    } catch (error) {
      console.error('Failed to get current user:', error)
      // 如果获取失败，可能是 token 无效，清除认证信息
      clearAuth()
      router.push('/login')
    }
  }
})

async function handleLogout() {
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
</script>

<style lang="scss" scoped>
.main-layout {
  height: 100vh;

  > .el-container {
    height: calc(100vh - 60px);
    overflow: hidden;
  }
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #DB0011;
  color: white;
  padding: 0 20px;
  height: 60px;
}

.logo-text {
  font-size: 20px;
  font-weight: bold;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.language-trigger, .user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: white;
}

.username {
  font-size: 14px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar {
  background-color: #fff;
  border-right: 1px solid #e6e6e6;
  transition: width 0.3s ease;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;

  .el-menu {
    flex: 1;
    border-right: none;
    overflow-y: auto;
  }
}

.collapse-btn {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-top: 1px solid #e6e6e6;
  flex-shrink: 0;

  &:hover {
    background-color: #f5f7fa;
  }
}

.main-content {
  background-color: #f5f7fa;
  padding: 0;
  overflow: auto;
  flex: 1;
  width: 100%;
}
</style>
