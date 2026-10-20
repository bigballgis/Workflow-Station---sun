<template>
  <BatchAssignmentModeFrame :config="config" :configured="configured" :mode="isDesignCanvas ? 'none' : mode"
    :disabled="isReadonly" :design-canvas="isDesignCanvas" :empty="isEmpty" :title="t('form.miAssignmentTitle')" @select="changeMode">
    <template #warning><el-alert :title="t('form.miAssignmentUnconfigured')" type="warning" :closable="false" show-icon /></template>
    <slot />
    <template #empty>{{ t('form.miAssignmentDropHint') }}</template>
    <template #note>{{ t('form.miAssignmentOwnedFieldsNote') }}</template>
  </BatchAssignmentModeFrame>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import BatchAssignmentModeFrame from '@platform-shared/batch/BatchAssignmentModeFrame.vue'
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
