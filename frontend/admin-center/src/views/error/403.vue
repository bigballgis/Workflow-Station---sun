<template>
  <div class="forbidden-container">
    <div class="forbidden-content">
      <div class="forbidden-icon">🚫</div>
      <h1 class="forbidden-title">403</h1>
      <p class="forbidden-message">抱歉，您没有权限访问此页面</p>
      <p class="forbidden-hint">请联系管理员获取相应权限</p>
      <div class="forbidden-actions">
        <el-button type="primary" @click="goBack">返回上一页</el-button>
        <el-button @click="goHome">返回首页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goBack = () => {
  router.back()
}

const goHome = () => {
  router.push('/dashboard')
}
</script>

<style scoped lang="scss">
.forbidden-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.forbidden-content {
  text-align: center;
}

.forbidden-icon {
  font-size: 80px;
  margin-bottom: 20px;
}

.forbidden-title {
  font-size: 72px;
  font-weight: 700;
  color: #DB0011;
  margin: 0 0 16px 0;
}

.forbidden-message {
  font-size: 20px;
  color: #303133;
  margin: 0 0 8px 0;
}

.forbidden-hint {
  font-size: 14px;
  color: #909399;
  margin: 0 0 32px 0;
}

.forbidden-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}
</style>
