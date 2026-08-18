<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ArrowDown,
  Back,
  CaretBottom,
  CaretTop,
  Close,
  DCaret,
  Filter,
  Grid,
  Right,
  Sort,
} from '@element-plus/icons-vue'
import type { ListColumnMeta } from './columnMeta'
import { listHeaderMenuItems, type ListHeaderCommand } from './listHeaderMenu'
import ColumnResizeHandle from './ColumnResizeHandle.vue'

const props = withDefaults(
  defineProps<{
    column: ListColumnMeta
    sort?: 'ASC' | 'DESC' | null
    grouped?: boolean
    filtered?: boolean
    /** Current column width; pass null to hide the resize handle. */
    width?: number | null
    /** Offer an exact-width menu entry (the host owns the dialog). */
    showWidth?: boolean
    showMove?: boolean
    canMoveLeft?: boolean
    canMoveRight?: boolean
  }>(),
  {
    sort: null,
    grouped: false,
    filtered: false,
    width: null,
    showWidth: false,
    showMove: false,
    canMoveLeft: false,
    canMoveRight: false,
  },
)

const emit = defineEmits<{
  'sort-change': [direction: 'ASC' | 'DESC']
  'clear-sort': []
  'group-change': [grouped: boolean]
  'filter-open': []
  'clear-filter': []
  'width-open': []
  move: [direction: 'left' | 'right']
  'width-change': [width: number]
  'width-commit': []
}>()

const { t } = useI18n()

const menuItems = computed(() =>
  listHeaderMenuItems(props.column, {
    sort: props.sort,
    grouped: props.grouped,
    filtered: props.filtered,
    showWidth: props.showWidth,
    showMove: props.showMove,
    canMoveLeft: props.canMoveLeft,
    canMoveRight: props.canMoveRight,
  }),
)

const COMMAND_ICONS: Record<ListHeaderCommand, Component> = {
  sortAsc: CaretTop,
  sortDesc: CaretBottom,
  clearSort: Sort,
  group: Grid,
  filter: Filter,
  clearFilter: Close,
  columnWidth: DCaret,
  moveLeft: Back,
  moveRight: Right,
}

const hasActiveState = computed(() => props.filtered || props.grouped || !!props.sort)

function onCommand(command: ListHeaderCommand) {
  if (command === 'sortAsc') emit('sort-change', 'ASC')
  else if (command === 'sortDesc') emit('sort-change', 'DESC')
  else if (command === 'clearSort') emit('clear-sort')
  else if (command === 'group') emit('group-change', !props.grouped)
  else if (command === 'filter') emit('filter-open')
  else if (command === 'clearFilter') emit('clear-filter')
  else if (command === 'columnWidth') emit('width-open')
  else if (command === 'moveLeft') emit('move', 'left')
  else emit('move', 'right')
}
</script>

<template>
  <div class="list-col-header">
    <el-dropdown
      class="list-col-dropdown"
      trigger="click"
      @command="onCommand"
    >
      <span
        class="list-col-trigger"
        @click.stop
      >
        <span class="list-col-label">{{ column.label }}</span>
        <el-icon
          class="list-col-caret"
          :class="{ 'is-active': hasActiveState }"
        ><ArrowDown /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu class="list-col-menu">
          <el-dropdown-item
            v-for="item in menuItems"
            :key="item.command"
            :command="item.command"
            :divided="item.divided"
            :disabled="item.disabled"
          >
            <el-icon><component :is="COMMAND_ICONS[item.command]" /></el-icon>
            <span>{{ t(item.labelKey) }}</span>
            <el-tag
              v-if="item.activeDot"
              size="small"
              type="info"
              class="list-col-active-tag"
            >
              ●
            </el-tag>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    <ColumnResizeHandle
      v-if="width !== null"
      :initial-width="width"
      @resize="(w: number) => emit('width-change', w)"
      @resize-end="emit('width-commit')"
    />
  </div>
</template>

<style scoped lang="scss">
.list-col-header {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  position: relative;
}

.list-col-dropdown {
  display: block;
  flex: 1 1 0;
  width: 0;
  min-width: 0;
}

.list-col-trigger {
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

.list-col-label {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-col-caret {
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
  min-width: 180px;
}

.list-col-active-tag {
  margin-left: auto;
  padding: 0 4px;
  height: 16px;
  line-height: 16px;
}
</style>
