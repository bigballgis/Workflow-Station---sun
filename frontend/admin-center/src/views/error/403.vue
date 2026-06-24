<template>
  <div class="forbidden-page">
    <header class="forbidden-header">
      <div class="brand">
        <el-icon class="brand-icon">
          <Lock />
        </el-icon>
        <span class="brand-name">{{ t('app.name') }}</span>
      </div>
    </header>

    <main class="forbidden-main">
      <div class="forbidden-content">
        <div class="forbidden-code">
          403
        </div>
        <div class="forbidden-message">
          {{ t('error.forbidden') }}
        </div>
        <div class="forbidden-description">
          {{ t('error.forbiddenDesc') }}
        </div>
        <div class="forbidden-actions">
          <el-button
            type="primary"
            @click="goBack"
          >
            {{ t('error.goBack') }}
          </el-button>
          <el-button @click="goHome">
            {{ t('error.backHome') }}
          </el-button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { Lock } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()

const goBack = () => {
  router.back()
}

const goHome = () => {
  router.push('/dashboard')
}
</script>

<style scoped lang="scss">
.forbidden-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.forbidden-header {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  color: #fff;
  background: linear-gradient(135deg, #db0011 0%, #8b0000 100%);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.brand-icon {
  flex: 0 0 auto;
  font-size: 24px;
}

.brand-name {
  overflow: hidden;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.forbidden-main {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 60px);
  padding: 40px 20px;
}

.forbidden-content {
  text-align: center;
  padding: 40px;
}

.forbidden-code {
  color: #db0011;
  font-size: 100px;
  font-weight: 700;
  line-height: 1;
  margin-bottom: 20px;
}

.forbidden-message {
  color: #303133;
  font-size: 22px;
  margin-bottom: 10px;
}

.forbidden-description {
  color: #909399;
  font-size: 14px;
  margin-bottom: 30px;
}

.forbidden-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

@media (max-width: 640px) {
  .forbidden-content {
    width: 100%;
    padding: 32px 16px;
  }

  .forbidden-code {
    font-size: 76px;
  }

  .forbidden-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>