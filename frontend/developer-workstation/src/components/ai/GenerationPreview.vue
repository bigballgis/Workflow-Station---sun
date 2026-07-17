<template>
  <el-card
    class="generation-preview"
    shadow="hover"
  >
    <template #header>
      <div class="generation-preview__header">
        <span class="generation-preview__title">{{ t('ai.preview.title') }}</span>
      </div>
    </template>

    <!-- Task 16.1: Streaming indicator -->
    <div
      v-if="props.isStreaming"
      class="generation-preview__streaming"
    >
      <el-icon class="is-loading">
        <Loading />
      </el-icon>
      <span>{{ t('ai.preview.generating') }}</span>
    </div>

    <!-- Task 15.2: Quality Score Display -->
    <div
      v-if="previewData.qualityScore"
      class="generation-preview__quality"
    >
      <div class="generation-preview__quality-row">
        <el-progress
          type="dashboard"
          :percentage="previewData.qualityScore.totalScore"
          :color="qualityScoreColor"
          :width="80"
        />
        <div class="generation-preview__quality-dimensions">
          <el-tag
            v-for="(score, dim) in previewData.qualityScore.dimensions"
            :key="dim"
            size="small"
            :type="score > 20 ? 'success' : score > 12 ? 'warning' : 'danger'"
          >
            {{ t(`ai.quality.${dim}`) }}: {{ score }}/25
          </el-tag>
        </div>
      </div>
      <el-alert
        v-if="previewData.qualityScore.totalScore < 50"
        type="warning"
        :closable="false"
        show-icon
        class="generation-preview__quality-warning"
      >
        {{ t('ai.quality.lowScoreWarning') }}
      </el-alert>
      <ul
        v-if="previewData.qualityScore.suggestions.length"
        class="generation-preview__suggestions"
      >
        <li
          v-for="(suggestion, idx) in previewData.qualityScore.suggestions"
          :key="idx"
        >
          {{ suggestion }}
        </li>
      </ul>
    </div>

    <div class="generation-preview__summary">
      <!-- Task 17.2: Tabs for MODIFY mode diff preview -->
      <el-tabs
        v-if="props.mode === 'MODIFY' && props.diffResult"
        v-model="previewTab"
        class="generation-preview__tabs"
      >
        <el-tab-pane
          :label="t('ai.preview.summary')"
          name="summary"
        />
        <el-tab-pane
          :label="t('ai.preview.diff')"
          name="diff"
        />
      </el-tabs>

      <!-- Summary tab (default) -->
      <template v-if="previewTab === 'summary' || !props.diffResult || props.mode !== 'MODIFY'">
        <el-descriptions
          :column="2"
          border
          size="small"
        >
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
          <el-descriptions-item :label="t('ai.preview.decisions')">
            {{ previewData.decisionCount }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('ai.preview.tableRelations')">
            {{ previewData.tableRelationCount }}
          </el-descriptions-item>
        </el-descriptions>

        <div
          v-if="previewData.actionTypes.length"
          class="generation-preview__tags"
        >
          <span class="generation-preview__label">{{ t('ai.preview.actionTypes') }}</span>
          <el-tag
            v-for="actionType in previewData.actionTypes"
            :key="actionType"
            size="small"
            type="info"
            class="generation-preview__tag"
          >
            {{ actionType }}
          </el-tag>
        </div>

        <div
          v-if="previewData.iconSvg"
          class="generation-preview__icon"
        >
          <span class="generation-preview__label">{{ t('ai.preview.iconPreview') }}</span>
          <div
            class="generation-preview__icon-box"
            v-html="sanitizedIconSvg"
          />
        </div>
      </template>

      <!-- Task 17.2: Diff tab -->
      <template v-if="previewTab === 'diff' && props.diffResult && props.mode === 'MODIFY'">
        <div class="generation-preview__diff-summary">
          {{ t('ai.diff.summary', { added: props.diffResult.added.length, modified: props.diffResult.modified.length, deleted: props.diffResult.removed.length }) }}
        </div>
        <div
          v-for="item in props.diffResult.added"
          :key="'add-'+item.type+'-'+item.name"
          class="generation-preview__diff-item generation-preview__diff-item--added"
        >
          <el-tag
            type="success"
            size="small"
          >
            +
          </el-tag>
          <span>{{ item.type }}: {{ item.name }}</span>
        </div>
        <div
          v-for="item in props.diffResult.removed"
          :key="'rm-'+item.type+'-'+item.name"
          class="generation-preview__diff-item generation-preview__diff-item--removed"
        >
          <el-tag
            type="danger"
            size="small"
          >
            -
          </el-tag>
          <span>{{ item.type }}: {{ item.name }}</span>
        </div>
        <div
          v-for="item in props.diffResult.modified"
          :key="'mod-'+item.type+'-'+item.name"
          class="generation-preview__diff-item generation-preview__diff-item--modified"
        >
          <el-tag
            type="warning"
            size="small"
          >
            ~
          </el-tag>
          <span>{{ item.type }}: {{ item.name }}</span>
          <ul
            v-if="item.changes?.length"
            class="generation-preview__diff-changes"
          >
            <li
              v-for="(c, ci) in item.changes"
              :key="ci"
            >
              {{ c }}
            </li>
          </ul>
        </div>
      </template>
    </div>

    <!-- Task 16.1: Skeleton for unreached parts during streaming -->
    <el-skeleton
      v-if="props.isStreaming && !previewData.formCount"
      :rows="2"
      animated
      class="generation-preview__skeleton"
    />
    <el-skeleton
      v-if="props.isStreaming && !previewData.processNodeCount && !previewData.processGatewayCount"
      :rows="1"
      animated
      class="generation-preview__skeleton"
    />

    <!-- Task 15.1: Detailed Preview Collapse Area -->
    <el-collapse class="generation-preview__details">
      <el-collapse-item :title="t('ai.preview.viewDetails')">
        <!-- Table definitions detail -->
        <div
          v-for="(table, tIdx) in (generatedData.tableDefinitions || [])"
          :key="'table-' + tIdx"
          class="preview-detail__table"
        >
          <div class="preview-detail__entity-header">
            <el-tag
              size="small"
              type="primary"
            >
              {{ table.tableType || 'TABLE' }}
            </el-tag>
            <span class="preview-detail__entity-name">
              <!-- Task 15.3: AI Explanation Tooltip for table name -->
              <el-tooltip
                v-if="getExplanation(`tableDefinitions[${tIdx}]`)"
                :content="getExplanation(`tableDefinitions[${tIdx}]`)!"
                placement="top"
              >
                <span>{{ table.tableName }} <el-icon><InfoFilled /></el-icon></span>
              </el-tooltip>
              <span v-else>{{ table.tableName }}</span>
            </span>
          </div>
          <div
            v-if="table.fieldDefinitions?.length"
            class="preview-detail__fields"
          >
            <el-tag
              v-for="(field, fIdx) in table.fieldDefinitions"
              :key="field.fieldName"
              size="small"
              class="preview-detail__field-tag"
            >
              <!-- Task 15.3: AI Explanation Tooltip for field dataType -->
              <el-tooltip
                v-if="getExplanation(`tableDefinitions[${tIdx}].fieldDefinitions[${fIdx}].dataType`)"
                :content="getExplanation(`tableDefinitions[${tIdx}].fieldDefinitions[${fIdx}].dataType`)!"
                placement="top"
              >
                <span>{{ field.fieldName }}: {{ field.dataType || field.fieldType }} <el-icon><InfoFilled /></el-icon></span>
              </el-tooltip>
              <span v-else>{{ field.fieldName }}: {{ field.dataType || field.fieldType }}</span>
            </el-tag>
          </div>
        </div>

        <!-- Form definitions detail -->
        <div
          v-for="(form, fmIdx) in (generatedData.formDefinitions || [])"
          :key="'form-' + fmIdx"
          class="preview-detail__form"
        >
          <el-tag size="small">
            {{ form.formType }}
          </el-tag>
          <span class="preview-detail__entity-name">
            <!-- Task 15.3: AI Explanation Tooltip for form -->
            <el-tooltip
              v-if="getExplanation(`formDefinitions[${fmIdx}]`)"
              :content="getExplanation(`formDefinitions[${fmIdx}]`)!"
              placement="top"
            >
              <span>{{ form.formName }} <el-icon><InfoFilled /></el-icon></span>
            </el-tooltip>
            <span v-else>{{ form.formName }}</span>
          </span>
          <span
            v-if="form.tableBindings?.length"
            class="preview-detail__binding"
          >
            → {{ form.tableBindings.map((b: any) => b.tableName).join(', ') }}
          </span>
        </div>
      </el-collapse-item>
    </el-collapse>

    <div class="generation-preview__actions">
      <el-button
        v-if="props.applyState === 'applied'"
        type="success"
        disabled
      >
        {{ t('ai.preview.applied') }}
      </el-button>
      <el-button
        v-else
        type="primary"
        :loading="props.applyState === 'applying'"
        :disabled="!props.isGenerationComplete"
        @click="emit('apply')"
      >
        {{ props.applyState === 'applying' ? t('ai.preview.applying') : t('ai.preview.apply') }}
      </el-button>
      <el-button
        :disabled="props.applyState === 'applying'"
        @click="emit('regenerate')"
      >
        {{ t('ai.preview.regenerate') }}
      </el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { InfoFilled, Loading } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import type { GenerationPreviewData, AiGeneratedData, DiffResult } from '@/types/aiGeneration'

const { t } = useI18n()

const SVG_PURIFY_CONFIG = {
  ALLOWED_TAGS: ['svg', 'path', 'circle', 'rect', 'line', 'polyline', 'polygon', 'g', 'defs', 'use'],
  FORBID_TAGS: ['script', 'iframe', 'object', 'embed'],
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur'],
  ALLOWED_ATTR: ['viewBox', 'd', 'fill', 'stroke', 'stroke-width', 'cx', 'cy', 'r', 'x', 'y',
    'width', 'height', 'points', 'transform', 'class', 'xmlns', 'xlink:href'],
}

const props = defineProps<{
  previewData: GenerationPreviewData
  generatedData: AiGeneratedData
  isGenerationComplete?: boolean
  isStreaming?: boolean
  mode?: string
  diffResult?: DiffResult | null
  /** Apply lifecycle owned by ChatDialog: spinner while the write runs, green "Applied" after. */
  applyState?: 'idle' | 'applying' | 'applied'
}>()

const emit = defineEmits<{
  apply: []
  regenerate: []
}>()

const sanitizedIconSvg = computed(() => {
  if (!props.previewData.iconSvg) return ''
  return DOMPurify.sanitize(props.previewData.iconSvg, SVG_PURIFY_CONFIG)
})

// Task 15.2: Quality score color coding
const qualityScoreColor = computed(() => {
  const score = props.previewData.qualityScore?.totalScore ?? 0
  if (score > 80) return '#67c23a'  // green
  if (score >= 50) return '#e6a23c' // yellow
  return '#f56c6c'                  // red
})

// Task 15.3: AI explanation lookup
function getExplanation(path: string): string | undefined {
  return props.generatedData?.explanations?.[path]
}

// Task 17.2: Diff preview tab state
const previewTab = ref('summary')
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

.generation-preview__streaming {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #409eff;
  font-size: 13px;
  margin-bottom: 8px;
}

.generation-preview__skeleton {
  margin: 8px 0;
}

.generation-preview__quality {
  margin-bottom: 12px;
}

.generation-preview__quality-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.generation-preview__quality-dimensions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.generation-preview__quality-warning {
  margin-top: 8px;
}

.generation-preview__suggestions {
  margin: 8px 0 0;
  padding-left: 20px;
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
}

.generation-preview__details {
  margin-top: 12px;
}

.preview-detail__table {
  margin-bottom: 8px;
}

.preview-detail__entity-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.preview-detail__entity-name {
  font-weight: 500;
  font-size: 13px;
}

.preview-detail__fields {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding-left: 16px;
}

.preview-detail__field-tag {
  margin: 0;
}

.preview-detail__form {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.preview-detail__binding {
  font-size: 12px;
  color: #909399;
}

.generation-preview__tabs {
  margin-bottom: 8px;
}

.generation-preview__diff-summary {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}

.generation-preview__diff-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 4px 8px;
  margin-bottom: 4px;
  border-radius: 4px;
  font-size: 13px;

  &--added {
    background: #f0f9eb;
  }

  &--removed {
    background: #fef0f0;
  }

  &--modified {
    background: #fdf6ec;
  }
}

.generation-preview__diff-changes {
  margin: 4px 0 0;
  padding-left: 20px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
