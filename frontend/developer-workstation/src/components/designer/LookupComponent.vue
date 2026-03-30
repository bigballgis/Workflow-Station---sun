<template>
  <div class="lookup-component">
    <el-input
      v-model="displayValue"
      :placeholder="placeholder"
      readonly
    >
      <template #suffix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue?: any
  placeholder?: string
  lookupConfig?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
}>()

const displayValue = ref(props.modelValue || '')
</script>

<style lang="scss" scoped>
.lookup-component {
  width: 100%;
}
</style>
