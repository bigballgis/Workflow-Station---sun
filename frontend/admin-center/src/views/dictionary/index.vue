<template>
  <div class="page-container">
    <PageHeader :title="t('menu.dictionary')">
      <template #actions>
        <el-button
          type="primary"
          @click="showCreateDialog"
        >
          <el-icon><Plus /></el-icon>{{ t('dictionary.createDictionary') }}
        </el-button>
      </template>
    </PageHeader>
    
    <el-row :gutter="20">
      <el-col :span="10">
        <el-card>
          <template #header>
            {{ t('dictionary.title') }}
          </template>
          <el-input
            v-model="filterText"
            :placeholder="t('dictionary.searchDictionary')"
            clearable
            style="margin-bottom: 15px"
          />
          <el-table
            v-loading="loading"
            :data="filteredDictionaries"
            highlight-current-row
            max-height="500"
            @current-change="handleDictSelect"
          >
            <el-table-column
              prop="name"
              :label="t('dictionary.dictName')"
            />
            <el-table-column
              prop="code"
              :label="t('dictionary.dictCode')"
              width="120"
            />
            <el-table-column
              prop="type"
              :label="t('dictionary.dictType')"
              width="100"
            >
              <template #default="{ row }">
                <el-tag size="small">
                  {{ typeText(row.type) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('dictionary.dictActions')"
              width="100"
            >
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click.stop="showEditDialog(row)"
                >
                  {{ t('dictionary.dictEdit') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      
      <el-col :span="14">
        <el-card v-if="selectedDict">
          <template #header>
            <div class="dict-header">
              <span>{{ selectedDict.name }} - {{ t('dictionary.dictItems') }}</span>
              <el-button
                type="primary"
                size="small"
                @click="showItemDialog()"
              >
                {{ t('dictionary.addItem') }}
              </el-button>
            </div>
          </template>
          
          <el-table
            v-loading="itemsLoading"
            :data="dictItems"
            row-key="id"
            default-expand-all
            :tree-props="{ children: 'children' }"
          >
            <el-table-column
              prop="label"
              :label="t('dictionary.displayName')"
            />
            <el-table-column
              prop="value"
              :label="t('dictionary.dictValue')"
              width="120"
            />
            <el-table-column
              prop="sortOrder"
              :label="t('dictionary.dictSort')"
              width="80"
            />
            <el-table-column
              prop="status"
              :label="t('dictionary.dictStatus')"
              width="80"
            >
              <template #default="{ row }">
                <el-tag
                  :type="row.status === 'ACTIVE' ? 'success' : 'info'"
                  size="small"
                >
                  {{ row.status === 'ACTIVE' ? t('dictionary.statusActive') : t('dictionary.statusInactive') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('dictionary.dictActions')"
              width="150"
            >
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click="showItemDialog(row)"
                >
                  {{ t('dictionary.dictEdit') }}
                </el-button>
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click="showItemDialog(undefined, row)"
                >
                  {{ t('dictionary.addChild') }}
                </el-button>
                <el-button
                  link
                  type="danger"
                  size="small"
                  @click="handleDeleteItem(row)"
                >
                  {{ t('dictionary.dictDelete') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-empty
          v-else
          :description="t('dictionary.selectDictHint')"
        />
      </el-col>
    </el-row>
    
    <DictionaryFormDialog
      v-model="formDialogVisible"
      :dictionary="currentDict"
      @success="fetchDictionaries"
    />
    <DictionaryItemDialog
      v-model="itemDialogVisible"
      :item="currentItem"
      :parent="parentItem"
      :dictionary-id="selectedDict?.id"
      @success="fetchDictItems"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import DictionaryFormDialog from './components/DictionaryFormDialog.vue'
import DictionaryItemDialog from './components/DictionaryItemDialog.vue'
import { useDictionary } from '@/composables/modules/useDictionary'
import { dictionaryTypeKey } from '@/utils/format'

const { t } = useI18n()

const {
  filterText,
  loading,
  itemsLoading,
  selectedDict,
  dictItems,
  formDialogVisible,
  itemDialogVisible,
  currentDict,
  currentItem,
  parentItem,
  filteredDictionaries,
  fetchDictionaries,
  fetchDictItems,
  handleDictSelect,
  showCreateDialog,
  showEditDialog,
  showItemDialog,
  handleDeleteItem,
} = useDictionary()

const typeText = (type: string) => t(dictionaryTypeKey(type))

onMounted(fetchDictionaries)
</script>

<style scoped>
.dict-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
