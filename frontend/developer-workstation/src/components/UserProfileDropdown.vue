<template>
  <el-dropdown
    trigger="click"
    :hide-on-click="false"
    @command="handleCommand"
  >
    <div class="user-info">
      <el-avatar :size="32" :src="avatarSrc">
        {{ userName.charAt(0) }}
      </el-avatar>
      <span class="user-name">{{ userName }}</span>
      <el-icon><ArrowDown /></el-icon>
    </div>
    <template #dropdown>
      <el-dropdown-menu class="user-profile-dropdown">
        <div class="profile-header">
          <el-avatar :size="48" :src="avatarSrc">
            {{ userName.charAt(0) }}
          </el-avatar>
          <div class="profile-info">
            <div class="profile-name">
              {{ userName }}
            </div>
            <div class="profile-email">
              {{ userEmail }}
            </div>
            <div class="profile-studio-hint">
              {{ t('profile.studioDropdownHint') }}
            </div>
          </div>
        </div>

        <el-divider />

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
              type="warning"
              class="item-tag"
            >
              {{ role.name }}
            </el-tag>
          </div>
        </div>

        <el-divider />

        <el-dropdown-item command="profile">
          <el-icon><User /></el-icon>
          {{ t('profile.title') }}
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
import { ArrowDown, User, SwitchButton, Connection, Key, Loading } from '@element-plus/icons-vue'
import { logout as authLogout, clearAuth, getUser, AUTH_BASE_URL } from '@/api/auth'
import { userApi } from '@/api/user'
import { redirectToUnifiedLogin } from '@/utils/sso'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const virtualGroups = ref<{ groupId: string; groupName: string }[]>([])
const roles = ref<{ id: string; name: string; type?: string }[]>([])

const currentUser = computed(() => getUser())
const userName = computed(() => currentUser.value?.displayName || currentUser.value?.username || 'Developer')
const userEmail = computed(() => currentUser.value?.email || '')
const avatarSrc = computed(() => currentUser.value?.hasAvatar ? `${AUTH_BASE_URL}/me/avatar` : undefined)

const loadUserPermissions = async () => {
  const user = currentUser.value
  if (!user?.userId) {
    return
  }
  loading.value = true
  try {
    const [vgResult, rolesResult] = await Promise.all([
      userApi.getVirtualGroups(user.userId, 'DEVELOPER'),
      userApi.getRoles(user.userId, 'DEVELOPER')
    ])
    virtualGroups.value = vgResult || []
    roles.value = (rolesResult || []).map((r: { id: string; name: string; type?: string }) => ({
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
  } else if (command === 'logout') {
    try {
      await authLogout()
      ElMessage.success(t('common.logoutSuccess'))
    } catch (error) {
      console.error('Logout API error:', error)
    } finally {
      clearAuth()
      redirectToUnifiedLogin('developer-workstation')
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
  color: white;

  &:hover {
    background: rgba(255, 255, 255, 0.12);
  }

  .user-name {
    font-size: 14px;
    font-weight: 500;
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  // 红顶栏上的头像：白底红字（与 admin-center 一致）
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

      .profile-studio-hint {
        font-size: 11px;
        color: var(--el-text-color-placeholder);
        margin-top: 8px;
        line-height: 1.4;
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
