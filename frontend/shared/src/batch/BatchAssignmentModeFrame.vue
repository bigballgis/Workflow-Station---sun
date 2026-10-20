<template>
  <div class="mi-assignment-widget" :class="{ 'is-unconfigured': !configured }">
    <div class="mi-assignment-widget__title">{{ title ?? t('subTable.assignMode') }}</div>
    <slot v-if="!configured" name="warning" />
    <div v-else class="mi-assignment-widget__modes" :class="{ 'is-static': designCanvas }" role="radiogroup" :aria-label="t('subTable.assignMode')">
      <button v-for="option in modes" :key="option.value" type="button" role="radio" class="mi-assignment-mode-card"
        :aria-checked="mode === option.value" :aria-disabled="locked(option.value)"
        :class="{ 'is-selected': !designCanvas && mode === option.value, 'is-disabled': !designCanvas && locked(option.value) }" :tabindex="designCanvas || locked(option.value) ? -1 : 0" @click="select(option.value)">
        <span class="mi-assignment-mode-card__dot" />
        <span class="mi-assignment-mode-card__text">
          <span class="mi-assignment-mode-card__name">{{ t(option.label) }}</span>
          <span class="mi-assignment-mode-card__hint">{{ t(option.hint) }}</span>
        </span>
      </button>
    </div>
    <div v-if="configured" class="mi-assignment-widget__fields" :class="{ 'is-dropzone': designCanvas, 'is-empty': designCanvas && empty }">
      <slot />
      <div v-if="designCanvas && empty" class="mi-assignment-widget__empty"><slot name="empty" /></div>
    </div>
    <div v-if="configured && designCanvas && !empty" class="mi-assignment-widget__note"><slot name="note" /></div>
  </div>
</template>
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { BatchAssignmentConfig } from './workspaceTypes'
const props = withDefaults(defineProps<{ config?: BatchAssignmentConfig; mode: string; disabled?: boolean; configured?: boolean; designCanvas?: boolean; empty?: boolean; title?: string }>(), { configured: true })
const emit = defineEmits<{ (event: 'select', mode: 'person' | 'role'): void }>()
const { t } = useI18n()
const modes = [
  { value: 'person' as const, label: 'subTable.assignByPerson', hint: 'subTable.assignByPersonHint' },
  { value: 'role' as const, label: 'subTable.assignByRole', hint: 'subTable.assignByRoleHint' },
]
function locked(mode: 'person' | 'role') { return props.disabled || !(mode === 'person' ? props.config?.allowUser : props.config?.allowRole) }
function select(mode: 'person' | 'role') { if (!props.designCanvas && !locked(mode)) emit('select', mode) }
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
