<template>
  <div
    class="launchpad-folder"
    :title="folder.name"
    @click="$emit('open', folder)"
  >
    <div class="folder-icon">
      <div class="folder-mini-grid">
        <span
          v-for="item in miniItems"
          :key="item.id"
          class="mini-cell"
        >
          <IconPreview
            :icon-id="item.iconId"
            size="small"
          />
        </span>
      </div>
      <span
        v-if="folder.itemIds.length > 9"
        class="folder-count"
      >{{ folder.itemIds.length }}</span>
    </div>
    <span class="tile-label">{{ folder.name }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import IconPreview from '@/components/icon/IconPreview.vue'
import type { FunctionUnitResponse } from '@/api/functionUnit'
import type { LaunchpadFolderEntry } from '@/composables/functionUnitList/useLaunchpadLayout'

const props = defineProps<{
  folder: LaunchpadFolderEntry
  /** 已按组内顺序解析出的成员（可见成员） */
  items: FunctionUnitResponse[]
}>()

defineEmits<{
  (e: 'open', folder: LaunchpadFolderEntry): void
}>()

const miniItems = computed(() => props.items.slice(0, 9))
</script>

<style lang="scss" scoped>
.launchpad-folder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;

  &:hover .folder-icon {
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(20, 20, 20, 0.14);
  }
}

.folder-icon {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: 20px;
  // 分组底比单品磁贴深一档：磨砂托盘的感觉
  background: linear-gradient(145deg, #e7e9ee 0%, #d9dce3 100%);
  border: 1px solid rgba(20, 20, 20, 0.08);
  box-shadow: inset 0 1px 2px rgba(255, 255, 255, 0.7), 0 2px 8px rgba(20, 20, 20, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.folder-mini-grid {
  display: grid;
  grid-template-columns: repeat(3, 20px);
  grid-auto-rows: 20px;
  gap: 4px;
}

.mini-cell {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  :deep(.icon-preview) {
    width: 14px;
    height: 14px;
    background: transparent;

    svg {
      width: 100%;
      height: 100%;
    }
  }
}

.folder-count {
  position: absolute;
  bottom: 5px;
  right: 7px;
  font-size: 10px;
  font-weight: 600;
  color: var(--ws-text-secondary);
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
