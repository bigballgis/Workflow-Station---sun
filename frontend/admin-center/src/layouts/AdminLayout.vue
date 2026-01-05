<template>
  <el-container class="admin-layout">
    <el-aside :width="isCollapse ? '64px' : '220px'">
      <div class="logo">
        <span v-if="!isCollapse">管理员中心</span>
        <span v-else>AC</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
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
    </el-aside>
    
    <el-container>
      <el-header>
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleLanguage">
            <span class="lang-btn">
              <el-icon><Globe /></el-icon>
              {{ currentLang }}
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
            <span class="user-info">
              <el-avatar :size="32" icon="UserFilled" />
              <span class="username">Admin</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>个人设置</el-dropdown-item>
                <el-dropdown-item divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const { t, locale } = useI18n()

const isCollapse = ref(false)
const activeMenu = computed(() => route.path)

const langMap: Record<string, string> = { 'zh-CN': '简体中文', 'zh-TW': '繁體中文', 'en': 'English' }
const currentLang = computed(() => langMap[locale.value] || '简体中文')

const handleLanguage = (lang: string) => {
  locale.value = lang
  localStorage.setItem('locale', lang)
}
</script>

<style scoped lang="scss">
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  background-color: #263445;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.lang-btn, .user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.username {
  margin-left: 8px;
}
</style>
