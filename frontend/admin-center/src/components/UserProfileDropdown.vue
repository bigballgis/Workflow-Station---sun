<template>
  <el-dropdown
    trigger="click"
    :hide-on-click="false"
    @command="handleCommand"
  >
    <div class="user-info">
      <el-avatar :size="32">
        {{ userName.charAt(0) }}
      </el-avatar>
      <span class="user-name">{{ userName }}</span>
      <el-icon><ArrowDown /></el-icon>
    </div>
    <template #dropdown>
      <el-dropdown-menu class="user-profile-dropdown">
        <!-- User Basic Info -->
        <div class="profile-header">
          <el-avatar :size="48">
            {{ userName.charAt(0) }}
          </el-avatar>
          <div class="profile-info">
            <div class="profile-name">
              {{ userName }}
            </div>
            <div class="profile-email">
              {{ userEmail }}
            </div>
            <div class="profile-context-hint">
              {{ t('profile.adminDropdownSubtitle') }}
            </div>
          </div>
        </div>
        
        <el-divider />
        
        <!-- 管理端不展示 BU：管理员关注系统级能力与权限，BU/workspace 信息在门户侧展示 -->
        
        <!-- Virtual Groups -->
        <div class="profile-section">
          <div class="section-title">
            <el-icon><Connection /></el-icon>
            {{ t('profile.virtualGroups') }}
          </div>
          <div
            v-if="loading"
            class="section-loading"
          >
            <el-icon class="is-loading">
              <Loading />
            </el-icon>
          </div>
          <div
            v-else-if="virtualGroups.length === 0"
            class="section-empty"
          >
            {{ t('profile.noVirtualGroups') }}
          </div>
          <div
            v-else
            class="section-content"
          >
            <el-tag
              v-for="vg in virtualGroups"
              :key="vg.groupId"
              size="small"
              type="success"
              class="item-tag"
            >
              {{ vg.groupName }}
            </el-tag>
          </div>
        </div>
        
        <!-- Roles -->
        <div class="profile-section">
          <div class="section-title">
            <el-icon><Key /></el-icon>
            {{ t('profile.roles') }}
          </div>
          <div
            v-if="loading"
            class="section-loading"
          >
            <el-icon class="is-loading">
              <Loading />
            </el-icon>
          </div>
          <div
            v-else-if="roles.length === 0"
            class="section-empty"
          >
            {{ t('profile.noRoles') }}
          </div>
          <div
            v-else
            class="section-content"
          >
            <el-tag
              v-for="role in roles"
              :key="role.id"
              size="small"
              :type="getRoleTagType(role.type)"
              class="item-tag"
            >
              {{ role.name }}
            </el-tag>
          </div>
        </div>
        
        <el-divider />
        
        <!-- Actions -->
        <el-dropdown-item command="profile">
          <el-icon><User /></el-icon>
          {{ t('profile.title') }}
        </el-dropdown-item>
        <el-dropdown-item command="settings">
          <el-icon><Setting /></el-icon>
          {{ t('menu.config') }}
        </el-dropdown-item>
        <el-dropdown-item
          command="logout"
          divided
        >
          <el-icon><SwitchButton /></el-icon>
          {{ t('common.logout') }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowDown, Connection, Key, User, Setting, SwitchButton, Loading } from '@element-plus/icons-vue'
import { logout as authLogout, clearAuth, getUser } from '@/api/auth'
import { userApi } from '@/api/user'
import { redirectToUnifiedLogin } from '@/utils/sso'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const virtualGroups = ref<{ groupId: string; groupName: string }[]>([])
const roles = ref<{ id: string; name: string; type?: string }[]>([])

const currentUser = computed(() => getUser())
const userName = computed(() => currentUser.value?.displayName || currentUser.value?.username || 'Admin')
const userEmail = computed(() => currentUser.value?.email || '')

const getRoleTagType = (type?: string) => {
  if (type === 'BU_BOUNDED') return 'warning'
  if (type === 'BU_UNBOUNDED') return 'success'
  if (type === 'ADMIN') return 'danger'
  return 'primary'
}

const loadUserPermissions = async () => {
  const user = currentUser.value
  if (!user?.userId) {
    return
  }
  
  loading.value = true
  try {
    const [vgResult, rolesResult] = await Promise.all([
      userApi.getVirtualGroups(user.userId, 'ADMIN'),
      userApi.getRoles(user.userId, 'ADMIN')
    ])
    virtualGroups.value = vgResult || []
    roles.value = (rolesResult || []).map((r: any) => ({
      id: r.id,
      name: r.name,
      type: r.type
    }))
  } catch (e) {
    console.error('Failed to load user permissions:', e)
  } finally {
    loading.value = false
  }
}

const handleCommand = async (command: string) => {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'settings') {
    router.push('/config')
  } else if (command === 'logout') {
    try {
      await authLogout()
      ElMessage.success(t('common.logoutSuccess'))
    } catch (error) {
      console.error('Logout API error:', error)
    } finally {
      clearAuth()
      redirectToUnifiedLogin('admin')
    }
  }
}

onMounted(() => {
  loadUserPermissions()
})
</script>

<style lang="scss" scoped>
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 999px;
  color: #fff;

  &:hover {
    background: rgba(255, 255, 255, 0.12);
  }

  .user-name {
    font-size: 14px;
    font-weight: 500;
  }

  :deep(.el-avatar) {
    background: #fff;
    color: var(--primary-color, #db0011);
    font-weight: 600;
  }
}

.user-profile-dropdown {
  width: 320px;
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

      .profile-context-hint {
        font-size: 11px;
        color: var(--el-text-color-placeholder);
        margin-top: 8px;
        line-height: 1.35;
      }
    }
  }
  
  .profile-section {
    padding: 8px 16px;
    
    .section-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      font-weight: 500;
      color: var(--el-text-color-secondary);
      margin-bottom: 8px;
    }
    
    .section-loading {
      display: flex;
      justify-content: center;
      padding: 8px;
    }
    
    .section-empty {
      font-size: 12px;
      color: var(--el-text-color-placeholder);
      padding: 4px 0;
    }
    
    .section-content {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      
      .item-tag {
        max-width: 140px;
        overflow: hidden;
        text-overflow: ellipsis;
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
