<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="blocking-progress"
      role="alert"
      aria-busy="true"
      aria-live="assertive"
      @keydown.esc.prevent
    >
      <div class="blocking-progress__top">
        <div class="blocking-progress__bar" aria-hidden="true">
          <i />
        </div>
        <span class="blocking-progress__top-text">{{ message }}</span>
      </div>
      <div
        class="blocking-progress__mask"
        @click.stop.prevent
        @mousedown.stop.prevent
      >
        <div class="blocking-progress__panel">
          <el-icon
            class="blocking-progress__spinner is-loading"
            :size="28"
          >
            <Loading />
          </el-icon>
          <p class="blocking-progress__title">
            {{ message }}
          </p>
          <p
            v-if="detail"
            class="blocking-progress__detail"
          >
            {{ detail }}
          </p>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'

defineProps<{
  visible: boolean
  message: string
  detail?: string
}>()
</script>

<style scoped lang="scss">
.blocking-progress {
  position: fixed;
  inset: 0;
  z-index: 5000;
  pointer-events: auto;
}

.blocking-progress__top {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 36px;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  box-shadow: 0 1px 4px rgb(0 0 0 / 8%);
}

.blocking-progress__bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  overflow: hidden;
  background: #e5e7eb;

  i {
    display: block;
    height: 100%;
    width: 28%;
    background: var(--el-color-primary, #409eff);
    border-radius: 1px;
    animation: blocking-progress-slide 1.2s ease-in-out infinite;
  }
}

.blocking-progress__top-text {
  position: relative;
  z-index: 1;
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.blocking-progress__mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(255 255 255 / 55%);
  backdrop-filter: blur(1px);
  cursor: wait;
}

.blocking-progress__panel {
  max-width: 420px;
  padding: 24px 28px;
  text-align: center;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgb(0 0 0 / 12%);
}

.blocking-progress__spinner {
  color: var(--el-color-primary, #409eff);
  margin-bottom: 12px;
}

.blocking-progress__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
}

.blocking-progress__detail {
  margin: 8px 0 0;
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

@keyframes blocking-progress-slide {
  0% {
    transform: translateX(-120%);
  }
  100% {
    transform: translateX(420%);
  }
}
</style>
