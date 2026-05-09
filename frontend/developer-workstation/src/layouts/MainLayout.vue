<template>
  <el-container class="main-layout" direction="vertical">
    <el-header class="header">
      <div class="logo">
        <span class="logo-text">{{ t('app.name') }}</span>
      </div>
      <div class="header-right">
        <UserProfileDropdown />
      </div>
    </el-header>
    <el-main class="main-content">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import UserProfileDropdown from '@/components/UserProfileDropdown.vue'
import { getUser, getCurrentUser, saveUser, clearAuth } from '@/api/auth'
import { redirectToUnifiedLogin } from '@/utils/sso'

const { t } = useI18n()

// Get current user info
const currentUser = computed(() => getUser())

onMounted(async () => {
  // If user info doesn't exist, try to get from API
  if (!currentUser.value) {
    try {
      const user = await getCurrentUser()
      if (user) {
        saveUser(user)
      }
    } catch (error) {
      console.error('Failed to get current user:', error)
      // If failed, token may be invalid, clear auth info
      clearAuth()
      redirectToUnifiedLogin('developer-workstation')
    }
  }
})
</script>

<style lang="scss" scoped>
$header-gradient-start: #DB0011;
$header-gradient-end: #8B0000;

.main-layout {
  height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, $header-gradient-start 0%, $header-gradient-end 100%);
  color: white;
  padding: 0 20px;
  height: 60px;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.main-content {
  background-color: #f5f7fa;
  padding: 0;
  overflow: auto;
  flex: 1;
  min-height: 0;
}
</style>
