<template>
  <slot />
</template>

<script setup lang="ts">
import { computed, provide } from 'vue'
import { MI_ASSIGNMENT_CONFIG_KEY, type AssignmentConfig } from '@/utils/miAssignmentConfig'

/**
 * Scopes the multi-instance assignment contract to one sub-table binding.
 *
 * <p>FormDesigner provides the contract of whichever sub-table tab is open. That is
 * right on the canvas, where one sub-table is edited at a time, but Preview renders
 * every sub-table at once — without a nearer provide each assignment block resolves
 * against the open tab and the rest report "no matching assignment".
 *
 * <p>Wrap a preview block in this to bind it to the sub-table it actually belongs to.
 */
const props = defineProps<{
  assignmentConfig?: AssignmentConfig
}>()

provide(MI_ASSIGNMENT_CONFIG_KEY, computed(() => props.assignmentConfig))
</script>
