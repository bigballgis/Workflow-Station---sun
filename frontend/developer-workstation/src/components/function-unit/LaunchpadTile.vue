<template>
  <el-tooltip
    placement="bottom"
    :show-after="250"
    :disabled="menuOpen"
    popper-class="launchpad-tile-tooltip"
  >
    <template #content>
      <!-- 磁贴名称会截断，气泡里始终给全名；描述缺省时明确说「暂无描述」而非静默 -->
      <div class="tip-name">
        {{ item.name }}
      </div>
      <div
        class="tip-desc"
        :class="{ 'tip-desc--empty': !item.description }"
      >
        {{ item.description || t('functionUnit.noDescription') }}
      </div>
    </template>
    <div
      class="launchpad-tile"
      @click="$emit('open', item)"
    >
      <div class="tile-icon">
        <IconPreview
          :icon-id="item.iconId"
          size="large"
        />
        <span
          class="status-dot"
          :class="`status-dot--${item.status.toLowerCase()}`"
          :title="statusLabel"
        />
        <el-dropdown
          trigger="click"
          class="tile-menu"
          popper-class="launchpad-dropdown-popper"
          @command="handleCommand"
          @visible-change="menuOpen = $event"
        >
          <button
            class="tile-menu-btn"
            :aria-label="t('common.operation')"
            @click.stop
          >
            <el-icon><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-if="permissions.canEdit()"
                command="settings"
              >
                <el-icon><Setting /></el-icon>{{ t('functionUnit.setting') }}
              </el-dropdown-item>
              <el-dropdown-item
                v-if="permissions.canClone()"
                command="clone"
              >
                <el-icon><CopyDocument /></el-icon>{{ t('functionUnit.clone') }}
              </el-dropdown-item>
              <el-dropdown-item
                v-if="item.status === 'ARCHIVED' && permissions.canEdit()"
                command="restore"
              >
                <el-icon><RefreshLeft /></el-icon>{{ t('functionUnit.restore') }}
              </el-dropdown-item>
              <el-dropdown-item
                v-if="inFolder"
                command="remove"
              >
                <el-icon><Remove /></el-icon>{{ t('functionUnit.removeFromGroup') }}
              </el-dropdown-item>
              <el-dropdown-item
                v-if="permissions.canDelete()"
                command="delete"
                divided
              >
                <el-icon><component :is="isArchived ? Delete : Box" /></el-icon>{{ deleteLabel }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <span class="tile-label">{{ item.name }}</span>
    </div>
  </el-tooltip>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Setting, CopyDocument, Delete, Box, RefreshLeft, MoreFilled, Remove } from '@element-plus/icons-vue'
import IconPreview from '@/components/icon/IconPreview.vue'
import type { FunctionUnitResponse } from '@/api/functionUnit'
import { permissions } from '@/utils/permission'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  item: FunctionUnitResponse
  /** 在分组浮层内时提供「移出分组」菜单项 */
  inFolder?: boolean
}>(), {
  inFolder: false,
})

const emit = defineEmits<{
  (e: 'open', item: FunctionUnitResponse): void
  (e: 'settings', item: FunctionUnitResponse): void
  (e: 'clone', item: FunctionUnitResponse): void
  (e: 'restore', item: FunctionUnitResponse): void
  (e: 'delete', item: FunctionUnitResponse): void
  (e: 'remove', item: FunctionUnitResponse): void
}>()

// 描述气泡也挂在磁贴上，菜单打开时两者会重叠并盖住菜单项，故开菜单即禁用气泡
const menuOpen = ref(false)

const statusLabel = computed(() => {
  const map: Record<string, string> = {
    DRAFT: t('functionUnit.draft'),
    PUBLISHED: t('functionUnit.published'),
    ARCHIVED: t('functionUnit.archived'),
  }
  return map[props.item.status] || props.item.status
})

// 后端 delete 是两段式：未归档的调用只是软删（置为 ARCHIVED），已归档的才真删。
// 菜单文案必须跟着状态走，否则第一次点「Delete」弹出的却是归档确认框。
const isArchived = computed(() => props.item.status === 'ARCHIVED')
const deleteLabel = computed(() =>
  isArchived.value ? t('functionUnit.deletePermanent') : t('functionUnit.archive')
)

function handleCommand(cmd: string) {
  if (cmd === 'settings') emit('settings', props.item)
  else if (cmd === 'clone') emit('clone', props.item)
  else if (cmd === 'restore') emit('restore', props.item)
  else if (cmd === 'delete') emit('delete', props.item)
  else if (cmd === 'remove') emit('remove', props.item)
}
</script>

<style lang="scss" scoped>
.launchpad-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;

  &:hover {
    .tile-icon {
      transform: translateY(-2px);
      box-shadow: 0 8px 20px rgba(20, 20, 20, 0.14);
    }

    .tile-menu-btn {
      opacity: 1;
    }
  }
}

.tile-icon {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: 20px;
  background: linear-gradient(145deg, #ffffff 0%, #eceef1 100%);
  border: 1px solid rgba(20, 20, 20, 0.06);
  box-shadow: 0 2px 8px rgba(20, 20, 20, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.18s ease, box-shadow 0.18s ease;

  :deep(.icon-preview) {
    width: 52px;
    height: 52px;
    background: transparent;

    svg {
      width: 100%;
      height: 100%;
    }
  }
}

.status-dot {
  position: absolute;
  top: 7px;
  right: 7px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  box-shadow: 0 0 0 2px #fff;

  &--published { background: #22a35a; }
  &--draft { background: #9c9c9c; }
  &--archived { background: #d9962c; }
}

.tile-menu {
  position: absolute;
  top: 4px;
  left: 4px;
}

.tile-menu-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 50%;
  background: rgba(20, 20, 20, 0.55);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease;

  &:hover {
    background: rgba(20, 20, 20, 0.75);
  }
}

.tile-label {
  max-width: 104px;
  font-size: 13px;
  line-height: 1.3;
  color: var(--ws-text);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

<style lang="scss">
// 图标悬浮描述气泡（Teleport 到 body，需全局样式）。
// z-index 交给 EP 自增基线（2000+）：分组浮层已压到 1900，无需再手动抬高。
.launchpad-tile-tooltip {
  max-width: 280px;
  line-height: 1.5;

  .tip-name {
    font-weight: 600;
  }

  .tip-desc {
    margin-top: 3px;
  }

  .tip-desc--empty {
    opacity: 0.65;
  }
}
</style>
