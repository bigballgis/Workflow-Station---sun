<template>
  <div class="mi-assignment-widget" :class="{ 'is-unconfigured': !configured }">
    <div class="mi-assignment-widget__title">{{ t('form.miAssignmentTitle') }}</div>
    <el-alert
      v-if="!configured"
      :title="t('form.miAssignmentUnconfigured')"
      type="warning"
      :closable="false"
      show-icon
    />
    <div
      v-else-if="showRadio"
      class="mi-assignment-widget__modes"
      :class="{ 'is-static': isDesignCanvas }"
      role="radiogroup"
      :aria-label="t('form.miAssignmentTitle')"
    >
      <button
        v-for="option in modeOptions"
        :key="option.value"
        type="button"
        role="radio"
        :aria-checked="mode === option.value"
        class="mi-assignment-mode-card"
        :class="{ 'is-selected': !isDesignCanvas && mode === option.value }"
        :tabindex="isDesignCanvas ? -1 : 0"
        @click="changeMode(option.value)"
      >
        <span class="mi-assignment-mode-card__dot" />
        <span class="mi-assignment-mode-card__text">
          <span class="mi-assignment-mode-card__name">{{ t(option.label) }}</span>
          <span class="mi-assignment-mode-card__hint">{{ t(option.hint) }}</span>
        </span>
      </button>
    </div>
    <!-- Single-mode contract: state the destination, nothing to choose. -->
    <div v-else class="mi-assignment-widget__single">
      <span class="mi-assignment-mode-card__name">
        {{ config?.allowRole ? t('subTable.assignByRole') : t('subTable.assignByPerson') }}
      </span>
      <span class="mi-assignment-mode-card__hint">
        {{ config?.allowRole ? t('subTable.assignByRoleHint') : t('subTable.assignByPersonHint') }}
      </span>
    </div>

    <!-- Reserved area for the assignment fields. On the designer canvas this is the
         container's drop zone: drag the imported assignee / BU / role fields in, drag
         them back out, reorder freely. At runtime it holds the active mode's pickers. -->
    <div
      v-if="configured"
      class="mi-assignment-widget__fields"
      :class="{ 'is-dropzone': isDesignCanvas, 'is-empty': isDesignCanvas && isEmpty }"
    >
      <slot />
      <div
        v-if="isDesignCanvas && isEmpty"
        class="mi-assignment-widget__empty"
      >
        {{ t('form.miAssignmentDropHint') }}
      </div>
    </div>

    <!-- Designer canvas only: say plainly that these fields travel with the component. -->
    <div v-if="configured && isDesignCanvas && !isEmpty" class="mi-assignment-widget__note">
      {{ t('form.miAssignmentOwnedFieldsNote') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  MI_ASSIGNMENT_CONFIG_KEY,
  MI_ASSIGNMENT_MODE_KEY,
  isAssignmentConfigured,
  shouldShowAssignModeRadio,
  type AssignmentMode,
} from '@/utils/miAssignmentConfig'

/**
 * The drag rule declares `input: false`, so form-create forwards NOTHING from
 * `rule.props` to this component — only its own internals. The active mode and its
 * setter therefore arrive via provide/inject (MI_ASSIGNMENT_MODE_KEY), the same channel
 * that already carries the BPMN contract in.
 *
 * `formCreateInject` is one of those internals form-create DOES pass: it carries the
 * component's nested rules, which is how the reserved area knows whether it is empty.
 */
const props = defineProps<{
  formCreateInject?: { children?: unknown[] }
}>()

const { t } = useI18n()
const injectedConfig = inject(MI_ASSIGNMENT_CONFIG_KEY, undefined)
const injectedMode = inject(MI_ASSIGNMENT_MODE_KEY, undefined)
const config = computed(() => injectedConfig?.value)
const configured = computed(() => isAssignmentConfigured(config.value))
const showRadio = computed(() => shouldShowAssignModeRadio(config.value))
const mode = computed(() =>
  injectedMode?.mode.value
  ?? (config.value?.allowRole && !config.value.allowUser ? 'role' : 'person'))

/**
 * Only the runtime/preview hosts provide MI_ASSIGNMENT_MODE_KEY. Its absence means we
 * are on the designer canvas, where every field the container holds is shown at once
 * (mode switching is a runtime concern) and the area acts as a drop zone.
 */
const isDesignCanvas = computed(() => !injectedMode)

/**
 * form-create hands a custom component its nested rules on `formCreateInject.children`;
 * an empty list means the reserved area has nothing in it yet, so show the drop hint
 * instead of a bare box. Falls back to "not empty" when the inject is unavailable, so a
 * missing internal never blanks out real content.
 */
const isEmpty = computed(() => {
  const children = props.formCreateInject?.children
  return Array.isArray(children) ? children.length === 0 : false
})

const modeOptions = [
  { value: 'person' as const, label: 'subTable.assignByPerson', hint: 'subTable.assignByPersonHint' },
  { value: 'role' as const, label: 'subTable.assignByRole', hint: 'subTable.assignByRoleHint' },
]

function changeMode(value: AssignmentMode): void {
  if (value === mode.value) return
  injectedMode?.setMode(value)
}
</script>

<style scoped>
/* Routing a row has two destinations — a named person, or a role pool in a BU.
   The modes are cards rather than bare radios so each one states what it will
   ask for, and the container's own fields render inside it via the default slot,
   so the block closes itself and moves as one unit when dragged.
   Kept in parity with the same block in user-portal SubTableAddDialog. */
.mi-assignment-widget {
  display: flex;
  flex-direction: column;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 6px;
  background: #f7f9fc;
}

.mi-assignment-widget.is-unconfigured {
  border: 1px solid var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

/* Spacing lives on the .el-col wrapper form-create puts around EACH child, not on
   .el-form-item: every item is the only child of its own col, so an
   `.el-form-item:last-child` rule matches them all and collapses every gap — which is
   how Business Unit and Role ended up glued together. The col-level rule leaves exactly
   one gap between siblings and none after the last. */
.mi-assignment-widget__fields :deep(.el-form-item) {
  margin-bottom: 0;
}

.mi-assignment-widget__fields :deep(.el-col + .el-col) {
  margin-top: 10px;
}

/* Straight-into-the-slot children (no col wrapper) get the same rhythm. */
.mi-assignment-widget__fields :deep(.el-form-item + .el-form-item) {
  margin-top: 10px;
}

/* form-create wraps each child in a row/col grid — keep it full width. */
.mi-assignment-widget__fields :deep(.el-row),
.mi-assignment-widget__fields :deep(.el-col) {
  width: 100%;
  max-width: 100%;
}

.mi-assignment-widget__title {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #8a9099;
}

.mi-assignment-widget__modes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.mi-assignment-widget__single {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 12px;
}

/* Designer canvas: the cards document the two runtime branches; there is nothing to
   pick at design time, so they read as labels rather than controls. */
.mi-assignment-widget__modes.is-static .mi-assignment-mode-card {
  cursor: default;
}

.mi-assignment-widget__modes.is-static .mi-assignment-mode-card:hover {
  border-color: #dcdfe6;
}

.mi-assignment-widget__note {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed #dcdfe6;
  font-size: 11px;
  line-height: 1.4;
  color: #9aa0a8;
}

/* Designer canvas: the reserved area reads as a drop target the author can fill,
   empty or not, so it is obvious that fields belong inside it. */
.mi-assignment-widget__fields.is-dropzone {
  padding: 8px;
  border: 1px dashed #c0c4cc;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.6);
}

.mi-assignment-widget__fields.is-dropzone.is-empty {
  padding: 0;
  border-style: dashed;
}

.mi-assignment-widget__empty {
  padding: 14px 12px;
  text-align: center;
  font-size: 12px;
  line-height: 1.5;
  color: #9aa0a8;
}

/* vuedraggable needs a minimum target height to accept the first drop. */
.mi-assignment-widget__fields.is-dropzone :deep(._fd-drag-box),
.mi-assignment-widget__fields.is-dropzone.is-empty {
  min-height: 46px;
}

/* Runtime/preview only: "person" shows one picker and "role" shows two, so toggling
   modes resized the whole dialog by ~40px. Reserve the taller branch's height up front
   so the cards swap their pickers without the surrounding form jumping.
   32px control + 10px gap per extra row; the designer canvas is exempt because it
   shows every field at once and must be free to grow as fields are dragged in. */
.mi-assignment-widget__fields:not(.is-dropzone) {
  min-height: 74px;
}

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

@media (max-width: 560px) {
  .mi-assignment-widget__modes {
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
