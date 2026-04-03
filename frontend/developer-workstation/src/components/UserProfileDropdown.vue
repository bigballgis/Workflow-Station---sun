<template>
  <el-dropdown @command="handleCommand" trigger="click" :hide-on-click="false">
    <div class="user-info">
      <el-avatar :size="32">{{ userName.charAt(0) }}</el-avatar>
      <span class="user-name">{{ userName }}</span>
      <el-icon><ArrowDown /></el-icon>
    </div>
    <template #dropdown>
      <el-dropdown-menu class="user-profile-dropdown">
        <div class="profile-header">
          <el-avatar :size="48">{{ userName.charAt(0) }}</el-avatar>
          <div class="profile-info">
            <div class="profile-name">{{ userName }}</div>
            <div class="profile-email">{{ userEmail }}</div>
            <div class="profile-studio-hint">{{ t('profile.studioDropdownHint') }}</div>
          </div>
        </div>

        <el-divider />

        <el-dropdown-item command="profile">
          <el-icon><User /></el-icon>
          {{ t('profile.title') }}
        </el-dropdown-item>
        <el-dropdown-item command="logout" divided>
          <el-icon><SwitchButton /></el-icon>
          {{ t('common.logout') }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowDown, User, SwitchButton } from '@element-plus/icons-vue'
import { logout as authLogout, clearAuth, getUser } from '@/api/auth'

const { t } = useI18n()
const router = useRouter()

const currentUser = computed(() => getUser())
const userName = computed(() => currentUser.value?.displayName || currentUser.value?.username || 'Developer')
const userEmail = computed(() => currentUser.value?.email || '')

const handleCommand = async (command: string) => {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    try {
      await authLogout()
      ElMessage.success(t('common.logoutSuccess'))
    } catch (error) {
      console.error('Logout API error:', error)
    } finally {
      clearAuth()
      router.push('/login')
    }
  }
}
</script>

<style lang="scss" scoped>
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  color: white;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
  }

  .user-name {
    font-size: 14px;
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.user-profile-dropdown {
  width: 300px;
  padding: 0;

  .profile-header {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 16px;

    .profile-info {
      flex: 1;

      .profile-name {
        font-size: 16px;
        font-weight: 500;
        color: var(--el-text-color-primary);
      }

      .profile-email {
        font-size: 12px;
        color: var(--el-text-color-secondary);
        margin-top: 4px;
      }

      .profile-studio-hint {
        font-size: 11px;
        color: var(--el-text-color-placeholder);
        margin-top: 8px;
        line-height: 1.4;
      }
    }
  }

  :deep(.el-divider) {
    margin: 8px 0;
  }

  :deep(.el-dropdown-menu__item) {
    padding: 8px 16px;
  }
}
</style>
