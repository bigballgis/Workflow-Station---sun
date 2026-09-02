<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  fieldsHiddenByMode,
  isAssignModeSwitchable,
  isAssignmentConfigured,
  lockedAssignMode,
  resolveAssignModeFromRow,
  type AssignmentConfig,
  type AssignmentMode,
} from '@/utils/miAssignmentConfig'

/**
 * Assignment Mode block for the surfaces that render a sub-form through
 * {@link PortalFormFields} — the Inline Form widget and the Link Form dialog.
 *
 * SubTableAddDialog renders its own copy of this block from an ordered COLUMN list
 * (`dialogFormLayout`), which those two surfaces do not have: they render FormField
 * trees, where the designer's `miAssignment` marker arrives as a container field with
 * its assignee / BU / role rules nested as children. Without a branch for that type the
 * marker fell through to the leaf renderer and drew an empty label-less box, and its
 * children never rendered at all — the block looked like a blank frame with the
 * remaining fields flat beneath it.
 *
 * Keep the markup and CSS in parity with SubTableAddDialog's block and with Developer
 * Workstation's MiAssignmentPlaceholderWidget — Preview is supposed to predict this.
 */
const props = defineProps<{
  config?: AssignmentConfig
  /** The row being edited — decides which mode the block opens on. */
  row?: Record<string, unknown> | null
  /** Blank the other mode's values so a row never carries both. */
  readonly?: boolean
}>()

const emit = defineEmits<{
  (e: 'clear-fields', fields: string[]): void
}>()

const { t } = useI18n()

const configured = computed(() => isAssignmentConfigured(props.config))
const switchable = computed(() => isAssignModeSwitchable(props.config))
const lockedMode = computed(() => lockedAssignMode(props.config))

const assignMode = ref<AssignmentMode>('person')

const modeOptions = [
  { value: 'person' as const, label: 'subTable.assignByPerson', hint: 'subTable.assignByPersonHint' },
  { value: 'role' as const, label: 'subTable.assignByRole', hint: 'subTable.assignByRoleHint' },
]

/**
 * BPMN configured only one mode — always open on it, regardless of row data. Row-data
 * inference only makes sense when the user can actually switch. Mirrors the dialog.
 *
 * Watches the CONTRACT only: re-seeding on every row change would fight the user's own
 * choice as they type into the block.
 */
watch(
  () => props.config,
  () => {
    if (!configured.value || !props.config) return
    assignMode.value = switchable.value
      ? resolveAssignModeFromRow((props.row ?? {}) as Record<string, unknown>, props.config)
      : (lockedMode.value ?? 'person')
  },
  { immediate: true, deep: true },
)

/** BPMN configured only one mode — the other card renders but is not selectable. */
function isModeCardDisabled(value: AssignmentMode): boolean {
  return !switchable.value && lockedMode.value !== value
}

function selectMode(value: AssignmentMode): void {
  if (props.readonly || isModeCardDisabled(value)) return
  if (value === assignMode.value) return
  assignMode.value = value
  const config = props.config
  if (!config) return
  emit('clear-fields', [...fieldsHiddenByMode(value, config)])
}

/** Fields the other mode owns — hidden while this mode is active. */
const hiddenFields = computed(() =>
  configured.value && props.config
    ? fieldsHiddenByMode(assignMode.value, props.config)
    : new Set<string>())

defineExpose({ mode: assignMode, hiddenFields })
</script>

<template>
  <div
    v-if="configured"
    class="mi-assignment-block"
  >
    <div class="mi-assignment-block__title">
      {{ t('subTable.assignMode') }}
    </div>
    <!-- Both cards always show: BPMN configuring only one mode LOCKS the other rather
         than hiding it, so the reader sees the mode was deliberately fixed. -->
    <div
      class="mi-assignment-block__modes"
      role="radiogroup"
      :aria-label="t('subTable.assignMode')"
    >
      <button
        v-for="option in modeOptions"
        :key="option.value"
        type="button"
        role="radio"
        :aria-checked="assignMode === option.value"
        :aria-disabled="isModeCardDisabled(option.value) || readonly"
        class="mi-assignment-mode-card"
        :class="{
          'is-selected': assignMode === option.value,
          'is-disabled': isModeCardDisabled(option.value) || readonly,
        }"
        @click="selectMode(option.value)"
      >
        <span class="mi-assignment-mode-card__dot" />
        <span class="mi-assignment-mode-card__text">
          <span class="mi-assignment-mode-card__name">{{ t(option.label) }}</span>
          <span class="mi-assignment-mode-card__hint">{{ t(option.hint) }}</span>
        </span>
      </button>
    </div>

    <!-- The active mode's pickers, so the block always shows the consequence of the
         choice instead of an empty frame. -->
    <div class="mi-assignment-block__fields">
      <slot :hidden-fields="hiddenFields" />
    </div>
  </div>
</template>

<style scoped>
/* Routing a row has two destinations — a named person, or a role pool in a BU. The modes
   are cards rather than bare radios so each states what it will ask for, and the owned
   pickers render inside the same box so the block closes itself.
   Kept in parity with SubTableAddDialog and DW MiAssignmentPlaceholderWidget. */
.mi-assignment-block {
  margin: 4px 0 18px;
  padding: 12px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #f7f9fc;
}

.mi-assignment-block__title {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #8a9099;
}

.mi-assignment-block__modes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

/* "person" owns one picker, "role" owns two, so toggling modes resized the surrounding
   form by ~40px. Reserve the taller branch's height so nothing below the block moves. */
.mi-assignment-block__fields {
  min-height: 74px;
}

.mi-assignment-block__fields :deep(.el-form-item) {
  margin-bottom: 0;
}

.mi-assignment-block__fields :deep(.el-form-item + .el-form-item) {
  margin-top: 10px;
}

/* The host renders fields inside an el-row grid — keep them full width in the block. */
.mi-assignment-block__fields :deep(.el-col) {
  width: 100%;
  max-width: 100%;
  flex: 0 0 100%;
  padding: 0;
}

/* Mode card: the rail on the left is the only saturated element in the block. */
.mi-assignment-mode-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  position: relative;
  margin: 0;
  padding: 10px 12px 10px 14px;
  overflow: hidden;
  font: inherit;
  text-align: left;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 5px;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.mi-assignment-mode-card::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  background: transparent;
  transition: background-color 0.15s ease;
}

.mi-assignment-mode-card:hover {
  border-color: #b6bcc4;
}

.mi-assignment-mode-card.is-selected {
  border-color: #c8102e;
  box-shadow: 0 1px 3px rgba(200, 16, 46, 0.12);
}

.mi-assignment-mode-card.is-selected::before {
  background: #c8102e;
}

/* BPMN configured only one mode (or the form is read-only) — the card stays visible but
   locked, so the reader sees the mode was deliberately fixed. */
.mi-assignment-mode-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.mi-assignment-mode-card.is-disabled:hover {
  border-color: #dcdfe6;
}

.mi-assignment-mode-card:focus-visible {
  outline: 2px solid #c8102e;
  outline-offset: 2px;
}

.mi-assignment-mode-card__dot {
  flex: none;
  width: 14px;
  height: 14px;
  margin-top: 2px;
  border: 1px solid #c0c4cc;
  border-radius: 50%;
  background: #fff;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.mi-assignment-mode-card.is-selected .mi-assignment-mode-card__dot {
  border-color: #c8102e;
  box-shadow: inset 0 0 0 3px #c8102e;
}

.mi-assignment-mode-card__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.mi-assignment-mode-card__name {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.3;
  color: #606266;
}

.mi-assignment-mode-card.is-selected .mi-assignment-mode-card__name {
  color: #1f2329;
}

.mi-assignment-mode-card__hint {
  font-size: 11px;
  line-height: 1.35;
  color: #9aa0a8;
  /* Wrap rather than clip — the hint is what tells you which picker you get. */
  white-space: normal;
  overflow-wrap: anywhere;
}

/* Narrow viewports (mobile): stack the modes rather than crushing the hint text. */
@media (max-width: 560px) {
  .mi-assignment-block__modes {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (prefers-reduced-motion: reduce) {
  .mi-assignment-mode-card,
  .mi-assignment-mode-card::before,
  .mi-assignment-mode-card__dot {
    transition: none;
  }
}
</style>
