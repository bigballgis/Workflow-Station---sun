<template>
  <div v-if="visible" class="self-service-banner" role="status">
    <el-icon class="banner-icon"><InfoFilled /></el-icon>
    <div class="banner-text">
      <span class="banner-title">{{ t('portalSelfService.bannerTitle') }}</span>
      <span class="banner-desc">{{ t('portalSelfService.bannerDesc') }}</span>
    </div>
    <el-button type="primary" size="small" plain @click="goPermissions">
      {{ t('portalSelfService.goPermissions') }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { InfoFilled } from '@element-plus/icons-vue'
import { getStoredUser } from '@/api/auth'

const { t } = useI18n()
const router = useRouter()

const visible = computed(() => {
  const u = getStoredUser()
  return u?.portalAccessMode === 'PERMISSION_SELF_SERVICE_ONLY'
})

function goPermissions() {
  router.push('/permissions')
}
</script>

<style scoped lang="scss">
.self-service-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  background: linear-gradient(90deg, rgba(219, 0, 17, 0.08) 0%, rgba(219, 0, 17, 0.02) 100%);
  border-bottom: 1px solid rgba(219, 0, 17, 0.15);
  color: #333;
}

.banner-icon {
  font-size: 20px;
  color: var(--hsbc-red, #db0011);
  flex-shrink: 0;
}

.banner-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.banner-title {
  font-weight: 600;
  font-size: 14px;
}

.banner-desc {
  font-size: 13px;
  color: #666;
  line-height: 1.4;
}
</style>
