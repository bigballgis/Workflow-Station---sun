<template>
  <div class="email-field-mapping-table">
    <div class="wizard-toolbar">
      <el-button size="small" type="primary" @click="emit('add-field')">
        {{ t('emailMonitor.wizard.addField') }}
      </el-button>
      <span v-if="lastSelection" class="wizard-selection">
        {{ t('emailMonitor.wizard.selected') }}: "{{ lastSelection }}"
        <el-button size="small" link type="primary" @click="emit('bind-selection')">
          {{ t('emailMonitor.wizard.bindSelection') }}
        </el-button>
      </span>
    </div>
    <div class="mapping-table-scroll">
    <el-table :data="fields" size="small" border>
      <el-table-column :label="t('emailMonitor.wizard.targetField')" min-width="180">
        <template #default="{ row }">
          <el-select
            v-model="row.target"
            size="small"
            filterable
            clearable
            :placeholder="targetPlaceholder(row)"
            class="target-field-select"
          >
            <el-option
              v-for="f in targetOptionsForRow(row, mainFieldOptions)"
              :key="f.fieldName"
              :label="fieldOptionLabel(f)"
              :value="f.fieldName"
            />
            <template v-if="emptyTargetHint(row)" #empty>
              <span class="el-select-dropdown__empty">{{ emptyTargetHint(row) }}</span>
            </template>
          </el-select>
        </template>
      </el-table-column>
      <el-table-column :label="t('emailMonitor.wizard.source')" width="170">
        <template #default="{ row }">
          <el-select v-model="row.source" size="small" @change="onSourceChange(row)">
            <el-option-group :label="t('emailMonitor.wizard.sourceGroupAttributes')">
              <el-option
                v-for="s in ATTRIBUTE_SOURCES"
                :key="s"
                :label="sourceLabel(s)"
                :value="s"
              />
            </el-option-group>
            <el-option-group :label="t('emailMonitor.wizard.sourceGroupBody')">
              <el-option
                v-for="s in BODY_SOURCES"
                :key="s"
                :label="sourceLabel(s)"
                :value="s"
              />
            </el-option-group>
            <el-option-group :label="t('emailMonitor.wizard.sourceGroupAttachments')">
              <el-option
                v-for="s in ATTACHMENT_SOURCES"
                :key="s"
                :label="sourceLabel(s)"
                :value="s"
              />
            </el-option-group>
            <el-option-group :label="t('emailMonitor.wizard.sourceGroupRawEml')">
              <el-option
                v-for="s in RAW_EML_SOURCES"
                :key="s"
                :label="sourceLabel(s)"
                :value="s"
              />
            </el-option-group>
          </el-select>
        </template>
      </el-table-column>
      <el-table-column :label="t('emailMonitor.wizard.type')" width="130">
        <template #default="{ row }">
          <el-select
            v-if="!isLockedAttributeSource(row.source)"
            v-model="row.type"
            size="small"
          >
            <el-option
              v-for="ty in typesForSource(row.source)"
              :key="ty"
              :label="typeLabel(ty)"
              :value="ty"
            />
          </el-select>
          <span v-else class="wizard-direct-label">{{ typeLabel('DIRECT') }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('emailMonitor.wizard.config')" min-width="220">
        <template #default="{ row }">
          <EmailFieldMappingRuleCell :row="row" />
        </template>
      </el-table-column>
      <el-table-column :label="t('emailMonitor.wizard.required')" min-width="100">
        <template #default="{ row }">
          <el-switch v-model="row.required" size="small" />
        </template>
      </el-table-column>
      <el-table-column :label="t('emailMonitor.wizard.preview')" min-width="120">
        <template #default="{ row }">
          <span class="wizard-preview-val">{{ previewFor(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column width="60">
        <template #default="{ $index }">
          <el-button size="small" link type="danger" @click="fields.splice($index, 1)">
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { ExtractionFieldRule } from '@/api/emailMonitor'
import EmailFieldMappingRuleCell from './EmailFieldMappingRuleCell.vue'
import type { SubTableFieldOption } from '@/composables/email/useProcessFormSubBindings'
import {
  ATTRIBUTE_SOURCES,
  ATTACHMENT_SOURCES,
  BODY_SOURCES,
  RAW_EML_SOURCES,
  isFileStoreSource,
  isAttachmentsSource,
  isRawEmlSource,
  isLockedAttributeSource,
  onSourceChange,
  previewAttachmentNames,
  previewRawEmlFilename,
  targetOptionsForRow,
  typesForSource,
} from '@/composables/email/emailExtractionFieldMapping'

const props = defineProps<{
  fields: ExtractionFieldRule[]
  mainFieldOptions: SubTableFieldOption[]
  lastSelection?: string
  attachmentPreview?: string
  sampleSubject?: string
  previewField: (row: ExtractionFieldRule) => string
}>()

const emit = defineEmits<{
  (e: 'add-field'): void
  (e: 'bind-selection'): void
}>()

const { t } = useI18n()

function sourceLabel(source: string): string {
  return t(`emailMonitor.wizard.source_${source}`)
}

function typeLabel(type: string): string {
  if (type === 'DIRECT') {
    return t('emailMonitor.wizard.type_DIRECT')
  }
  return type
}

function fieldOptionLabel(f: SubTableFieldOption): string {
  return f.displayName !== f.fieldName ? `${f.displayName} (${f.fieldName})` : f.fieldName
}

function targetPlaceholder(row: ExtractionFieldRule): string {
  return isFileStoreSource(row.source)
    ? t('emailMonitor.wizard.attachmentsTargetPlaceholder')
    : t('emailMonitor.wizard.targetFieldPlaceholder')
}

function emptyTargetHint(row: ExtractionFieldRule): string {
  if (isFileStoreSource(row.source) && props.mainFieldOptions.every((f) => String(f.dataType || '').toUpperCase() !== 'FILE')) {
    return t('emailMonitor.wizard.attachmentsTargetEmpty')
  }
  if (props.mainFieldOptions.length === 0) {
    return t('emailMonitor.wizard.mainTargetFieldEmpty')
  }
  return ''
}

function previewFor(row: ExtractionFieldRule): string {
  if (isAttachmentsSource(row.source)) {
    return previewAttachmentNames(props.attachmentPreview)
  }
  if (isRawEmlSource(row.source)) {
    return previewRawEmlFilename(props.sampleSubject)
  }
  return props.previewField(row)
}
</script>

<style scoped lang="scss">
.email-field-mapping-table {
  .mapping-table-scroll {
    overflow-x: auto;
  }

  :deep(.el-table) {
    min-width: 1100px;
  }

  .wizard-hint {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }
  .wizard-toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 8px;
  }
  .wizard-selection {
    font-size: 12px;
    color: #606266;
  }
  .wizard-preview-val {
    font-family: monospace;
    color: #409eff;
  }
  .wizard-direct-label {
    font-size: 12px;
    color: #606266;
  }
  .target-field-select {
    width: 100%;
  }
}
</style>
