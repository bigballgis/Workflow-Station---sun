<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CaretTop,
  CaretBottom,
  Grid,
  Filter,
  DCaret,
  Back,
  Right,
  ArrowDown,
} from '@element-plus/icons-vue'
import type { MainTableViewFieldColumn } from '@/api/mainTableView'

const props = defineProps<{
  column: MainTableViewFieldColumn
  canMoveLeft: boolean
  canMoveRight: boolean
  isGrouped: boolean
  hasFilter: boolean
}>()

const emit = defineEmits<{
  command: [action: string]
}>()

const { t } = useI18n()

const isDateLike = computed(() => {
  const name = props.column.fieldName.toLowerCase()
  return name.includes('time') || name.includes('date') || props.column.systemField
})

function onCommand(action: string) {
  emit('command', action)
}
</script>

<template>
  <el-dropdown
    class="col-header-dropdown"
    trigger="click"
    @command="onCommand"
  >
    <span
      class="col-header-trigger"
      @click.stop
    >
      <span class="col-header-label">{{ column.displayLabel }}</span>
      <el-icon class="col-header-caret"><ArrowDown /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu class="col-header-menu">
        <el-dropdown-item command="sortAsc">
          <el-icon><CaretTop /></el-icon>
          <span>{{ isDateLike ? t('mainTableView.colSortOlder') : t('mainTableView.colSortAsc') }}</span>
        </el-dropdown-item>
        <el-dropdown-item command="sortDesc">
          <el-icon><CaretBottom /></el-icon>
          <span>{{ isDateLike ? t('mainTableView.colSortNewer') : t('mainTableView.colSortDesc') }}</span>
        </el-dropdown-item>
        <el-dropdown-item
          divided
          command="groupBy"
        >
          <el-icon><Grid /></el-icon>
          <span>{{ isGrouped ? t('mainTableView.colUngroup') : t('mainTableView.colGroupBy') }}</span>
        </el-dropdown-item>
        <el-dropdown-item command="filterBy">
          <el-icon><Filter /></el-icon>
          <span>{{ t('mainTableView.colFilterBy') }}</span>
          <el-tag
            v-if="hasFilter"
            size="small"
            type="info"
            class="menu-active-tag"
          >
            ●
          </el-tag>
        </el-dropdown-item>
        <el-dropdown-item command="columnWidth">
          <el-icon><DCaret /></el-icon>
          <span>{{ t('mainTableView.colColumnWidth') }}</span>
        </el-dropdown-item>
        <el-dropdown-item
          divided
          command="moveLeft"
          :disabled="!canMoveLeft"
        >
          <el-icon><Back /></el-icon>
          <span>{{ t('mainTableView.colMoveLeft') }}</span>
        </el-dropdown-item>
        <el-dropdown-item
          command="moveRight"
          :disabled="!canMoveRight"
        >
          <el-icon><Right /></el-icon>
          <span>{{ t('mainTableView.colMoveRight') }}</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped lang="scss">
.col-header-dropdown {
  display: block;
  flex: 1 1 0;
  width: 0;
  min-width: 0;
}

.col-header-trigger {
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

.col-header-label {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-header-caret {
  font-size: 12px;
  flex-shrink: 0;
  opacity: 0.65;
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 200px;
}

.menu-active-tag {
  margin-left: auto;
  padding: 0 4px;
  height: 16px;
  line-height: 16px;
}
</style>
