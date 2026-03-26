<template>
  <div class="lookup-field">
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
    <el-dialog v-model="showSearch" title="Search" width="600px" append-to-body>
      <el-input
        v-model="searchKeyword"
        placeholder="Type to search..."
        clearable
        @input="debouncedSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-table
        :data="searchResults"
        size="small"
        class="search-results"
        v-loading="searching"
        @row-click="handleSelect"
        highlight-current-row
      >
        <el-table-column
          v-for="field in visibleColumns"
          :key="field"
          :prop="field"
          :label="field"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>

      <template #footer>
        <el-button @click="showSearch = false">Cancel</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { relationTableApi } from '@/api/relationTable'

const props = defineProps<{
  modelValue?: any
  tableId: number
  searchFields: string[]
  displayField: string
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
  (e: 'select', row: Record<string, any>): void
}>()

const showSearch = ref(false)
const searchKeyword = ref('')
const searchResults = ref<Record<string, any>[]>([])
const searching = ref(false)
const displayValue = ref('')

const visibleColumns = computed(() => {
  const cols = new Set<string>()
  if (props.displayField) cols.add(props.displayField)
  props.searchFields?.forEach(f => cols.add(f))
  return Array.from(cols)
})

let searchTimer: ReturnType<typeof setTimeout> | null = null

function debouncedSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(doSearch, 300)
}

async function doSearch() {
  if (!searchKeyword.value || !props.tableId) {
    searchResults.value = []
    return
  }
  searching.value = true
  try {
    const res = await relationTableApi.searchForLookup(props.tableId, {
      keyword: searchKeyword.value,
      searchFields: props.searchFields,
      displayField: props.displayField,
      limit: 20
    })
    searchResults.value = res.data || []
  } catch {
    searchResults.value = []
  } finally {
    searching.value = false
  }
}

function handleSelect(row: Record<string, any>) {
  displayValue.value = String(row[props.displayField] || '')
  emit('update:modelValue', row)
  emit('select', row)
  showSearch.value = false
}
</script>

<style lang="scss" scoped>
.lookup-field {
  width: 100%;
  .search-results { margin-top: 12px; }
}
</style>
