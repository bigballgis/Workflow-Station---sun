<template>
  <div
    class="action-post-email-rules"
    data-testid="action-post-email-rules"
  >
    <el-divider>
      <span class="post-email-title-row">
        {{ t('action.postEmail.title') }}
        <DesignerHelpLink
          path="/action-email#conditions"
          :aria-label="t('action.postEmail.guideLinkAria')"
          test-id="action-post-email-guide-link"
        />
      </span>
    </el-divider>
    <el-alert
      v-if="unsupported"
      type="info"
      :closable="false"
      :title="t('action.postEmail.unsupportedHint')"
      style="margin-bottom: 12px"
    />
    <template v-if="!unsupported">
      <div
        v-for="(rule, index) in rules"
        :key="rule.id"
        class="email-rule-card"
      >
        <div class="email-rule-header">
          <el-input
            v-model="rule.name"
            size="small"
            class="rule-name-input"
            :placeholder="t('action.postEmail.ruleNamePlaceholder', { n: index + 1 })"
          />
          <el-switch
            v-model="rule.enabled"
            size="small"
            :aria-label="t('action.postEmail.ruleEnabled')"
          />
          <span class="rule-enabled-label">{{ t('action.postEmail.enabled') }}</span>
          <el-button
            size="small"
            link
            :disabled="rules.length >= maxRules"
            @click="duplicateRule(index)"
          >
            {{ t('action.postEmail.duplicateRule') }}
          </el-button>
          <el-button
            size="small"
            link
            type="danger"
            @click="removeRule(index)"
          >
            {{ t('common.delete') }}
          </el-button>
        </div>
        <el-form-item
          :label="t('action.postEmail.sendCondition')"
          class="send-condition-item"
        >
          <el-button
            type="primary"
            plain
            size="small"
            data-testid="action-post-email-edit-condition"
            @click="openConditionEditor(rule)"
          >
            {{ t('action.postEmail.editCondition') }}
          </el-button>
        </el-form-item>
        <ActionPostEmailConfig
          v-if="rule.enabled"
          :model-value="rule"
          :action-type="actionType"
          :function-unit-id="functionUnitId"
          embedded
          @update:model-value="patchRule(index, $event)"
        />
      </div>
      <el-button
        type="primary"
        plain
        :disabled="rules.length >= maxRules"
        data-testid="add-post-email-rule"
        @click="addRule"
      >
        {{ t('action.postEmail.addRule') }}
      </el-button>
      <p
        v-if="rules.length >= maxRules"
        class="rules-limit-hint"
      >
        {{ t('action.postEmail.rulesLimitHint', { max: maxRules }) }}
      </p>
    </template>

    <MainTableViewFilterEditor
      v-model="conditionDialogVisible"
      :filter-config="conditionEditing?.condition ?? null"
      :field-options="fieldOptions"
      :title="t('action.postEmail.editConditionTitle')"
      @save="onConditionSave"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import DesignerHelpLink from '@/components/designer/DesignerHelpLink.vue'
import MainTableViewFilterEditor from '@/components/designer/MainTableViewFilterEditor.vue'
import ActionPostEmailConfig, {
  type ActionPostEmailModel,
} from '@/components/designer/action-designer/ActionPostEmailConfig.vue'
import {
  MAX_POST_EMAIL_RULES,
  createEmptyPostEmailRule,
  type ActionPostEmailRule,
} from '@/utils/actionPostEmailRules'
import type { FilterConfig, FilterFieldOption } from '@/utils/mainTableViewFilter'

const UNSUPPORTED = new Set(['API_CALL', 'CUSTOM_SCRIPT', 'COMPOSITE'])

const props = defineProps<{
  modelValue: ActionPostEmailRule[]
  actionType: string
  functionUnitId: number
  fieldOptions: FilterFieldOption[]
  refreshFieldOptions?: () => Promise<void>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: ActionPostEmailRule[]]
}>()

const { t } = useI18n()
const maxRules = MAX_POST_EMAIL_RULES
const conditionDialogVisible = ref(false)
const conditionEditing = ref<ActionPostEmailRule | null>(null)

const rules = computed({
  get: () => props.modelValue ?? [],
  set: (val: ActionPostEmailRule[]) => emit('update:modelValue', val),
})

const unsupported = computed(() => UNSUPPORTED.has((props.actionType || '').toUpperCase()))

watch(unsupported, (isUnsupported) => {
  if (isUnsupported && rules.value.some(r => r.enabled)) {
    emit('update:modelValue', rules.value.map(r => ({ ...r, enabled: false })))
  }
})

function addRule() {
  if (rules.value.length >= maxRules) return
  emit('update:modelValue', [...rules.value, createEmptyPostEmailRule()])
}

function duplicateRule(index: number) {
  if (rules.value.length >= maxRules) return
  const source = rules.value[index]
  const copy = createEmptyPostEmailRule()
  Object.assign(copy, source, {
    id: createEmptyPostEmailRule().id,
    name: source.name ? `${source.name} (${t('action.postEmail.duplicateRule')})` : '',
    condition: source.condition
      ? JSON.parse(JSON.stringify(source.condition))
      : null,
  })
  const next = [...rules.value]
  next.splice(index + 1, 0, copy)
  emit('update:modelValue', next)
}

async function removeRule(index: number) {
  try {
    await ElMessageBox.confirm(
      t('action.postEmail.deleteRuleConfirm'),
      t('common.confirm'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  const next = rules.value.filter((_, i) => i !== index)
  emit('update:modelValue', next)
}

function patchRule(index: number, partial: ActionPostEmailModel) {
  const next = [...rules.value]
  next[index] = { ...next[index], ...partial }
  emit('update:modelValue', next)
}

async function openConditionEditor(rule: ActionPostEmailRule) {
  await props.refreshFieldOptions?.()
  conditionEditing.value = rule
  conditionDialogVisible.value = true
}

function onConditionSave(config: FilterConfig) {
  if (!conditionEditing.value) return
  const idx = rules.value.findIndex(r => r.id === conditionEditing.value?.id)
  if (idx < 0) return
  const next = [...rules.value]
  const empty = !config.conditions?.length && !config.groups?.length
  next[idx] = { ...next[idx], condition: empty ? null : config }
  emit('update:modelValue', next)
  conditionEditing.value = null
}

</script>

<style scoped>
.post-email-title-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.email-rule-card {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}

.email-rule-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.rule-name-input {
  width: 200px;
  flex-shrink: 0;
}

.rules-limit-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rule-enabled-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.send-condition-item {
  margin-bottom: 8px;
}

</style>
