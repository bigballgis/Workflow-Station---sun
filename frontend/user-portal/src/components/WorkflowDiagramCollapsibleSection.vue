<template>
  <div class="section workflow-section">
    <div
      class="section-header section-header--collapsible"
      role="button"
      tabindex="0"
      :aria-expanded="expanded"
      :aria-label="expanded ? t('common.collapse') : t('common.expand')"
      @click="toggle"
      @keydown="onHeaderKeydown"
    >
      <el-icon><Share /></el-icon>
      <span>{{ title }}</span>
      <slot name="badge" />
      <el-icon class="collapse-arrow">
        <ArrowDown v-if="expanded" />
        <ArrowRight v-else />
      </el-icon>
    </div>
    <div
      v-if="expanded"
      class="section-content"
    >
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowDown, ArrowRight, Share } from '@element-plus/icons-vue'

defineProps<{
  title: string
}>()

const { t } = useI18n()
/** Always start expanded; user may collapse manually. */
const expanded = ref(true)

function toggle(): void {
  expanded.value = !expanded.value
}

function onHeaderKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' && event.key !== ' ') return
  event.preventDefault()
  toggle()
}
</script>

<style scoped lang="scss">
.section {
  background: white;
  border-radius: 8px;
  border: 1px solid var(--border-color, #e4e7ed);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px;
  background: #fafafa;
  border-bottom: 1px solid var(--border-color, #e4e7ed);
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);

  .el-icon {
    color: var(--hsbc-red, #db0011);
  }
}

.section-header--collapsible {
  cursor: pointer;
  user-select: none;

  &:not([aria-expanded='true']) {
    border-bottom: none;
  }

  &:focus-visible {
    outline: 2px solid var(--hsbc-red, #db0011);
    outline-offset: -2px;
  }

  /* Keep title/badge left; arrow flush right */
  .collapse-arrow {
    margin-left: auto;
    color: var(--text-secondary, #909399);
  }
}

.section-content {
  padding: 20px;
  min-height: 300px;
}
</style>
