<script setup lang="ts">
import { computed } from 'vue'
import { View as ViewIcon } from '@element-plus/icons-vue'
import { sanitizeSvgIcon } from '@/utils/sanitizeSvgIcon'

const props = defineProps<{
  iconSvg?: string | null
}>()

const sanitized = computed(() => {
  const raw = props.iconSvg?.trim()
  return raw ? sanitizeSvgIcon(raw) : ''
})
</script>

<template>
  <span
    class="fu-menu-icon"
    aria-hidden="true"
  >
    <span
      v-if="sanitized"
      class="fu-menu-icon-svg"
      v-html="sanitized"
    />
    <el-icon v-else>
      <ViewIcon />
    </el-icon>
  </span>
</template>

<style scoped>
.fu-menu-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  margin-right: 8px;
  flex-shrink: 0;
  overflow: hidden;
}
.fu-menu-icon-svg,
.fu-menu-icon :deep(svg) {
  display: block;
  width: 18px;
  height: 18px;
}
</style>
