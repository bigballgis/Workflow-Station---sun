<template>
  <div class="owner-placeholder-widget" @click.stop="onWidgetClick">
    <div class="owner-mock-lookup is-readonly">
      <span class="owner-mock-tag">
        <span class="owner-mock-head" aria-hidden="true">
          <el-icon :size="12"><UserFilled /></el-icon>
        </span>
        <span class="owner-mock-text">{{ t('form.ownerPlaceholder') }}</span>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { UserFilled } from '@element-plus/icons-vue'

const props = defineProps<{
  ownerConfig?: string
  formCreateInject?: unknown
}>()
void props.ownerConfig

const { t } = useI18n()

function onWidgetClick(e: MouseEvent) {
  e.stopPropagation()
  const el = e.currentTarget as HTMLElement
  const dragTool = el.closest('._fd-drag-tool') as HTMLElement | null
  if (dragTool) {
    const clickEvent = new MouseEvent('click', { bubbles: false, cancelable: true })
    dragTool.dispatchEvent(clickEvent)
  }
}
</script>

<style scoped>
.owner-placeholder-widget {
  cursor: default;
  user-select: none;
  width: 100%;
}

.owner-mock-lookup {
  display: flex;
  align-items: center;
  min-height: 32px;
  padding: 4px 8px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: var(--ws-radius-input, 8px);
  background: var(--el-disabled-bg-color, #f5f7fa);
}

.owner-mock-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  padding: 0 8px 0 4px;
  border-radius: 4px;
  background: #f0f2f5;
}

.owner-mock-head {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--el-color-primary, #db0011);
  color: #fff;
}

.owner-mock-text {
  color: #909399;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
