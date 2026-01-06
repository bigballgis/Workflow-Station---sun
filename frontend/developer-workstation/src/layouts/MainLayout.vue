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
      <el-aside width="240px" class="sidebar">
        <el-menu :default-active="activeMenu" router>
          <el-menu-item index="/function-units">
            <el-icon><Folder /></el-icon>
            <span>{{ $t('functionUnit.title') }}</span>
          </el-menu-item>
          <el-menu-item index="/icons">
            <el-icon><Picture /></el-icon>
            <span>{{ $t('icon.title') }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Folder, Picture, Connection } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const { locale } = useI18n()

const activeMenu = computed(() => route.path)

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

function handleLogout() {
  localStorage.removeItem('token')
  router.push('/login')
}
</script>

<style lang="scss" scoped>
.main-layout {
  height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #DB0011;
  color: white;
  padding: 0 20px;
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

.sidebar {
  background-color: #fff;
  border-right: 1px solid #e6e6e6;
}

.main-content {
  background-color: #f5f7fa;
  padding: 20px;
}
</style>
