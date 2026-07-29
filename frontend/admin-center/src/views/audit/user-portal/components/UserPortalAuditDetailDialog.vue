<template>
  <el-dialog
    :model-value="visible"
    :title="t('upAudit.viewDetail')"
    width="720px"
    destroy-on-close
    @update:model-value="$emit('update:visible', $event)"
  >
    <div v-if="record" class="audit-detail">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item :label="t('audit.time')">
          {{ formatTimestamp(record.timestamp) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('audit.operator')">
          {{ record.userName || record.userId }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('upAudit.functionUnit')">
          {{ record.functionUnitName || record.functionUnitCode || '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('upAudit.changeType')">
          <el-tag :type="changeTypeTag(record.changeType)" size="small">
            {{ changeTypeText(t, record.changeType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('upAudit.processInstanceId')">
          <div>{{ record.processTitle || record.processInstanceId }}</div>
          <div v-if="record.processTitle && record.processInstanceId" class="muted-id">
            {{ record.processInstanceId }}
          </div>
        </el-descriptions-item>
        <el-descriptions-item :label="t('upAudit.stage')">
          {{ record.stageName || record.stageId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('upAudit.fieldName')">
          {{ record.fieldLabel || record.fieldName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('upAudit.subTableName')">
          {{ record.subTableDisplayName || record.subTableName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="record.rowIdentifier" :label="t('upAudit.rowIdentifier')">
          {{ record.rowIdentifier }}
        </el-descriptions-item>
        <el-descriptions-item v-if="record.taskInstanceId" :label="t('upAudit.taskInstanceId')">
          {{ record.taskInstanceId }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="value-compare">
        <div class="value-block">
          <div class="value-label">{{ t('upAudit.oldValue') }}</div>
          <div class="value-content">{{ record.oldValue || '-' }}</div>
        </div>
        <div class="value-block">
          <div class="value-label">{{ t('upAudit.newValue') }}</div>
          <div class="value-content">{{ record.newValue || '-' }}</div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">
        {{ t('common.close') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { UserPortalAuditRecord } from '@/api/user-portal-audit'
import { changeTypeTag, changeTypeText, formatTimestamp } from '@/composables/modules/useUserPortalAudit'

defineProps<{
  visible: boolean
  record: UserPortalAuditRecord | null
}>()

defineEmits<{
  'update:visible': [value: boolean]
}>()

const { t } = useI18n()
</script>

<style scoped>
.audit-detail {
  max-height: 60vh;
  overflow-y: auto;
}

.value-compare {
  display: flex;
  gap: 16px;
  margin-top: 16px;
}

.value-block {
  flex: 1;
  min-width: 0;
}

.value-label {
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.value-content {
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 10px 12px;
  font-family: 'Cascadia Code', 'JetBrains Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow-y: auto;
}

.muted-id {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  word-break: break-all;
}
</style>
