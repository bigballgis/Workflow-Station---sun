<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ArrowDown,
  Back,
  CaretBottom,
  CaretTop,
  Close,
  Filter,
  Right,
  Sort,
} from '@element-plus/icons-vue'
import type { ListColumnMeta } from './columnMeta'
import { listHeaderMenuItems, sortLabelKeys, type ListHeaderCommand } from './listHeaderMenu'
import ColumnResizeHandle from './ColumnResizeHandle.vue'

const props = withDefaults(
  defineProps<{
    column: ListColumnMeta
    sort?: 'ASC' | 'DESC' | null
    filtered?: boolean
    /** Current column width; pass null to hide the resize handle. */
    width?: number | null
    showMove?: boolean
    canMoveLeft?: boolean
    canMoveRight?: boolean
  }>(),
  {
    sort: null,
    filtered: false,
    width: null,
    showMove: false,
    canMoveLeft: false,
    canMoveRight: false,
  },
)

const emit = defineEmits<{
  'sort-change': [direction: 'ASC' | 'DESC']
  'clear-sort': []
  'filter-open': []
  'clear-filter': []
  move: [direction: 'left' | 'right']
  'width-change': [width: number]
  'width-commit': []
}>()

const { t } = useI18n()

const menuItems = computed(() =>
  listHeaderMenuItems(props.column, {
    sort: props.sort,
    filtered: props.filtered,
    showMove: props.showMove,
    canMoveLeft: props.canMoveLeft,
    canMoveRight: props.canMoveRight,
  }),
)

const COMMAND_ICONS: Record<ListHeaderCommand, Component> = {
  sortAsc: CaretTop,
  sortDesc: CaretBottom,
  clearSort: Sort,
  filter: Filter,
  clearFilter: Close,
  moveLeft: Back,
  moveRight: Right,
}

const hasActiveState = computed(() => props.filtered || !!props.sort)

// A column that declares no capability (display-only payload columns) has no menu at all;
// rendering the dropdown anyway would open an empty popper on click.
const hasMenu = computed(() => menuItems.value.length > 0)

function onCommand(command: ListHeaderCommand) {
  if (command === 'sortAsc') emit('sort-change', 'ASC')
  else if (command === 'sortDesc') emit('sort-change', 'DESC')
  else if (command === 'clearSort') emit('clear-sort')
  else if (command === 'filter') emit('filter-open')
  else if (command === 'clearFilter') emit('clear-filter')
  else if (command === 'moveLeft') emit('move', 'left')
  else emit('move', 'right')
}
</script>

<template>
  <div class="list-col-header">
    <span
      v-if="!hasMenu"
      class="list-col-label list-col-plain"
    >{{ column.label }}</span>
    <el-dropdown
      v-else
      class="list-col-dropdown"
      trigger="click"
      @command="onCommand"
    >
      <span
        class="list-col-trigger"
        :class="{ 'is-active-state': hasActiveState }"
        @click.stop
      >
        <span class="list-col-label">{{ column.label }}</span>
        <span
          v-if="hasActiveState"
          class="list-col-state"
          aria-hidden="true"
        >
          <el-icon
            v-if="sort === 'ASC'"
            class="state-icon"
            :title="t(sortLabelKeys(column.kind).asc)"
          ><CaretTop /></el-icon>
          <el-icon
            v-else-if="sort === 'DESC'"
            class="state-icon"
            :title="t(sortLabelKeys(column.kind).desc)"
          ><CaretBottom /></el-icon>
          <el-icon
            v-if="filtered"
            class="state-icon is-filter"
            :title="t('sharedList.filterBy')"
          ><Filter /></el-icon>
        </span>
        <el-icon class="list-col-caret"><ArrowDown /></el-icon>
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
              type="danger"
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
/* Compact header metrics — same as PR #107 / DW designer-list, not an inflated cell. */
.list-col-header {
  position: static;
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  min-height: 23px;
  box-sizing: border-box;
  padding-right: 12px;
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

  &.is-active-state .list-col-label {
    color: var(--hsbc-red, var(--el-color-primary));
    font-weight: 600;
  }
}

.list-col-label {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-col-plain {
  cursor: default;
}

.list-col-state {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.state-icon {
  font-size: 13px;
  color: var(--hsbc-red, var(--el-color-primary));

  &.is-filter {
    font-size: 14px;
  }
}

.list-col-caret {
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

.list-col-active-tag {
  margin-left: auto;
  padding: 0 4px;
  height: 16px;
  line-height: 16px;
}
</style>
