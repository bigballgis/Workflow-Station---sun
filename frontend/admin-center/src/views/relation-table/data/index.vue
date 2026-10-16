<template>
  <div class="page-container">
    <PageHeader :title="t('menu.tableData')">
      <template #actions>
        <DesignerHelpLink
          path="/table-data"
          v-bind="{ ariaLabel: t('tableDataNavigation.guide') }"
          test-id="table-data-guide-link"
        />
      </template>
    </PageHeader>

    <div class="data-layout">
      <RelationTableNavigator
        :tables="tables"
        :selected-table-id="selectedTableId"
        :active-group-key="activeGroupKey"
        :loading="tableListLoading"
        :error="tableListError"
        @select="handleSelectTable"
        @retry="refresh"
      />

      <!-- Right: Data grid -->
      <div class="data-grid-panel">
        <template v-if="selectedTable">
          <div
            class="selected-table-header"
            data-testid="selected-table-header"
          >
            <div class="selected-table-info">
              <div class="table-breadcrumb">
                {{ selectedGroupLabel }} / {{ selectedTable.displayName || selectedTable.tableName }}
              </div>
              <div class="selected-table-title">
                <h2>{{ selectedTable.displayName || selectedTable.tableName }}</h2>
                <el-tag
                  size="small"
                  :type="canWrite ? 'info' : 'warning'"
                >
                  {{ t(canWrite ? 'tableDataNavigation.writable' : 'tableDataNavigation.readonly') }}
                </el-tag>
              </div>
              <p
                v-if="selectedTable.description"
                class="table-description"
              >
                {{ selectedTable.description }}
              </p>
            </div>
          </div>
          <div class="grid-toolbar">
            <el-input
              v-model="searchKeyword"
              placeholder="Search..."
              clearable
              style="width: 240px; margin-right: 12px;"
              @keyup.enter="fetchData"
              @clear="fetchData"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button
              v-if="canWrite"
              type="primary"
              @click="openAddDialog"
            >
              <el-icon><Plus /></el-icon> Add
            </el-button>
            <el-button
              :loading="exporting"
              @click="handleExport"
            >
              <el-icon><Download /></el-icon> Export CSV
            </el-button>
            <el-dropdown
              v-if="canWrite"
              trigger="click"
              @command="handleDownloadTemplate"
            >
              <el-button :loading="exportingTemplate">
                <el-icon><Download /></el-icon> Export Template
                <el-icon class="el-icon--right">
                  <ArrowDown />
                </el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="csv">
                    CSV (.csv)
                  </el-dropdown-item>
                  <el-dropdown-item command="xlsx">
                    Excel (.xlsx)
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button
              v-if="canWrite"
              @click="openImportDialog"
            >
              <el-icon><Upload /></el-icon> Import
            </el-button>
          </div>

          <el-alert
            v-if="fetchDataError"
            :title="fetchDataError"
            type="error"
            show-icon
            :closable="false"
            style="margin-bottom: 12px;"
          />

          <RelationTableDataGrid
            :grid="grid"
            :loading="dataLoading"
            :can-write="canWrite"
            :status-width="RT_DATA_STATUS_COL_WIDTH"
            :actions-width="RT_DATA_ACTIONS_COL_WIDTH"
            :is-row-disabled="isRowDisabled"
            :format-date-time="formatHKT"
            @fetch="fetchData"
            @edit="openEditDialog"
            @enable="handleEnable"
            @disable="handleDisable"
            @delete="handleDelete"
          />
        </template>
        <el-empty
          v-else
          :description="selectionError || t('tableDataNavigation.selectTable')"
        />
      </div>
    </div>

    <!-- Add/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'add' ? 'Add Record' : 'Edit Record'"
      width="600px"
      destroy-on-close
    >
      <el-form
        :model="formData"
        label-width="auto"
        label-position="left"
      >
        <el-form-item
          v-for="field in visibleFieldColumns"
          :key="field.fieldName"
          :label="field.displayName || field.fieldName"
          :required="!field.nullable"
        >
          <template v-if="field.dataType === 'LOOKUP'">
            <LookupField
              :model-value="formData[field.fieldName]"
              :table-id="field.lookupConfig?.refTableId || 0"
              :search-fields="field.lookupConfig?.searchFields || []"
              :display-field="field.lookupConfig?.displayFields?.[0] || ''"
              :display-fields="field.lookupConfig?.displayFields || []"
              :selected-display-field="field.lookupConfig?.selectedDisplayField"
              :filter-conditions="lookupFilterConditionsFor(field)"
              :view-fields="lookupViewFieldsFor(field)"
              :multiple="field.lookupConfig?.multiple"
              :lookup-config="JSON.stringify(field.lookupConfig || {})"
              @update:model-value="formData[field.fieldName] = $event"
              @select="onLookupSelect(field, $event)"
              @clear="onLookupClear(field)"
            />
            <LookupViewDisplay
              v-if="field.lookupConfig?.showBackfillView !== false && lookupSelectedData[field.fieldName]"
              :selected-data="lookupSelectedData[field.fieldName]"
              :view-fields="lookupViewFieldsFor(field)"
            />
          </template>
          <el-switch
            v-else-if="field.dataType === 'BOOLEAN'"
            v-model="formData[field.fieldName]"
            :disabled="isPkFieldDisabled(field) || isFkFieldDisabled(field)"
          />
          <el-input-number
            v-else-if="isNumericType(field.dataType)"
            v-model="formData[field.fieldName]"
            :precision="field.dataType === 'DECIMAL' ? (field.scale || 2) : 0"
            style="width: 100%;"
            :disabled="isPkFieldDisabled(field) || isFkFieldDisabled(field)"
          />
          <el-date-picker
            v-else-if="field.dataType === 'DATE'"
            v-model="formData[field.fieldName]"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%;"
            :disabled="isPkFieldDisabled(field) || isFkFieldDisabled(field)"
          />
          <el-date-picker
            v-else-if="field.dataType === 'TIMESTAMP'"
            v-model="formData[field.fieldName]"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
            :disabled="isPkFieldDisabled(field) || isFkFieldDisabled(field)"
          />
          <el-input
            v-else-if="field.dataType === 'TEXT'"
            v-model="formData[field.fieldName]"
            type="textarea"
            :rows="3"
            :disabled="isPkFieldDisabled(field) || isFkFieldDisabled(field)"
          />
          <el-input
            v-else
            v-model="formData[field.fieldName]"
            :maxlength="field.length || undefined"
            :disabled="isPkFieldDisabled(field) || isFkFieldDisabled(field)"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
          Cancel
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="handleSaveRecord"
        >
          Save
        </el-button>
      </template>
    </el-dialog>

    <!-- Import Dialog -->
    <el-dialog
      v-model="importDialogVisible"
      title="Import Data"
      width="640px"
    >
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 12px;"
      >
        Download a template first, fill it in, then upload (CSV or Excel). Rows are validated against the table structure; invalid rows are skipped. Auto-generated primary keys are filled by the system, so they are not included in the template. Up to 1000 rows per import.
      </el-alert>
      <el-upload
        drag
        :auto-upload="false"
        :show-file-list="false"
        accept=".csv,.xlsx"
        :on-change="onImportFileChange"
      >
        <el-icon class="el-icon--upload">
          <Upload />
        </el-icon>
        <div class="el-upload__text">
          Drop file here or <em>click to upload</em>
        </div>
      </el-upload>
      <div
        v-if="importing"
        style="margin-top: 12px;"
      >
        <el-icon class="is-loading">
          <Loading />
        </el-icon> Validating...
      </div>
      <div
        v-if="importResult"
        style="margin-top: 12px;"
      >
        <el-alert
          v-if="importResult.dryRun"
          :type="importResult.failed > 0 ? 'warning' : 'success'"
          :closable="false"
          :title="`${importResult.validCount} valid row(s), ${importResult.failed} invalid${importResult.failed > 0 ? ' (will be skipped)' : ''}. Click “Confirm Import” to import.`"
          style="margin-bottom: 8px;"
        />
        <el-alert
          v-else
          :type="importResult.failed > 0 ? 'warning' : 'success'"
          :closable="false"
          :title="`Inserted ${importResult.inserted}, Failed ${importResult.failed}`"
          style="margin-bottom: 8px;"
        />
        <el-table
          v-if="importResult.errors.length"
          :data="importResult.errors"
          stripe
          max-height="240"
          size="small"
        >
          <el-table-column
            prop="row"
            label="Row"
            width="70"
          />
          <el-table-column
            prop="field"
            label="Field"
            width="160"
          />
          <el-table-column
            prop="message"
            label="Error"
          />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="importDialogVisible = false">
          Close
        </el-button>
        <el-button
          v-if="importResult && importResult.dryRun && (importResult.validCount ?? 0) > 0"
          type="primary"
          :loading="importing"
          @click="handleConfirmImport"
        >
          Confirm Import ({{ importResult.validCount }})
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onActivated } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, Download, Plus, Upload, ArrowDown, Loading } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import LookupField from '@/components/lookup/LookupField.vue'
import LookupViewDisplay from '@/components/lookup/LookupViewDisplay.vue'
import {
  useRelationTableData,
  RT_DATA_STATUS_COL_WIDTH,
  RT_DATA_ACTIONS_COL_WIDTH,
} from '@/composables/modules/useRelationTableData'
import RelationTableDataGrid from './components/RelationTableDataGrid.vue'
import RelationTableNavigator from './components/RelationTableNavigator.vue'
import DesignerHelpLink from '@/components/relation-table/DesignerHelpLink.vue'

const { t } = useI18n()

const {
  tableListLoading, dataLoading, exporting, saving,
  exportingTemplate, importDialogVisible, importing, importResult,
  tables, tableListError, selectedTableId, searchKeyword, activeGroupKey, selectedGroupLabel, selectionError,
  fetchDataError, dialogVisible, dialogMode, formData,
  selectedTable, canWrite, visibleFieldColumns,
  isNumericType, isRowDisabled, isFkFieldDisabled, isPkFieldDisabled,
  lookupSelectedData, lookupFilterConditionsFor, onLookupSelect, onLookupClear, lookupViewFieldsFor,
  fetchData, handleSelectTable,
  openAddDialog, openEditDialog, handleSaveRecord, handleDisable, handleEnable, handleDelete,
  formatHKT, handleExport, handleDownloadTemplate, openImportDialog, handleImportFile, handleConfirmImport, refresh,
  grid,
} = useRelationTableData()

const onImportFileChange = (file: { raw?: File }) => {
  if (file.raw) handleImportFile(file.raw)
}

onActivated(refresh)
</script>

<style scoped>
.page-container { padding: 20px; }
.data-layout { display: flex; gap: 16px; height: calc(100vh - 140px); }
.data-grid-panel { flex: 1; min-width: 0; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }
.grid-toolbar { display: flex; flex-wrap: wrap; align-items: center; margin-bottom: 12px; gap: 8px; }
.selected-table-header { padding: 2px 0 16px; margin-bottom: 16px; border-bottom: 1px solid var(--el-border-color-light); }
.table-breadcrumb { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 10px; }
.selected-table-title { display: flex; align-items: center; gap: 12px; }
.selected-table-title h2 { font-size: 20px; font-weight: 600; line-height: 1.4; margin: 0; overflow-wrap: anywhere; }
.table-description { margin: 8px 0 0; color: var(--el-text-color-regular); font-size: 13px; line-height: 1.5; }
@media (max-width: 1100px) { .page-container { padding: 12px; } .data-layout { gap: 12px; } }
</style>
