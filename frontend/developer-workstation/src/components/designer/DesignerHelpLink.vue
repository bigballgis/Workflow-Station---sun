<!--
  Admin counterpart: frontend/admin-center/src/components/relation-table/DesignerHelpLink.vue
-->
<template>
  <el-tooltip
    :content="href"
    placement="bottom"
    :show-after="200"
  >
    <a
      class="designer-help-link"
      :data-testid="testId"
      :href="href"
      target="_blank"
      rel="noopener noreferrer"
      :aria-label="ariaLabel"
      @click.stop
    >
      <el-icon><QuestionFilled /></el-icon>
    </a>
  </el-tooltip>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
import { helpGuideAbsoluteUrl } from '@/utils/computedFieldGuide'

const props = defineProps<{
  /** Help article path, e.g. `/email-send#connection` or `/computed-fields`. */
  path: string
  ariaLabel: string
  testId?: string
}>()

const href = computed(() => helpGuideAbsoluteUrl(props.path))
</script>

<style scoped>
.designer-help-link {
  display: inline-flex;
  align-items: center;
  color: var(--el-color-primary);
  font-size: 16px;
  line-height: 1;
  flex-shrink: 0;
}

.designer-help-link:hover,
.designer-help-link:focus-visible {
  color: var(--el-color-primary-light-3);
}
</style>
