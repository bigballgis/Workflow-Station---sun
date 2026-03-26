<template>
  <div class="lookup-component">
    <el-input
      v-model="displayValue"
      :placeholder="placeholder"
      readonly
      @click="showSearch = true"
    >
      <template #suffix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>

    <!-- Search dialog -->
    <el-dialog v-model="showSearch" title="Lookup Search" width="600px">
      <el-input
        v-model="searchKeyword"
        placeholder="Search..."
        clearable
        @input="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-table :data="searchResults" size="small" class="search-results" @row-click="handleSelect">
        <el-table-column
          v-for="field in displayFields"
          :key="field"
          :prop="field"
          :label="field"
          min-width="120"
        />
      </el-table>

      <template #footer>
        <el-button @click="showSearch = false">Cancel</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue?: any
  placeholder?: string
  displayFields?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
  (e: 'select', row: any): void
}>()

const showSearch = ref(false)
const searchKeyword = ref('')
const searchResults = ref<any[]>([])
const displayValue = ref(props.modelValue || '')

function handleSearch() {
  // In design mode, this is a placeholder
  searchResults.value = []
}

function handleSelect(row: any) {
  displayValue.value = row[props.displayFields?.[0] || ''] || ''
  emit('update:modelValue', row)
  emit('select', row)
  showSearch.value = false
}
</script>

<style lang="scss" scoped>
.lookup-component {
  width: 100%;
  .search-results {
    margin-top: 12px;
  }
}
</style>
