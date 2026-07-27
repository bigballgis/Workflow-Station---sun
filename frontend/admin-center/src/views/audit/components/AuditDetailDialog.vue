<template>
  <el-dialog
    v-model="visible"
    :title="t('audit.logDetail')"
    width="800px"
  >
    <div
      v-if="log"
      class="log-detail"
    >
      <!-- Basic Info Section -->
      <div class="detail-section section-basic">
        <div class="section-title">
          {{ t('audit.basicInfo') }}
        </div>
        <div class="detail-grid">
          <div class="detail-row">
            <span class="detail-label">{{ t('audit.actionType') }}</span>
            <span class="detail-value"><el-tag
              :type="actionType(log.action)"
              size="small"
            >{{ actionText(log.action) }}</el-tag></span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('audit.operator') }}</span>
            <span class="detail-value">{{ log.username || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('audit.resourceType') }}</span>
            <span class="detail-value">{{ resourceTypeText(log.resourceType) || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('audit.resourceId') }}</span>
            <span class="detail-value">{{ log.resourceId || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('audit.ipAddress') }}</span>
            <span class="detail-value">{{ log.ipAddress || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('audit.result') }}</span>
            <span class="detail-value">
              <span :class="log.result === 'SUCCESS' ? 'result-success' : log.result === 'PENDING' ? 'result-pending' : 'result-danger'">
                {{ log.result === 'SUCCESS' ? t('audit.success') : log.result === 'PENDING' ? t('audit.pending') : t('audit.failed') }}
              </span>
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('audit.requestMethod') }}</span>
            <span class="detail-value">{{ log.requestMethod || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('audit.requestPath') }}</span>
            <span class="detail-value path-value">{{ log.requestPath || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('audit.duration') }}</span>
            <span class="detail-value">{{ log.duration != null ? log.duration + 'ms' : '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('audit.time') }}</span>
            <span class="detail-value">{{ formatTime(log.createdAt) }}</span>
          </div>
          <div
            v-if="log.errorMessage"
            class="detail-row"
          >
            <span class="detail-label">{{ t('audit.errorMessage') }}</span>
            <span class="detail-value result-danger">{{ log.errorMessage }}</span>
          </div>
        </div>
      </div>

      <!-- Before Change Section -->
      <div
        v-if="beforeData !== null"
        class="detail-section section-before"
      >
        <div class="section-title">
          {{ t('audit.oldValue') }}
          <span
            v-if="actionCategory(log.action) === 'delete'"
            class="section-badge"
          >{{ t('audit.fullRecord') }}</span>
          <span
            v-if="actionCategory(log.action) === 'update'"
            class="section-badge badge-diff"
          >{{ t('audit.changedFieldsOnly') }}</span>
        </div>
        <div class="json-container">
          <pre
            class="json-content"
            :class="{ collapsed: !beforeExpanded }"
            v-html="formatJsonHighlight(beforeData, beforeCompare)"
          />
          <el-button
            class="expand-btn"
            link
            type="primary"
            @click="beforeExpanded = !beforeExpanded"
          >
            {{ beforeExpanded ? t('common.collapse') : t('common.expand') }}
          </el-button>
        </div>
      </div>

      <!-- After Change Section -->
      <div
        v-if="afterData !== null"
        class="detail-section section-after"
      >
        <div class="section-title">
          {{ afterSectionTitle }}
          <span
            v-if="actionCategory(log.action) === 'create'"
            class="section-badge"
          >{{ t('audit.fullRecord') }}</span>
          <span
            v-if="actionCategory(log.action) === 'update'"
            class="section-badge badge-diff"
          >{{ t('audit.changedFieldsOnly') }}</span>
        </div>
        <div class="json-container">
          <pre
            class="json-content"
            :class="{ collapsed: !afterExpanded }"
            v-html="formatJsonHighlight(afterData, afterCompare)"
          />
          <el-button
            class="expand-btn"
            link
            type="primary"
            @click="afterExpanded = !afterExpanded"
          >
            {{ afterExpanded ? t('common.collapse') : t('common.expand') }}
          </el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AuditLog } from '@/api/audit'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  log: AuditLog | null
  /** Pre-computed display helpers (passed from parent to keep logic centralized) */
  actionType: (action: string) => 'success' | 'warning' | 'info' | 'primary' | 'danger'
  actionText: (action: string) => string
  resourceTypeText: (rt: string | null | undefined) => string
  formatTime: (isoStr: string | null | undefined) => string
  actionCategory: (action: string) => 'create' | 'update' | 'delete' | 'query' | 'other'
  formatJsonHighlight: (obj: Record<string, unknown> | null, compareAgainst?: Record<string, unknown> | null) => string
  /** Pre-computed diff data from parent */
  beforeData: Record<string, unknown> | null
  afterData: Record<string, unknown> | null
  beforeCompare: Record<string, unknown> | null
  afterCompare: Record<string, unknown> | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })

// For DELETE/QUERY the "after" block holds the operation parameters, not a
// post-change state (see getAfterData) — label it accordingly.
const afterSectionTitle = computed(() => {
  const cat = props.log ? props.actionCategory(props.log.action) : 'other'
  return cat === 'delete' || cat === 'query' ? t('audit.requestParams') : t('audit.newValue')
})

// Local expand/collapse state (purely UI concern)
const beforeExpanded = ref(false)
const afterExpanded = ref(false)

// Reset expand state when dialog opens with new log
watch(() => props.log, () => {
  beforeExpanded.value = false
  afterExpanded.value = false
})
</script>

<style scoped>
.log-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-section {
  border: 1px solid #dee2e6;
  border-radius: 6px;
  overflow: hidden;
}

.section-basic { background: #f8f9fa; }

.section-before { background: #fff8f0; }
.section-before .section-title {
  background: #ffe8cc;
  border-bottom-color: #ffd6a5;
  color: #7d4800;
}

.section-after { background: #f0fff4; }
.section-after .section-title {
  background: #c3f0d4;
  border-bottom-color: #99e0b4;
  color: #1a5c35;
}

.section-title {
  font-weight: 600;
  font-size: 13px;
  padding: 8px 14px;
  border-bottom: 1px solid #dee2e6;
  background: #e9ecef;
  color: #343a40;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-badge {
  font-size: 11px;
  font-weight: 400;
  padding: 1px 7px;
  border-radius: 10px;
  background: #e9ecef;
  color: #6c757d;
  border: 1px solid #dee2e6;
}

.section-badge.badge-diff {
  background: #fff3cd;
  color: #856404;
  border-color: #ffc107;
}

.detail-grid {
  display: grid;
  grid-template-columns: 130px 1fr;
  gap: 0;
}

.detail-row { display: contents; }

.detail-label {
  padding: 8px 14px;
  font-size: 13px;
  color: #6c757d;
  font-weight: 500;
  border-bottom: 1px solid #e9ecef;
  background: rgba(0,0,0,0.02);
  display: flex;
  align-items: center;
}

.detail-value {
  padding: 8px 14px;
  font-size: 13px;
  color: #212529;
  border-bottom: 1px solid #e9ecef;
  display: flex;
  align-items: center;
  word-break: break-all;
}

.detail-row:last-child .detail-label,
.detail-row:last-child .detail-value {
  border-bottom: none;
}

.path-value {
  font-family: monospace;
  font-size: 12px;
}

.result-success { color: #198754; font-weight: 600; }
.result-danger  { color: #dc3545; }
.result-pending { color: #e6a23c; font-weight: 600; }

.json-container {
  padding: 10px 14px;
  position: relative;
}

.json-content {
  background: #1e1e2e;
  color: #cdd6f4;
  border-radius: 4px;
  padding: 10px 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  line-height: 1.6;
  margin: 0;
  overflow: hidden;
  white-space: pre-wrap;
  word-break: break-all;
  transition: max-height 0.2s ease;
}

.json-content.collapsed {
  max-height: 4.8em;
  overflow: hidden;
  mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
}

.expand-btn { margin-top: 6px; font-size: 12px; }

:deep(.jk)    { color: #89b4fa; }
:deep(.js)    { color: #a6e3a1; }
:deep(.jn)    { color: #fab387; }
:deep(.jb)    { color: #cba6f7; }
:deep(.jnull) { color: #6c7086; font-style: italic; }

:deep(.jk-changed) {
  background: rgba(250, 204, 21, 0.22);
  color: #fde68a !important;
  padding: 0 4px;
  border-radius: 3px;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(250, 204, 21, 0.45);
}

.section-after :deep(.jk-changed) {
  background: rgba(34, 197, 94, 0.25);
  color: #bbf7d0 !important;
  box-shadow: inset 0 0 0 1px rgba(34, 197, 94, 0.55);
}

.section-before :deep(.jk-changed) {
  background: rgba(239, 68, 68, 0.22);
  color: #fecaca !important;
  box-shadow: inset 0 0 0 1px rgba(239, 68, 68, 0.5);
}
</style>
