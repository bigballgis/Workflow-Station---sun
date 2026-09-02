<template>
  <div
    class="mi-assignment-preview-scope"
    :style="labelMinWidth ? { '--mi-label-min-width': labelMinWidth } : undefined"
  >
    <slot :rule="shapedRule" />
  </div>
</template>

<script setup lang="ts">
import { computed, provide, ref, watch } from 'vue'
import {
  MI_ASSIGNMENT_CONFIG_KEY,
  MI_ASSIGNMENT_MODE_KEY,
  isAssignModeSwitchable,
  isAssignmentConfigured,
  lockedAssignMode,
  resolveAssignModeFromRow,
  type AssignmentConfig,
  type AssignmentMode,
} from '@/utils/miAssignmentConfig'
import {
  filterRuleByAssignMode,
  injectDemoBuRoleOptions,
  measureAssignmentLabelMinWidth,
} from './miAssignmentRuleMode'
import { cloneFormRules } from '@/utils/formDesigner'

/**
 * Runtime-shaped Assignment Mode scope for ONE sub-table binding, used by the Inline
 * Form preview block.
 *
 * Preview renders every inline block at once, so the mode cannot live in the page: each
 * block gets its own instance, binding both the BPMN contract and the active mode to the
 * sub-table it actually belongs to. Providing MI_ASSIGNMENT_MODE_KEY is also what tells
 * the widget it is a runtime surface rather than the designer canvas — that is the whole
 * difference between "two dead cards with all three pickers under them" and the dialog's
 * behaviour: one card selected, only its picker shown, clicking the other card switches.
 *
 * The shaped rule is exposed through the default slot rather than rendered here, so the
 * host keeps ownership of the form-create element (its key, model and option).
 */
const props = defineProps<{
  rule: any[]
  assignmentConfig?: AssignmentConfig
  /** Row backing this inline block — decides which mode it opens on. */
  row?: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'clear-fields', fields: string[]): void
}>()

const configRef = computed(() => props.assignmentConfig)
provide(MI_ASSIGNMENT_CONFIG_KEY, configRef)

const assignMode = ref<AssignmentMode>('person')

/**
 * BPMN configured only one mode — always open on it, regardless of row data.
 * Row-data inference only makes sense when the user can actually switch modes.
 * Mirrors SubTableFormDialog.
 */
function resetModeFromRow(): void {
  const config = props.assignmentConfig
  if (!config || !isAssignmentConfigured(config)) return
  assignMode.value = isAssignModeSwitchable(config)
    ? resolveAssignModeFromRow(props.row ?? {}, config)
    : (lockedAssignMode(config) ?? 'person')
}

/**
 * Re-open on the row's mode only when the CONTRACT changes — never on `props.rule`.
 *
 * The host passes `visiblePreviewRules(...)`, which builds a fresh array on every render,
 * so watching the rule fires on each re-render. Since selecting a mode re-renders, that
 * watch reset the mode straight back and the cards appeared frozen. Row identity is not
 * watched either: it seeds the initial mode, but re-seeding after every keystroke in the
 * block would fight the user's own choice.
 */
watch(
  () => props.assignmentConfig,
  () => resetModeFromRow(),
  { immediate: true, deep: true },
)

provide(MI_ASSIGNMENT_MODE_KEY, {
  mode: assignMode,
  setMode: (mode: AssignmentMode) => changeMode(mode),
})

function changeMode(mode: AssignmentMode): void {
  const config = props.assignmentConfig
  // Defense in depth: the widget already prevents clicking the locked card, but
  // the injected setter itself must not trust an unexpected call either.
  if (config && !isAssignModeSwitchable(config) && mode !== lockedAssignMode(config)) return
  if (mode === assignMode.value) return
  assignMode.value = mode
  if (!config) return
  emit('clear-fields', [...fieldsHiddenFor(mode, config)])
}

function fieldsHiddenFor(mode: AssignmentMode, config: AssignmentConfig): Set<string> {
  return mode === 'person'
    ? new Set([config.roleField, config.buField].filter((f): f is string => !!f))
    : new Set([config.assigneeField].filter((f): f is string => !!f))
}

/**
 * Clone before shaping: the demo BU/Role options are written onto the rules in place,
 * and the incoming rule tree is the designer's live binding.
 */
const shapedRule = computed(() => {
  const config = props.assignmentConfig
  if (!config || !isAssignmentConfigured(config)) return props.rule
  const shaped = filterRuleByAssignMode(
    cloneFormRules(props.rule || []) as any[],
    assignMode.value,
    config,
  )
  injectDemoBuRoleOptions(shaped, config)
  return shaped
})

const labelMinWidth = computed(() =>
  measureAssignmentLabelMinWidth(props.rule || [], props.assignmentConfig))
</script>

<style scoped>
/* Hold the label column at the widest label either assignment mode can show, so toggling
   modes does not move every other row's input edge. Element Plus sets
   `min-width: max-content` on labels in auto mode, so this needs the extra .el-form
   specificity (and !important) to win; `labelWidth: auto` still governs above the floor,
   so longer labels never wrap. Mirrors SubTableFormDialog. */
.mi-assignment-preview-scope :deep(.el-form .el-form-item__label) {
  min-width: var(--mi-label-min-width, 0) !important;
}

/* The widget draws its own complete frame — only its width and the gap below it are the
   host's business. Mirrors SubTableFormDialog / user-portal SubTableAddDialog. */
.mi-assignment-preview-scope :deep(.mi-assignment-widget) {
  width: 100%;
  box-sizing: border-box;
  margin-bottom: 18px;
}
</style>
