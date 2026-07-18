<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowDown, Filter, Close } from '@element-plus/icons-vue'
import DesignerListColumnResizeHandle from './DesignerListColumnResizeHandle.vue'

defineProps<{
  label: string
  hasFilter: boolean
  width: number
}>()

const emit = defineEmits<{
  filter: []
  clearFilter: []
  resize: [width: number]
  resizeEnd: []
}>()

const { t } = useI18n()

function onCommand(action: string) {
  if (action === 'filter') emit('filter')
  else if (action === 'clear') emit('clearFilter')
}
</script>

<template>
  <div class="dwl-col-header">
    <el-dropdown
      class="dwl-col-dropdown"
      trigger="click"
      @command="onCommand"
    >
      <span
        class="dwl-col-trigger"
        @click.stop
      >
        <span class="dwl-col-label">{{ label }}</span>
        <el-icon
          class="dwl-col-caret"
          :class="{ 'is-active': hasFilter }"
        ><ArrowDown /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu class="dwl-col-menu">
          <el-dropdown-item command="filter">
            <el-icon><Filter /></el-icon>
            <span>{{ t('designerList.filter') }}</span>
            <el-tag
              v-if="hasFilter"
              size="small"
              type="info"
              class="dwl-active-tag"
            >
              ●
            </el-tag>
          </el-dropdown-item>
          <el-dropdown-item
            v-if="hasFilter"
            command="clear"
          >
            <el-icon><Close /></el-icon>
            <span>{{ t('designerList.clearFilter') }}</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    <DesignerListColumnResizeHandle
      :initial-width="width"
      @resize="(w: number) => emit('resize', w)"
      @resize-end="emit('resizeEnd')"
    />
  </div>
</template>

<style scoped lang="scss">
.dwl-col-dropdown {
  display: block;
  flex: 1 1 0;
  width: 0;
  min-width: 0;
}

.dwl-col-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  cursor: pointer;
  user-select: none;

  &:hover {
    color: var(--el-color-primary);
  }
}

.dwl-col-label {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dwl-col-caret {
  font-size: 12px;
  flex-shrink: 0;
  opacity: 0.55;

  &.is-active {
    opacity: 1;
    color: var(--el-color-primary);
  }
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 160px;
}

.dwl-active-tag {
  margin-left: auto;
  padding: 0 4px;
  height: 16px;
  line-height: 16px;
}
</style>
