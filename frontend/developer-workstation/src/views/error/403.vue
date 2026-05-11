<template>
  <div class="error-page">
    <div class="error-content">
      <div class="error-code">
        403
      </div>
      <div class="error-message">
        {{ t('error.forbidden') }}
      </div>
      <div class="error-description">
        {{ t('error.forbiddenDesc') }}
      </div>
      <div class="error-actions">
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
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()

const goBack = () => {
  router.back()
}

const goHome = () => {
  router.push('/function-units')
}
</script>

<style scoped lang="scss">
.error-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.error-content {
  text-align: center;
  padding: 40px;
}

.error-code {
  font-size: 100px;
  font-weight: bold;
  color: #db0011;
  line-height: 1;
  margin-bottom: 20px;
}

.error-message {
  font-size: 22px;
  color: #303133;
  margin-bottom: 10px;
}

.error-description {
  font-size: 14px;
  color: #909399;
  margin-bottom: 30px;
}

.error-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}
</style>
