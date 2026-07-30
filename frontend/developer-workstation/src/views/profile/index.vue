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
        </div>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="studio-alert"
        >
          {{ t('profile.studioIntro') }}
        </el-alert>

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
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { getUser, AUTH_BASE_URL } from '@/api/auth'
import { languageLabelFor } from '@/utils/languageLabel'

const { t, locale } = useI18n()

interface UserInfo {
  userId?: string
  username?: string
  displayName?: string
  email?: string
  language?: string
}

const loading = ref(false)
const userInfo = ref<UserInfo | null>(null)

const languageLabel = computed(() => languageLabelFor(userInfo.value?.language, String(locale.value)))

const loadUserInfo = async () => {
  loading.value = true
  try {
    const user = getUser()
    if (user) {
      userInfo.value = user
    }
  } catch (error) {
    console.error('Failed to load user info:', error)
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

.studio-alert {
  margin-bottom: 8px;
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
</style>
