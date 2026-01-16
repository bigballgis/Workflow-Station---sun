<template>
  <el-container class="main-layout">
    <el-header class="header">
      <div class="logo">
        <span class="logo-text">{{ t('app.name') }}</span>
      </div>
      <div class="header-right">
        <UserProfileDropdown />
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
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Folder, Picture, DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import UserProfileDropdown from '@/components/UserProfileDropdown.vue'

const route = useRoute()
const { t } = useI18n()

const activeMenu = computed(() => route.path)

// Sidebar collapse state
const isCollapsed = ref(false)
const sidebarWidth = computed(() => isCollapsed.value ? '64px' : '240px')

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

onMounted(() => {
  initSidebarState()
})
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
