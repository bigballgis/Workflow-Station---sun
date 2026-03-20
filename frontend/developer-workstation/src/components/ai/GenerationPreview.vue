<template>
  <el-card class="generation-preview" shadow="hover">
    <template #header>
      <div class="generation-preview__header">
        <span class="generation-preview__title">{{ t('ai.preview.title') }}</span>
      </div>
    </template>

    <div class="generation-preview__summary">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item :label="t('ai.preview.tables')">
          {{ t('ai.preview.tablesSummary', { count: previewData.tableCount, fields: previewData.totalFieldCount }) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('ai.preview.forms')">
          {{ t('ai.preview.formsSummary', { count: previewData.formCount }) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('ai.preview.actions')">
          {{ t('ai.preview.actionsSummary', { count: previewData.actionCount }) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('ai.preview.process')">
          {{ t('ai.preview.processSummary', { nodes: previewData.processNodeCount, gateways: previewData.processGatewayCount }) }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="previewData.actionTypes.length" class="generation-preview__tags">
        <span class="generation-preview__label">{{ t('ai.preview.actionTypes') }}</span>
        <el-tag v-for="t in previewData.actionTypes" :key="t" size="small" type="info" class="generation-preview__tag">
          {{ t }}
        </el-tag>
      </div>

      <div v-if="previewData.iconSvg" class="generation-preview__icon">
        <span class="generation-preview__label">{{ t('ai.preview.iconPreview') }}</span>
        <div class="generation-preview__icon-box" v-html="previewData.iconSvg" />
      </div>
    </div>

    <div class="generation-preview__actions">
      <el-button type="primary" @click="emit('apply')">{{ t('ai.preview.apply') }}</el-button>
      <el-button @click="emit('regenerate')">{{ t('ai.preview.regenerate') }}</el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { GenerationPreviewData, AiGeneratedData } from '@/types/aiGeneration'

const { t } = useI18n()

const props = defineProps<{
  previewData: GenerationPreviewData
  generatedData: AiGeneratedData
}>()

const emit = defineEmits<{
  apply: []
  regenerate: []
}>()
</script>

<style lang="scss" scoped>
.generation-preview {
  margin: 12px 0;
}

.generation-preview__header {
  display: flex;
  align-items: center;
}

.generation-preview__title {
  font-weight: 600;
  font-size: 15px;
}

.generation-preview__summary {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.generation-preview__tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.generation-preview__label {
  font-size: 13px;
  color: #606266;
}

.generation-preview__tag {
  margin: 0;
}

.generation-preview__icon {
  display: flex;
  align-items: center;
  gap: 8px;
}

.generation-preview__icon-box {
  width: 48px;
  height: 48px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  background: #fafafa;

  :deep(svg) {
    width: 100%;
    height: 100%;
  }
}

.generation-preview__actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
