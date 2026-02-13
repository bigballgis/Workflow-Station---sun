<template>
  <div class="function-unit-card" @click="handleClick">
    <!-- Status Badge - Top Right -->
    <el-tag class="status-badge" :type="statusType" size="small">{{ statusLabel }}</el-tag>
    
    <!-- Icon Area - Larger -->
    <div class="card-icon">
      <IconPreview :icon-id="item.iconId" size="large" />
    </div>
    
    <!-- Content Area -->
    <div class="card-content">
      <h3 class="card-title">{{ item.name }}</h3>
      <p class="card-description-preview" v-if="item.description">{{ item.description }}</p>
      <div class="card-tags" v-if="tags.length > 0">
        <el-tag 
          v-for="tag in displayTags" 
          :key="tag" 
          size="small"
          effect="plain"
        >{{ tag }}</el-tag>
        <el-tag 
          v-if="extraTagCount > 0" 
          size="small" 
          type="info"
          effect="plain"
        >+{{ extraTagCount }}</el-tag>
      </div>
    </div>

    <!-- Hover Overlay with Description -->
    <div class="card-overlay">
      <p class="card-description" v-if="item.description">{{ item.description }}</p>
      <div class="card-actions" @click.stop>
        <el-button v-if="permissions.canEdit()" size="small" type="primary" @click="$emit('edit', item)">
          <el-icon><Edit /></el-icon>
          {{ t('common.edit') }}
        </el-button>
        <el-button v-if="permissions.canClone()" size="small" type="warning" @click="$emit('clone', item)">
          <el-icon><CopyDocument /></el-icon>
          {{ t('functionUnit.clone') }}
        </el-button>
        <el-button v-if="permissions.canDelete()" size="small" type="danger" @click="$emit('delete', item)">
          <el-icon><Delete /></el-icon>
          {{ t('common.delete') }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Edit, CopyDocument, Delete } from '@element-plus/icons-vue'
import IconPreview from '@/components/icon/IconPreview.vue'
import type { FunctionUnitResponse } from '@/api/functionUnit'
import { permissions } from '@/utils/permission'

const { t } = useI18n()
const MAX_DISPLAY_TAGS = 3

const props = defineProps<{
  item: FunctionUnitResponse
  tags: string[]
}>()

const emit = defineEmits<{
  (e: 'click', item: FunctionUnitResponse): void
  (e: 'edit', item: FunctionUnitResponse): void
  (e: 'clone', item: FunctionUnitResponse): void
  (e: 'delete', item: FunctionUnitResponse): void
}>()

const displayTags = computed(() => props.tags.slice(0, MAX_DISPLAY_TAGS))
const extraTagCount = computed(() => Math.max(0, props.tags.length - MAX_DISPLAY_TAGS))

const statusType = computed(() => {
  const map: Record<string, string> = { 
    DRAFT: 'info', 
    PUBLISHED: 'success', 
    ARCHIVED: 'warning' 
  }
  return map[props.item.status] || 'info'
})

const statusLabel = computed(() => {
  const map: Record<string, string> = { 
    DRAFT: t('functionUnit.draft'), 
    PUBLISHED: t('functionUnit.published'), 
    ARCHIVED: t('functionUnit.archived') 
  }
  return map[props.item.status] || props.item.status
})

function handleClick() {
  emit('click', props.item)
}
</script>

<style lang="scss" scoped>
.function-unit-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  position: relative;
  
  &:hover {
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
    transform: translateY(-4px);
    
    .card-overlay {
      opacity: 1;
    }
  }
}

.status-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2;
}

.card-icon {
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  
  :deep(.icon-preview) {
    width: 80px;
    height: 80px;
    background: transparent;
    
    svg {
      width: 100%;
      height: 100%;
    }
  }
}

.card-content {
  padding: 16px;
  text-align: center;
  min-height: 120px;
  display: flex;
  flex-direction: column;
}

.card-title {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-description-preview {
  margin: 0 0 12px 0;
  font-size: 12px;
  line-height: 1.5;
  color: #606266;
  text-align: center;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  flex: 1;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
  margin-top: auto;
  
  .el-tag {
    font-size: 11px;
  }
}

.card-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  opacity: 0;
  transition: opacity 0.3s ease;
  z-index: 3;
}

.card-description {
  color: #fff;
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
  margin: 0 0 20px 0;
  max-height: 80px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
</style>
