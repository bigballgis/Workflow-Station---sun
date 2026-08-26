<template>
  <el-collapse-item name="emailMonitor">
    <template #title>
      <span class="start-email-monitor-title">
        {{ t('emailMonitor.startEvent.panelTitle') }}
        <DesignerHelpLink
          path="/email-monitor"
          :aria-label="t('emailMonitor.startEvent.guideLinkAria')"
          test-id="start-event-monitor-guide-link"
        />
      </span>
    </template>
    <div v-loading="loading" class="start-email-monitor">
      <el-alert
        v-if="templates.length === 0"
        type="warning"
        :closable="false"
        :title="t('emailMonitor.startEvent.noTemplate')"
        style="margin-bottom: 12px;"
      />

      <el-form label-position="top" size="small">
        <el-form-item :label="t('emailMonitor.startEvent.enable')">
          <el-switch
            :model-value="enabled"
            @change="onEnabledChange"
          />
          <div class="form-tip">{{ t('emailMonitor.startEvent.enableHint') }}</div>
        </el-form-item>

        <template v-if="enabled">
          <el-form-item :label="t('emailMonitor.startEvent.boundProcess')">
            <el-input :model-value="processDefinitionKey" disabled />
          </el-form-item>
          <el-form-item :label="t('emailMonitor.startEvent.boundEvent')">
            <el-input :model-value="startEventId" disabled />
          </el-form-item>

          <el-form-item :label="t('emailMonitor.startEvent.selectTemplate')" required>
            <el-select
              v-model="templateRuleId"
              style="width: 100%;"
              :placeholder="t('emailMonitor.startEvent.selectTemplatePlaceholder')"
            >
              <el-option
                v-for="rule in templates"
                :key="rule.id"
                :label="rule.name"
                :value="rule.id"
              />
            </el-select>
            <div class="form-tip">{{ t('emailMonitor.startEvent.configureInMonitorsTab') }}</div>
          </el-form-item>

          <el-form-item v-if="selectedTemplate" :label="t('emailMonitor.connection')">
            <el-input :model-value="connectionLabel" disabled />
          </el-form-item>

          <el-form-item v-if="selectedTemplate" :label="t('emailMonitor.startEvent.extractionStatus')">
            <el-tag :type="hasExtraction ? 'success' : 'warning'" size="small">
              {{
                hasExtraction
                  ? t('emailMonitor.startEvent.extractionConfigured')
                  : t('emailMonitor.startEvent.extractionMissing')
              }}
            </el-tag>
          </el-form-item>

          <el-form-item :label="t('emailMonitor.filterFrom')">
            <el-input
              v-model="filterFrom"
              :placeholder="t('emailMonitor.filterFromPlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="t('emailMonitor.filterSubject')">
            <el-input
              v-model="filterSubject"
              :placeholder="t('emailMonitor.filterSubjectPlaceholder')"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveBinding">
              {{ t('emailMonitor.startEvent.saveBinding') }}
            </el-button>
          </el-form-item>
        </template>
      </el-form>
    </div>
  </el-collapse-item>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import DesignerHelpLink from '@/components/designer/DesignerHelpLink.vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { useStartEventEmailMonitor } from '@/composables/eventProperties/useStartEventEmailMonitor'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
  updateExtProp: (name: string, value: unknown) => void
}>()

const {
  enabled,
  saving,
  loading,
  templates,
  templateRuleId,
  selectedTemplate,
  connectionLabel,
  hasExtraction,
  processDefinitionKey,
  startEventId,
  filterFrom,
  filterSubject,
  saveBinding,
  onEnabledChange
} = useStartEventEmailMonitor(
  props,
  props.updateExtProp,
  t
)
</script>

<style lang="scss" scoped>
.start-email-monitor-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.start-email-monitor {
  .form-tip {
    font-size: 11px;
    color: #909399;
    margin-top: 4px;
    line-height: 1.4;
  }
}
</style>
