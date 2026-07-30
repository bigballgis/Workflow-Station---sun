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
        <!-- User Basic Info -->
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
            <div class="profile-workspace">
              {{ t('profile.dropdownWorkspace') }}：{{ workspaceSummary }}
            </div>
            <div class="profile-hint">
              {{ t('profile.dropdownOrgHint') }}
            </div>
          </div>
        </div>
        
        <el-divider />

        <!-- UBR：业务单元 — 角色（工作台） -->
        <div class="profile-section">
          <div class="section-title">
            <el-icon><Key /></el-icon>
            {{ t('profile.sectionBuRolePairs') }}
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
            v-else-if="buBoundedRoles.length === 0"
            class="section-empty"
          >
            {{ t('profile.noBuRoleAssignments') }}
          </div>
          <ul
            v-else
            class="ubr-lines"
          >
            <li
              v-for="(row, idx) in buBoundedRoles"
              :key="`${row.role?.id}-${idx}`"
              class="ubr-line"
            >
              {{ formatUbrLine(row) }}
            </li>
          </ul>
          <div class="section-hint-inline">
            {{ t('profile.sectionBuRolePairsHint') }}
          </div>
        </div>

        <!-- 门户顶栏下拉仅展示“工作台(U BR)”信息，避免信息噪音；完整权限/成员详情见 Profile/Permissions 页面 -->

        <el-divider />
        
        <!-- Actions -->
        <el-dropdown-item command="profile">
          <el-icon><User /></el-icon>
          {{ t('profile.title') }}
        </el-dropdown-item>
        <el-dropdown-item command="settings">
          <el-icon><Setting /></el-icon>
          {{ t('menu.settings') }}
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
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowDown, OfficeBuilding, Key, User, Setting, SwitchButton, Loading } from '@element-plus/icons-vue'
import { logout as authLogout, clearAuth, getCurrentUser, getUser, saveUser, AUTH_BASE_URL } from '@/api/auth'
import { permissionApi } from '@/api/permission'
import { parseMyPermissionViewPayload, type PortalBuBoundedRow } from '@/utils/myPermissionView'
import { redirectToUnifiedLogin } from '@/utils/sso'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const loading = ref(false)
const businessUnits = ref<{ id: string; name: string }[]>([])
const buBoundedRoles = ref<PortalBuBoundedRow[]>([])
const buUnboundedRoles = ref<{ id: string; name: string }[]>([])

const formatUbrLine = (row: PortalBuBoundedRow) => {
  const r = row.role
  const bu = row.activatedBusinessUnits?.[0]
  const buName = bu?.name || '—'
  const roleName = r?.name || '—'
  return `${buName} · ${roleName}`
}

/** localStorage 非响应式：用 ref + 路由切换时同步，避免从个人中心等页返回后顶栏仍显示旧用户/工作台 */
const portalUser = ref(getUser())

function syncPortalUserFromStorage() {
  portalUser.value = getUser()
}

watch(
  () => route.fullPath,
  () => {
    syncPortalUserFromStorage()
  }
)

const currentUser = computed(() => portalUser.value)
const userName = computed(() => currentUser.value?.displayName || currentUser.value?.username || 'User')
const userEmail = computed(() => currentUser.value?.email || '')
const avatarSrc = computed(() => currentUser.value?.hasAvatar ? `${AUTH_BASE_URL}/me/avatar` : undefined)
const workspaceSummary = computed(() => {
  const u = currentUser.value
  if (!u?.activeBusinessUnitName && !u?.activeRoleName) {
    return t('profile.noWorkspaceSelected')
  }
  const bu = u.activeBusinessUnitName || '—'
  const r = u.activeRoleName || '—'
  return `${bu} · ${r}`
})

const loadUserPermissions = async () => {
  loading.value = true
  try {
    const response = await permissionApi.getMyPermissionView() as { data?: Record<string, unknown> } & Record<string, unknown>
    const data = (response.data || response) as Record<string, unknown>
    const lists = parseMyPermissionViewPayload(data)
    businessUnits.value = lists.businessUnits
    buBoundedRoles.value = lists.buBoundedRoles
    buUnboundedRoles.value = lists.buUnboundedRoles
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
    router.push('/settings')
  } else if (command === 'logout') {
    try {
      await authLogout()
      ElMessage.success(t('common.logoutSuccess'))
    } catch (error) {
      console.error('Logout API error:', error)
    } finally {
      clearAuth()
      portalUser.value = null
      redirectToUnifiedLogin('portal')
    }
  }
}

onMounted(() => {
  syncPortalUserFromStorage()
  void (async () => {
    try {
      const fresh = await getCurrentUser()
      saveUser(fresh)
      portalUser.value = fresh
    } catch {
      // 保持 storage 缓存
    }
  })()
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
  }

  // 红顶栏上的头像：白底红字（与 admin-center 一致）
  :deep(.el-avatar) {
    background: #fff;
    color: var(--primary-color, #db0011);
    font-weight: 600;
  }
}

.user-profile-dropdown {
  width: 340px;
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

      .profile-workspace {
        font-size: 12px;
        color: var(--el-text-color-regular);
        margin-top: 8px;
        line-height: 1.4;
      }

      .profile-hint {
        font-size: 11px;
        color: var(--el-text-color-placeholder);
        margin-top: 6px;
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
        max-width: 200px;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }

    .section-hint-inline {
      font-size: 11px;
      color: var(--el-text-color-placeholder);
      margin-top: 6px;
      line-height: 1.35;
    }

    .ubr-lines {
      margin: 0;
      padding-left: 18px;
      font-size: 12px;
      color: var(--el-text-color-regular);
      line-height: 1.5;
    }

    .ubr-line {
      margin-bottom: 4px;
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
