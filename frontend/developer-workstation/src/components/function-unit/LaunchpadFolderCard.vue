<template>
  <div
    class="folder-card"
    :title="folder.name"
    @click="$emit('open', folder)"
  >
    <div class="folder-card-icon-area">
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
    </div>
    <div class="folder-card-content">
      <h3 class="folder-card-title">
        {{ folder.name }}
      </h3>
      <p class="folder-card-count">
        {{ t('functionUnit.groupItemCount', { count: folder.itemIds.length }) }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import IconPreview from '@/components/icon/IconPreview.vue'
import type { FunctionUnitResponse } from '@/api/functionUnit'
import type { LaunchpadFolderEntry } from '@/composables/functionUnitList/useLaunchpadLayout'

const { t } = useI18n()

const props = defineProps<{
  folder: LaunchpadFolderEntry
  /** 已按组内顺序解析出的成员 */
  items: FunctionUnitResponse[]
}>()

defineEmits<{
  (e: 'open', folder: LaunchpadFolderEntry): void
}>()

const miniItems = computed(() => props.items.slice(0, 9))
</script>

<style lang="scss" scoped>
// 与 FunctionUnitCard 同一卡片语言：白底 12px 圆角、同阴影、同图标区高度
.folder-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  flex-direction: column;

  &:hover {
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
    transform: translateY(-4px);
  }
}

.folder-card-icon-area {
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  // 比单品卡片深一档的托盘底，示意「一组」
  background: linear-gradient(135deg, #eceef1 0%, #dde0e6 100%);
}

.folder-mini-grid {
  display: grid;
  grid-template-columns: repeat(3, 34px);
  grid-auto-rows: 34px;
  gap: 7px;
}

.mini-cell {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: #ffffff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  :deep(.icon-preview) {
    width: 22px;
    height: 22px;
    background: transparent;

    svg {
      width: 100%;
      height: 100%;
    }
  }
}

.folder-card-content {
  padding: 16px;
  text-align: center;
}

.folder-card-title {
  margin: 0 0 6px 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-card-count {
  margin: 0;
  font-size: 12px;
  color: var(--ws-text-muted);
}
</style>
