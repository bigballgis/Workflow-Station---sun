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
    <!-- Both cards always show once configured — BPMN configuring only one mode
         locks the OTHER card rather than hiding it, so the reader can see the
         mode was deliberately fixed, not just that it's narrower. -->
    <div
      v-else
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
        :aria-disabled="isCardLocked(option.value) || isReadonly"
        class="mi-assignment-mode-card"
        :class="{
          'is-selected': !isDesignCanvas && mode === option.value,
          'is-disabled': !isDesignCanvas && (isCardLocked(option.value) || isReadonly),
        }"
        :tabindex="isDesignCanvas || isCardLocked(option.value) || isReadonly ? -1 : 0"
        @click="changeMode(option.value)"
      >
        <span class="mi-assignment-mode-card__dot" />
        <span class="mi-assignment-mode-card__text">
          <span class="mi-assignment-mode-card__name">{{ t(option.label) }}</span>
          <span class="mi-assignment-mode-card__hint">{{ t(option.hint) }}</span>
        </span>
      </button>
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
  isAssignModeSwitchable,
  isAssignmentConfigured,
  lockedAssignMode,
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
const switchable = computed(() => isAssignModeSwitchable(config.value))
const lockedMode = computed(() => lockedAssignMode(config.value))
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

/**
 * Readonly is set on the CONTAINER, but `input: false` means form-create forwards no
 * props here — so the flag is read back off the children the container owns, which
 * mapFormCreateRulesReadonlyDeep has already stamped `disabled` by cascade.
 *
 * A readonly block still shows which mode is active (that is information about the row);
 * it just refuses to switch, matching the disabled pickers underneath.
 */
const isReadonly = computed(() => {
  const children = props.formCreateInject?.children
  if (!Array.isArray(children) || children.length === 0) return false
  return children.every((child) => {
    const rule = child as { disabled?: unknown; props?: { disabled?: unknown } } | null
    return rule?.disabled === true || rule?.props?.disabled === true
  })
})

const modeOptions = [
  { value: 'person' as const, label: 'subTable.assignByPerson', hint: 'subTable.assignByPersonHint' },
  { value: 'role' as const, label: 'subTable.assignByRole', hint: 'subTable.assignByRoleHint' },
]

/** BPMN configured only one mode — the other card renders but is not selectable. */
function isCardLocked(value: AssignmentMode): boolean {
  return !switchable.value && value !== lockedMode.value
}

function changeMode(value: AssignmentMode): void {
  if (isReadonly.value || isCardLocked(value)) return
  if (value === mode.value) return
  injectedMode?.setMode(value)
}
</script>

<style scoped>
/* Mode-card presentation lives in styles/miAssignmentModeCard.scss, loaded once from
   styles/index.scss — Portal is supposed to predict what this preview shows, so the cards
   come from one file (see its header for what stays host-owned and why it is not imported
   into SFC style blocks). Being global, it also loses to the scoped `is-static` canvas
   overrides below, which is what we want.

   This widget's own FRAME: the container's fields render inside it via the default slot,
   so the block closes itself and moves as one unit when dragged. */
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

/* Designer canvas: the cards document the two runtime branches; there is nothing to
   pick at design time, so they read as labels rather than controls. */
.mi-assignment-widget__modes.is-static .mi-assignment-mode-card {
  cursor: default;
}

.mi-assignment-widget__modes.is-static .mi-assignment-mode-card:hover {
  border-color: #dcdfe6;
}

/* The locked-card (`is-disabled`) presentation comes from the shared stylesheet. */

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

/* Narrow viewports: stack the modes rather than crushing the hint text.
   Host-owned: it targets this widget's own modes container. */
@media (max-width: 560px) {
  .mi-assignment-widget__modes {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
