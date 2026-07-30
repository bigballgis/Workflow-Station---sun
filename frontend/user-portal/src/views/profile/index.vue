<template>
  <div class="profile-container">
    <el-card class="profile-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('profile.title') }}</span>
        </div>
      </template>

      <div
        v-loading="loading"
        class="profile-content"
      >
        <div class="avatar-section">
          <el-avatar :size="100" :src="userInfo?.hasAvatar ? `${AUTH_BASE_URL}/me/avatar` : undefined">
            {{ (userInfo?.displayName || userInfo?.username || 'U').charAt(0).toUpperCase() }}
          </el-avatar>
          <h2>{{ userInfo?.displayName || userInfo?.username || t('user.username') }}</h2>
          <p class="workspace-line">
            {{ workspaceLine }}
          </p>
        </div>

        <el-divider />

        <h4 class="subsection-title">
          {{ t('profile.sectionAccount') }}
        </h4>
        <el-descriptions
          :column="2"
          border
          class="subsection-block"
        >
          <el-descriptions-item :label="t('user.username')">
            {{ userInfo?.username || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.email')">
            {{ userInfo?.email || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('profile.accountId')">
            {{ userInfo?.userId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('profile.interfaceLanguage')">
            {{ languageLabel }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <h4 class="subsection-title">
          {{ t('profile.sectionWorkspace') }}
        </h4>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="hint-alert"
        >
          {{ t('profile.workspaceHint') }}
        </el-alert>
        <el-descriptions
          :column="1"
          border
          class="subsection-block"
        >
          <el-descriptions-item :label="t('profile.workspaceCurrent')">
            <template v-if="workspaceContextText">
              {{ workspaceContextText }}
            </template>
            <span
              v-else
              class="empty-text"
            >{{ t('profile.noWorkspaceSelected') }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <h4 class="subsection-title">
          {{ t('profile.sectionMembership') }}
        </h4>
        <el-alert
          type="success"
          :closable="false"
          show-icon
          class="hint-alert"
        >
          {{ t('profile.membershipRolesHint') }}
        </el-alert>
        <el-descriptions
          :column="1"
          border
          class="subsection-block"
        >
          <el-descriptions-item :label="t('profile.sectionBuRolePairs')">
            <ul
              v-if="buBoundedRoles.length"
              class="ubr-list"
            >
              <li
                v-for="(row, idx) in buBoundedRoles"
                :key="`${row.role?.id}-${idx}`"
              >
                {{ formatUbrLine(row) }}
              </li>
            </ul>
            <span
              v-else
              class="empty-text"
            >{{ t('profile.noBuRoleAssignments') }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { getCurrentUser, getUser, saveUser, type UserInfo, AUTH_BASE_URL } from '@/api/auth'
import { permissionApi } from '@/api/permission'
import { parseMyPermissionViewPayload, type PortalBuBoundedRow } from '@/utils/myPermissionView'
import { languageLabelFor } from '@/utils/languageLabel'

const { t, locale } = useI18n()

const loading = ref(false)
const userInfo = ref<UserInfo | null>(null)
const buBoundedRoles = ref<PortalBuBoundedRow[]>([])

const formatUbrLine = (row: PortalBuBoundedRow) => {
  const bu = row.activatedBusinessUnits?.[0]
  const buName = bu?.name || '—'
  const roleName = row.role?.name || '—'
  return `${buName} · ${roleName}`
}
const languageLabel = computed(() => languageLabelFor(userInfo.value?.language, String(locale.value)))

const workspaceContextText = computed(() => {
  const u = userInfo.value
  if (!u?.activeBusinessUnitName && !u?.activeRoleName) return ''
  const bu = u.activeBusinessUnitName || '—'
  const r = u.activeRoleName || '—'
  return `${bu} · ${r}`
})

const workspaceLine = computed(() => workspaceContextText.value || t('profile.noWorkspaceSelected'))

const loadUserInfo = async () => {
  loading.value = true
  try {
    let u: UserInfo | null = null
    try {
      u = await getCurrentUser()
      saveUser(u)
    } catch {
      u = getUser()
    }
    userInfo.value = u

    const response = (await permissionApi.getMyPermissionView()) as { data?: Record<string, unknown> } & Record<string, unknown>
    const data = (response.data || response) as Record<string, unknown>
    const lists = parseMyPermissionViewPayload(data)
    buBoundedRoles.value = lists.buBoundedRoles
  } catch (error) {
    console.error('Failed to load user info:', error)
    userInfo.value = getUser()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped>
.profile-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.profile-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.profile-content {
  min-height: 200px;
}

.avatar-section {
  text-align: center;
  padding: 20px 0;
}

.avatar-section h2 {
  margin: 15px 0 5px;
  font-size: 20px;
}

.workspace-line {
  color: #606266;
  font-size: 14px;
  margin: 0;
}

.subsection-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.subsection-block {
  margin-bottom: 8px;
}

.hint-alert {
  margin-bottom: 12px;
}

.item-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.empty-text {
  color: #909399;
  font-size: 12px;
}

.ubr-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.desc-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  line-height: 1.4;
}
</style>
