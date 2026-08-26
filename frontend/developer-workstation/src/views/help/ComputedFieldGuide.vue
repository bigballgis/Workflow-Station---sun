<script setup lang="ts">
import { onMounted } from 'vue'
import { computedFieldGuideAbsoluteUrl } from '@/utils/computedFieldGuide'

onMounted(() => {
  window.location.replace(computedFieldGuideAbsoluteUrl())
})
</script>

<template>
  <div />
</template>
