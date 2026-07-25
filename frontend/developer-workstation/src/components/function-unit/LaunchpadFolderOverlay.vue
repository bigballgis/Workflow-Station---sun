<template>
  <Teleport to="body">
    <Transition name="folder-fade">
      <div
        v-if="folder"
        class="folder-overlay"
        @click.self="$emit('close')"
        @dragover.prevent
        @drop.self.prevent="onDropOutside"
      >
        <div class="folder-panel">
          <input
            class="folder-name-input"
            :value="folder.name"
            :placeholder="t('functionUnit.groupNamePlaceholder')"
            spellcheck="false"
            @change="onRename(($event.target as HTMLInputElement).value)"
            @keydown.enter="($event.target as HTMLInputElement).blur()"
          >
          <!-- 3 列网格，默认露出 3 行（9 张卡），更多成员上下滚动 -->
          <div class="folder-grid-viewport">
            <div class="folder-grid">
              <el-tooltip
                v-for="item in items"
                :key="item.id"
                placement="bottom"
                :show-after="250"
                popper-class="launchpad-tile-tooltip"
              >
                <template #content>
                  <!-- 与主网格图标气泡同款：全名 + 描述（缺省时明示「暂无描述」） -->
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
                  class="member-card"
                  :class="dropClasses(item.id)"
                  draggable="true"
                  @click="$emit('openItem', item)"
                  @dragstart="onCellDragStart(item.id)"
                  @dragend="onCellDragEnd"
                  @dragover.prevent.stop="onCellDragOver(item.id, $event)"
                  @dragleave="onCellDragLeave(item.id)"
                  @drop.prevent.stop="onCellDrop(item.id)"
                >
                  <span class="member-icon">
                    <IconPreview
                      :icon-id="item.iconId"
                      size="medium"
                    />
                  </span>
                  <span class="member-info">
                    <span class="member-name">{{ item.name }}</span>
                    <span
                      class="member-status"
                      :class="`member-status--${item.status.toLowerCase()}`"
                    >{{ statusLabel(item) }}</span>
                  </span>
                  <el-dropdown
                    trigger="click"
                    popper-class="launchpad-dropdown-popper"
                    @command="(cmd: string) => handleCommand(cmd, item)"
                  >
                    <button
                      class="member-menu-btn"
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
                        <el-dropdown-item command="remove">
                          <el-icon><Remove /></el-icon>{{ t('functionUnit.removeFromGroup') }}
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-if="permissions.canDelete()"
                          command="delete"
                          divided
                        >
                          <el-icon><Delete /></el-icon>{{ t('common.delete') }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </el-tooltip>
            </div>
          </div>
          <p class="folder-hint">
            {{ t('functionUnit.dragOutHint') }}
          </p>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Setting, CopyDocument, Delete, RefreshLeft, MoreFilled, Remove } from '@element-plus/icons-vue'
import IconPreview from '@/components/icon/IconPreview.vue'
import type { FunctionUnitResponse } from '@/api/functionUnit'
import type { LaunchpadFolderEntry } from '@/composables/functionUnitList/useLaunchpadLayout'
import { permissions } from '@/utils/permission'

const { t } = useI18n()

const props = defineProps<{
  /** null = 关闭 */
  folder: LaunchpadFolderEntry | null
  /** 按组内顺序解析出的成员 */
  items: FunctionUnitResponse[]
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'openItem', item: FunctionUnitResponse): void
  (e: 'settings', item: FunctionUnitResponse): void
  (e: 'clone', item: FunctionUnitResponse): void
  (e: 'restore', item: FunctionUnitResponse): void
  (e: 'delete', item: FunctionUnitResponse): void
  (e: 'remove', folderId: string, item: FunctionUnitResponse): void
  (e: 'rename', folderId: string, name: string): void
  (e: 'reorder', folderId: string, fromId: number, toId: number, mode: 'before' | 'after'): void
}>()

function statusLabel(item: FunctionUnitResponse): string {
  const map: Record<string, string> = {
    DRAFT: t('functionUnit.draft'),
    PUBLISHED: t('functionUnit.published'),
    ARCHIVED: t('functionUnit.archived'),
  }
  return map[item.status] || item.status
}

function handleCommand(cmd: string, item: FunctionUnitResponse) {
  if (!props.folder) return
  if (cmd === 'settings') emit('settings', item)
  else if (cmd === 'clone') emit('clone', item)
  else if (cmd === 'restore') emit('restore', item)
  else if (cmd === 'delete') emit('delete', item)
  else if (cmd === 'remove') emit('remove', props.folder.id, item)
}

function onRename(name: string) {
  if (props.folder) emit('rename', props.folder.id, name)
}

// ==================== 组内拖拽：卡片间重排；拖到面板外 = 移出分组 ====================
const draggingId = ref<number | null>(null)
const dropTarget = ref<{ id: number; mode: 'before' | 'after' } | null>(null)

function onCellDragStart(id: number) {
  draggingId.value = id
  dropTarget.value = null
}

function onCellDragEnd() {
  draggingId.value = null
  dropTarget.value = null
}

function onCellDragOver(id: number, event: DragEvent) {
  if (draggingId.value == null || draggingId.value === id) {
    dropTarget.value = null
    return
  }
  const el = event.currentTarget as HTMLElement
  const rect = el.getBoundingClientRect()
  const mode = event.clientX - rect.left < rect.width / 2 ? 'before' : 'after'
  dropTarget.value = { id, mode }
}

function onCellDragLeave(id: number) {
  if (dropTarget.value?.id === id) dropTarget.value = null
}

function onCellDrop(id: number) {
  const fromId = draggingId.value
  const target = dropTarget.value
  onCellDragEnd()
  if (fromId == null || !target || target.id !== id || !props.folder) return
  emit('reorder', props.folder.id, fromId, id, target.mode)
}

function dropClasses(id: number) {
  return {
    'is-dragging': draggingId.value === id,
    'drop-before': dropTarget.value?.id === id && dropTarget.value.mode === 'before',
    'drop-after': dropTarget.value?.id === id && dropTarget.value.mode === 'after',
  }
}

/** 松手在面板外（遮罩层）：移出分组回到主网格 */
function onDropOutside() {
  const fromId = draggingId.value
  onCellDragEnd()
  if (fromId == null || !props.folder) return
  const item = props.items.find((i) => i.id === fromId)
  if (item) emit('remove', props.folder.id, item)
}
</script>

<style lang="scss" scoped>
.folder-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(30, 30, 34, 0.45);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
}

.folder-panel {
  width: 760px;
  max-width: calc(100vw - 48px);
  padding: 24px 24px 16px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
}

.folder-name-input {
  display: block;
  width: 100%;
  margin-bottom: 18px;
  border: none;
  outline: none;
  background: transparent;
  font-size: 20px;
  font-weight: 700;
  text-align: center;
  color: var(--ws-text);
  font-family: inherit;

  &::placeholder {
    color: var(--ws-text-muted);
    font-weight: 500;
  }

  &:focus {
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.7);
  }
}

// 3 行整高 + 滚动：一行卡片 76 + 行距 14
.folder-grid-viewport {
  max-height: calc(3 * 76px + 2 * 14px);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.folder-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

// 成员：紧凑横向卡片，沿用主卡片语言（白底圆角阴影）
.member-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 76px;
  padding: 0 12px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  user-select: none;
  transition: box-shadow 0.2s ease, transform 0.2s ease, opacity 0.15s ease;

  &:hover {
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.14);
    transform: translateY(-2px);

    .member-menu-btn {
      opacity: 1;
    }
  }

  &.is-dragging {
    opacity: 0.35;
  }

  &.drop-before::before,
  &.drop-after::after {
    content: '';
    position: absolute;
    top: 10px;
    bottom: 10px;
    width: 3px;
    border-radius: 2px;
    background: var(--primary-color);
  }

  &.drop-before::before { left: -9px; }
  &.drop-after::after { right: -9px; }
}

.member-icon {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 11px;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  display: flex;
  align-items: center;
  justify-content: center;

  :deep(.icon-preview) {
    width: 28px;
    height: 28px;
    background: transparent;

    svg {
      width: 100%;
      height: 100%;
    }
  }
}

.member-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.member-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-status {
  font-size: 11px;

  &--published { color: #1f7a40; }
  &--draft { color: var(--ws-text-secondary); }
  &--archived { color: #a36a00; }
}

.member-menu-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 50%;
  background: rgba(20, 20, 20, 0.08);
  color: var(--ws-text-secondary);
  font-size: 12px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease;

  &:hover {
    background: rgba(20, 20, 20, 0.16);
  }
}

.folder-hint {
  margin: 14px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--ws-text-secondary);
}

.folder-fade-enter-active,
.folder-fade-leave-active {
  transition: opacity 0.18s ease;

  .folder-panel {
    transition: transform 0.18s ease;
  }
}

.folder-fade-enter-from,
.folder-fade-leave-to {
  opacity: 0;

  .folder-panel {
    transform: scale(0.92);
  }
}

@media (prefers-reduced-motion: reduce) {
  .folder-fade-enter-active,
  .folder-fade-leave-active,
  .folder-fade-enter-active .folder-panel,
  .folder-fade-leave-active .folder-panel {
    transition: none;
  }
}
</style>
