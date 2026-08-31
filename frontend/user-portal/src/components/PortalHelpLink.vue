<!--
  DW counterpart: frontend/developer-workstation/src/components/designer/DesignerHelpLink.vue
  Admin counterpart: frontend/admin-center/src/components/relation-table/DesignerHelpLink.vue
-->
<template>
  <el-tooltip
    :content="href"
    placement="bottom"
    :show-after="200"
  >
    <a
      class="portal-help-link"
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
import { helpGuideAbsoluteUrl } from '@/utils/helpGuideUrl'

const props = defineProps<{
  /** Help article path, e.g. `/up-tasks-to-claim`. */
  path: string
  ariaLabel: string
  testId?: string
}>()

const href = computed(() => helpGuideAbsoluteUrl(props.path))
</script>

<style scoped>
.portal-help-link {
  display: inline-flex;
  align-items: center;
  color: var(--el-color-primary);
  font-size: 16px;
  line-height: 1;
  flex-shrink: 0;
}

.portal-help-link:hover,
.portal-help-link:focus-visible {
  color: var(--el-color-primary-light-3);
}
</style>
