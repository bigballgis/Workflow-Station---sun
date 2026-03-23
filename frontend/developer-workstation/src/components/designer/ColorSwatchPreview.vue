<template>
  <span
    v-if="modelValue"
    class="color-swatch-preview"
    :style="{ backgroundColor: modelValue }"
    :title="modelValue"
  />
  <span v-else class="color-swatch-preview color-swatch-empty" title="No color">-</span>
</template>

<script setup lang="ts">
defineProps<{ modelValue?: string }>()
</script>

<style scoped>
.color-swatch-preview {
  display: inline-block;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  vertical-align: middle;
}
.color-swatch-empty {
  background-color: #ffffff;
  text-align: center;
  line-height: 28px;
  color: #c0c4cc;
  font-size: 12px;
}
</style>
