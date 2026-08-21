<!--
  Function Unit Table Design editor. Admin Relation Tables keep a separate copy at
  frontend/admin-center/src/components/relation-table/ComputedFieldEditor.vue
  (no aggregate scope; RT has no sub-tables). Shared parse/serialize lives in
  frontend/shared/src/computedFieldConfig.ts.
-->
<template>
  <div class="computed-field-editor">
    <el-checkbox
      v-model="enabled"
      :disabled="disabled"
      @change="onToggle"
    />
    <el-tooltip
      v-if="enabled"
      :content="summaryLabel"
      placement="top"
      :show-after="400"
    >
      <el-button
        size="small"
        circle
        class="computed-config-btn"
        :type="isConfigured ? 'primary' : 'warning'"
        plain
        @click="openDialog"
      >
        <el-icon><EditPen /></el-icon>
      </el-button>
    </el-tooltip>

    <el-dialog
      v-model="dialogVisible"
      width="640px"
      top="4vh"
      class="computed-field-dialog"
      append-to-body
      destroy-on-close
      @open="syncFromProps"
    >
      <template #header>
        <div class="cf-dialog-title">
          <span class="el-dialog__title">{{ t('table.computedField.dialogTitle') }}</span>
          <el-tooltip
            :content="guideUrl"
            placement="bottom"
            :show-after="200"
          >
            <a
              class="cf-guide-link"
              data-testid="computed-field-guide-link"
              :href="guideUrl"
              target="_blank"
              rel="noopener noreferrer"
              :aria-label="t('table.computedField.guideLinkAria')"
              @click.stop
            >
              <el-icon><QuestionFilled /></el-icon>
            </a>
          </el-tooltip>
        </div>
      </template>
      <p class="computed-lede">
        {{ t('table.computedField.dialogHint') }}
      </p>

      <section class="cf-block">
        <h3 class="cf-heading">
          {{ t('table.computedField.formula') }}
        </h3>
        <el-input
          v-model="localSource"
          type="textarea"
          :rows="4"
          class="formula-input"
          :placeholder="tableType === 'MAIN'
            ? t('table.computedField.formulaPlaceholder')
            : t('table.computedField.formulaPlaceholderSub')"
          spellcheck="false"
        />
        <p
          v-if="parseMessage"
          class="parse-error"
        >
          {{ parseMessage }}
        </p>
      </section>

      <section class="cf-block">
        <h3 class="cf-heading">
          {{ t('table.computedField.scope') }}
        </h3>
        <div
          class="choice-row"
          role="radiogroup"
        >
          <button
            type="button"
            class="choice"
            :class="{ on: localScope === 'row' }"
            @click="localScope = 'row'"
          >
            <span class="choice-title">{{ t('table.computedField.scopeRow') }}</span>
            <span class="choice-hint">{{ t('table.computedField.scopeRowHint') }}</span>
          </button>
          <button
            type="button"
            class="choice"
            :class="{ on: localScope === 'aggregate' }"
            :disabled="tableType !== 'MAIN'"
            @click="localScope = 'aggregate'"
          >
            <span class="choice-title">{{ t('table.computedField.scopeAggregate') }}</span>
            <span class="choice-hint">{{ t('table.computedField.scopeAggregateHint') }}</span>
          </button>
        </div>
        <p
          v-if="tableType !== 'MAIN'"
          class="field-note"
        >
          {{ t('table.computedField.subTableScopeNote') }}
        </p>
      </section>

      <section class="cf-block">
        <h3 class="cf-heading">
          {{ t('table.computedField.onError') }}
        </h3>
        <div
          class="choice-row"
          role="radiogroup"
        >
          <button
            type="button"
            class="choice"
            :class="{ on: localOnError === 'fail' }"
            @click="localOnError = 'fail'"
          >
            <span class="choice-title">{{ t('table.computedField.onErrorFail') }}</span>
            <span class="choice-hint">{{ t('table.computedField.onErrorFailHint') }}</span>
          </button>
          <button
            type="button"
            class="choice"
            :class="{ on: localOnError === 'null' }"
            @click="localOnError = 'null'"
          >
            <span class="choice-title">{{ t('table.computedField.onErrorNull') }}</span>
            <span class="choice-hint">{{ t('table.computedField.onErrorNullHint') }}</span>
          </button>
        </div>
      </section>

      <section class="cf-block cf-preview">
        <h3 class="cf-heading">
          {{ t('table.computedField.preview') }}
        </h3>
        <p
          v-if="!previewDeps.length"
          class="preview-empty"
        >
          {{ localScope === 'aggregate'
            ? t('table.computedField.previewAggregateNote')
            : t('table.computedField.previewEmpty') }}
        </p>
        <div
          v-else
          class="preview-deps"
        >
          <label
            v-for="dep in previewDeps"
            :key="dep"
            class="preview-dep-row"
          >
            <span class="preview-dep-name">{{ dep }}</span>
            <el-input
              v-model="sampleRow[dep]"
              size="small"
              @input="maybeAutoPreview"
            />
          </label>
        </div>
        <div class="preview-actions">
          <el-button
            size="small"
            :disabled="!localSource.trim()"
            @click="runPreview"
          >
            {{ t('table.computedField.tryEvaluate') }}
          </el-button>
          <span
            v-if="previewResult"
            class="preview-result"
          >
            <span class="preview-result-label">{{ t('table.computedField.previewResultLabel') }}</span>
            {{ previewResult }}
          </span>
        </div>
      </section>

      <template #footer>
        <el-button @click="dialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          @click="confirmDialog"
        >
          {{ t('table.computedField.applyFormula') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { EditPen, QuestionFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  buildComputedFieldDefinition,
  computedFieldSummary,
  parseComputedFieldFromApi,
  type ComputedFieldDefinition,
  type ComputedFieldOnError,
  type ComputedFieldScope,
} from '@/utils/computedFieldConfig'
import { evaluateAst, toText, type AstNode, type EvaluationContext } from '@platform-shared/computedField'
import { computedFieldGuideAbsoluteUrl } from '@/utils/computedFieldGuide'

const props = defineProps<{
  isComputed?: boolean
  computedField?: Record<string, unknown> | ComputedFieldDefinition | null
  computedFieldJson?: Record<string, unknown> | null
  disabled?: boolean
  tableType?: string
}>()

const emit = defineEmits<{
  'update:isComputed': [value: boolean]
  'update:computedField': [value: ComputedFieldDefinition | undefined]
}>()

const { t } = useI18n()
const guideUrl = computedFieldGuideAbsoluteUrl()

const enabled = ref(!!props.isComputed)
const dialogVisible = ref(false)
const localSource = ref('')
const localScope = ref<ComputedFieldScope>('row')
const localOnError = ref<ComputedFieldOnError>('fail')
const parseMessage = ref('')
const previewResult = ref('')
const previewDeps = ref<string[]>([])
const sampleRow = reactive<Record<string, string>>({})

const tableType = computed(() => String(props.tableType ?? 'MAIN').toUpperCase())

const currentDefinition = computed(() =>
  parseComputedFieldFromApi(props.computedField ?? props.computedFieldJson ?? undefined),
)

const isConfigured = computed(() => !!currentDefinition.value?.source?.trim())

const summaryLabel = computed(() => {
  const summary = computedFieldSummary(currentDefinition.value)
  return summary || t('table.computedField.configure')
})

watch(
  () => props.isComputed,
  (v) => { enabled.value = !!v },
)

watch(localSource, () => {
  refreshDepsFromSource()
})

watch(localScope, () => {
  refreshDepsFromSource()
})

function syncFromProps() {
  const def = currentDefinition.value
  localSource.value = def?.source ?? ''
  localScope.value = tableType.value === 'MAIN' ? (def?.scope ?? 'row') : 'row'
  localOnError.value = def?.onError ?? 'fail'
  parseMessage.value = ''
  previewResult.value = ''
  Object.keys(sampleRow).forEach((k) => delete sampleRow[k])
  refreshDepsFromSource()
}

function onToggle(val: boolean | string | number) {
  const on = val === true
  emit('update:isComputed', on)
  if (!on) {
    emit('update:computedField', undefined)
    parseMessage.value = ''
    previewResult.value = ''
    return
  }
  if (!isConfigured.value) {
    openDialog()
  }
}

function openDialog() {
  dialogVisible.value = true
}

function refreshDepsFromSource() {
  const source = localSource.value.trim()
  if (!source) {
    previewDeps.value = []
    parseMessage.value = ''
    previewResult.value = ''
    return
  }
  const built = buildComputedFieldDefinition(source, localScope.value, localOnError.value)
  if (!built.ok) {
    return
  }
  parseMessage.value = ''
  const next = built.value.dependsOn.filter(d => tableType.value !== 'MAIN' || !d.includes('.'))
  previewDeps.value = next
  for (const key of Object.keys(sampleRow)) {
    if (!next.includes(key)) delete sampleRow[key]
  }
  for (const dep of next) {
    if (!(dep in sampleRow)) sampleRow[dep] = ''
  }
  maybeAutoPreview()
}

function maybeAutoPreview() {
  if (!previewDeps.value.length) return
  if (previewDeps.value.some(dep => !String(sampleRow[dep] ?? '').trim())) {
    previewResult.value = ''
    return
  }
  runPreview()
}

function formatOutcome(outcome: ReturnType<typeof evaluateAst>): string {
  if (!outcome.ok) return `#ERR ${outcome.error.code}: ${outcome.error.message}`
  if (outcome.value.kind === 'blank') return t('table.computedField.previewBlank')
  return toText(outcome.value)
}

function buildPreviewContext(): EvaluationContext {
  const row: Record<string, unknown> = {}
  const parents: Record<string, Record<string, unknown>> = {}
  for (const [key, val] of Object.entries(sampleRow)) {
    const dot = key.indexOf('.')
    if (dot > 0) {
      const table = key.slice(0, dot).toLowerCase()
      const column = key.slice(dot + 1)
      if (!parents[table]) parents[table] = {}
      parents[table][column] = val
    } else {
      row[key] = val
    }
  }
  return { row, subTables: {}, parents }
}

function hasParentFieldRef(node: AstNode): boolean {
  switch (node.type) {
    case 'field':
      return !!node.table
    case 'unary':
      return hasParentFieldRef(node.operand)
    case 'binary':
      return hasParentFieldRef(node.left) || hasParentFieldRef(node.right)
    case 'call':
      return node.args.some(hasParentFieldRef)
    default:
      return false
  }
}

function runPreview() {
  const built = buildComputedFieldDefinition(localSource.value, localScope.value, localOnError.value)
  if (!built.ok) {
    parseMessage.value = built.position != null
      ? `${built.message} (@${built.position})`
      : built.message
    previewResult.value = ''
    return
  }
  parseMessage.value = ''
  previewResult.value = formatOutcome(evaluateAst(built.value.ast, buildPreviewContext()))
}

function confirmDialog() {
  const scope = tableType.value === 'MAIN' ? localScope.value : 'row'
  const built = buildComputedFieldDefinition(localSource.value, scope, localOnError.value)
  if (!built.ok) {
    parseMessage.value = built.position != null
      ? `${built.message} (@${built.position})`
      : built.message
    ElMessage.warning(t('table.computedField.invalidFormula'))
    return
  }
  if (tableType.value === 'MAIN' && hasParentFieldRef(built.value.ast)) {
    parseMessage.value = t('table.computedField.parentRefNotAllowedOnMain')
    ElMessage.warning(t('table.computedField.parentRefNotAllowedOnMain'))
    return
  }
  emit('update:isComputed', true)
  emit('update:computedField', built.value)
  dialogVisible.value = false
  ElMessage.success(t('table.computedField.savedLocally'))
}
</script>

<style scoped>
.computed-field-editor {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.computed-config-btn {
  width: 24px;
  height: 24px;
  padding: 0;
}
.cf-dialog-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding-right: 28px;
}
.cf-guide-link {
  display: inline-flex;
  align-items: center;
  color: var(--el-color-primary);
  font-size: 16px;
  line-height: 1;
}
.cf-guide-link:hover,
.cf-guide-link:focus-visible {
  color: var(--el-color-primary-light-3);
}
.computed-lede {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.45;
  color: var(--ws-text-secondary);
}
.cf-block {
  margin-bottom: 12px;
}
.cf-heading {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ws-text-muted);
}
.formula-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 14px;
  line-height: 1.55;
  background: var(--ws-canvas);
}
.parse-error {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-color-danger);
}
.choice-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.choice {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 8px 10px;
  text-align: left;
  font: inherit;
  border: 1px solid var(--ws-line-strong);
  border-radius: var(--ws-radius-input);
  background: var(--ws-card-bg);
  color: var(--ws-text);
  cursor: pointer;
}
.choice:hover:not(:disabled) {
  border-color: var(--ws-ink);
}
.choice.on {
  border-color: var(--ws-ink);
  box-shadow: inset 3px 0 0 var(--primary-color);
  background: var(--primary-soft);
}
.choice:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.choice-title {
  font-size: 13px;
  font-weight: 600;
}
.choice-hint {
  font-size: 12px;
  line-height: 1.4;
  color: var(--ws-text-secondary);
}
.field-note,
.preview-empty {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--ws-text-secondary);
}
.cf-preview {
  padding: 12px;
  border-radius: var(--ws-radius-input);
  background: var(--ws-canvas);
}
.preview-deps {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.preview-dep-row {
  display: grid;
  grid-template-columns: minmax(96px, 140px) 1fr;
  gap: 8px;
  align-items: center;
}
.preview-dep-name {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--ws-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.preview-result {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  color: var(--ws-ink);
}
.preview-result-label {
  margin-right: 6px;
  font-family: inherit;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ws-text-muted);
}
</style>

<style>
.computed-field-dialog .el-dialog__body {
  padding-top: 8px;
  padding-bottom: 8px;
}
</style>
