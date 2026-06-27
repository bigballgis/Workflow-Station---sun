<script setup lang="ts">
import MainTableViewFilterEditor from './MainTableViewFilterEditor.vue'
import type { MainTableViewDefinition } from '@/api/mainTableView'
import { useMainTableViewDesigner } from '@/composables/mainTableView/useMainTableViewDesigner'

const props = defineProps<{ functionUnitId: number; view: MainTableViewDefinition }>()
const emit = defineEmits<{
  saved: [view: MainTableViewDefinition]
  'navigate-to-table-view': [refTableId: number]
}>()

const {
  t, Search, Close, Menu, DArrowRight, DArrowLeft, Plus, Filter, CaretTop, CaretBottom, Connection, Key,
  columnsPanelOpen, propsPanelOpen, fieldSearchKeyword, saving, viewName, viewFields, sortConfig, filterConfig,
  enableExport, enableImport, mainTableName, filterDialogVisible, addColumnPopoverVisible, thenSortField,
  dragOverIndex, dragSourceField, visibleColumns, displayFilterConditions,
  sortFieldOptions, filteredCatalog, previewRowCount, fieldLabel, getFieldIcon, getMockValue, sortIndicator,
  formatFilterTag, addField, removeField, toggleSortDirection, sortDirectionTooltip, onFilterEditorSave,
  removeDisplayFilterTag, addSortField, removeSort, handleSave, onFieldDragStart, onFieldDragEnd, onGridDrop,
  onColDragStart, onColDragOver, onColDragLeave, onColDrop, onColDragEnd,
  isFkField, isPkField, onFkColumnClick,
} = useMainTableViewDesigner(props, emit)
</script>



<template>
<div class="main-table-view-designer">
    <div class="designer-toolbar">
      <div class="toolbar-right">
        <el-button
          type="primary"
          size="small"
          :loading="saving"
          @click="handleSave"
        >
          {{ t('common.save') }}
        </el-button>
      </div>
    </div>



    <div class="designer-body">
      <!-- Left: Table columns -->
      <div
        v-if="columnsPanelOpen"
        class="columns-panel"
      >
        <div class="columns-panel-header">
          <div class="columns-panel-title">
            <el-icon><Menu /></el-icon>
            <span>{{ t('mainTableView.tableColumns') }}</span>
          </div>
          <el-icon
            class="close-btn"
            @click="columnsPanelOpen = false"
          >
            <Close />
          </el-icon>
        </div>
        <div
          v-if="mainTableName"
          class="columns-panel-table-name"
        >
          {{ mainTableName }}
        </div>
        <div class="columns-panel-search">
          <el-input
            v-model="fieldSearchKeyword"
            :placeholder="t('common.search')"
            clearable
            size="small"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
</template>

          </el-input>

        </div>

        <div class="columns-field-list">

          <div

            v-for="field in filteredCatalog"

            :key="field.fieldName"

            class="field-item"

            :class="{ dragging: dragSourceField === field.fieldName }"

            draggable="true"

            @dragstart="onFieldDragStart($event, field)"

            @dragend="onFieldDragEnd"

            @click="addField(field)"

          >

            <el-icon class="field-icon">

              <component :is="getFieldIcon(field.dataType)" />

            </el-icon>

            <span class="field-name">{{ field.displayName || field.fieldName }}</span>

          </div>

          <el-empty

            v-if="!filteredCatalog.length"

            :description="t('mainTableView.noAvailableFields')"

            :image-size="40"

          />

        </div>

      </div>

      <div

        v-else

        class="columns-toggle"

        @click="columnsPanelOpen = true"

      >

        <el-icon><DArrowRight /></el-icon>

      </div>



      <!-- Center: Live preview grid -->

      <div

        class="preview-panel"

        @dragover.prevent

        @drop="onGridDrop"

      >

        <div

          v-if="visibleColumns.length"

          class="preview-grid"

        >

          <div class="column-headers">

            <div

              v-for="(field, index) in visibleColumns"

              :key="field.fieldName"

              class="column-header"

              :class="{ 'drag-over': dragOverIndex === index }"

              :style="{ flex: field.columnWidth ? `0 0 ${field.columnWidth}px` : undefined }"

              draggable="true"

              @dragstart="onColDragStart($event, index)"

              @dragover.prevent="onColDragOver($event, index)"

              @dragleave="onColDragLeave"

              @drop.stop="onColDrop($event, index)"

              @dragend="onColDragEnd"

            >

              <el-icon
                v-if="isPkField(field.fieldName)"
                class="col-key-icon"
                :title="t('mainTableView.primaryKeyField')"
              ><Key /></el-icon>

              <span
                v-if="isFkField(field.fieldName)"
                class="col-name col-fk-link"
                :title="t('mainTableView.openRelatedView')"
                @click.stop="onFkColumnClick(field.fieldName)"
              >
                {{ field.displayLabel || field.fieldName }}
                <el-icon class="col-fk-icon"><Connection /></el-icon>
              </span>
              <span
                v-else
                class="col-name"
              >{{ field.displayLabel || field.fieldName }}</span>

              <span class="col-sort-icons">

                <el-icon

                  v-if="sortIndicator(field.fieldName) === 'ASC'"

                  class="sort-icon active"

                ><CaretTop /></el-icon>

                <el-icon

                  v-else-if="sortIndicator(field.fieldName) === 'DESC'"

                  class="sort-icon active"

                ><CaretBottom /></el-icon>

              </span>

              <el-icon

                class="col-remove"

                @click.stop="removeField(viewFields.findIndex(f => f.fieldName === field.fieldName))"

              >

                <Close />

              </el-icon>

            </div>

            <el-popover

              v-model:visible="addColumnPopoverVisible"

              placement="bottom"

              :width="220"

              trigger="click"

            >

              <template #reference>

                <div class="add-column-header">

                  <el-icon><Plus /></el-icon>

                  <span>{{ t('mainTableView.addViewColumn') }}</span>

                </div>

              </template>

              <div class="add-column-popover">

                <div

                  v-for="field in filteredCatalog"

                  :key="'add-' + field.fieldName"

                  class="field-item compact"

                  @click="addField(field)"

                >

                  {{ field.displayName || field.fieldName }}

                </div>

                <el-empty

                  v-if="!filteredCatalog.length"

                  :description="t('mainTableView.noAvailableFields')"

                  :image-size="32"

                />

              </div>

            </el-popover>

          </div>

          <div

            v-for="rowIdx in previewRowCount"

            :key="'row-' + rowIdx"

            class="data-row"

          >

            <div

              v-for="field in visibleColumns"

              :key="field.fieldName + '-' + rowIdx"

              class="data-cell"

              :style="{ flex: field.columnWidth ? `0 0 ${field.columnWidth}px` : undefined }"

            >

              <a
                v-if="isFkField(field.fieldName)"
                class="cell-fk-link"
                :title="t('mainTableView.openRelatedView')"
                @click="onFkColumnClick(field.fieldName)"
              >{{ getMockValue(field, rowIdx - 1) }}</a>
              <template v-else>{{ getMockValue(field, rowIdx - 1) }}</template>

            </div>

            <div class="add-column-spacer" />

          </div>

        </div>

        <el-empty

          v-else

          class="preview-empty"

          :description="t('mainTableView.noColumns')"

          :image-size="64"

        />

      </div>



      <!-- Right: collapsed strip to re-open the properties panel -->
      <div
        v-if="!propsPanelOpen"
        class="props-toggle"
        :title="t('mainTableView.expandProps')"
        @click="propsPanelOpen = true"
      >
        <el-icon><DArrowLeft /></el-icon>
      </div>

      <!-- Right: View properties -->

      <div
        v-if="propsPanelOpen"
        class="properties-panel"
      >

        <div class="properties-header">

          <div class="properties-title-row">
            <div class="properties-title">
              {{ viewName || props.view.viewName }}
            </div>
            <el-icon
              class="props-collapse-btn"
              :title="t('mainTableView.collapseProps')"
              @click="propsPanelOpen = false"
            >
              <DArrowRight />
            </el-icon>
          </div>

          <div class="properties-subtitle">

            {{ t('mainTableView.entityTypeView') }}

          </div>

        </div>



        <div class="properties-section">

          <label class="section-label">{{ t('mainTableView.viewName') }}</label>

          <el-input

            v-model="viewName"

            size="small"

          />

        </div>



        <div class="properties-section">

          <label class="section-label">{{ t('mainTableView.portalToolbar') }}</label>

          <div class="toolbar-toggles">

            <el-checkbox v-model="enableExport">

              {{ t('mainTableView.enableExport') }}

            </el-checkbox>

            <el-checkbox v-model="enableImport">

              {{ t('mainTableView.enableImport') }}

            </el-checkbox>

          </div>

        </div>



        <div class="properties-section sort-by-section">

          <label class="section-label">{{ t('mainTableView.sortBy') }}</label>

          <div
            v-if="sortConfig.length"
            class="sort-by-list"
          >
            <div
              v-for="(sort, idx) in sortConfig"
              :key="'sort-' + idx"
              class="sort-pill"
            >
              <el-tooltip
                :content="sortDirectionTooltip(sort)"
                placement="bottom"
                :show-after="300"
              >
                <button
                  type="button"
                  class="sort-direction-btn"
                  :aria-label="sortDirectionTooltip(sort)"
                  @click="toggleSortDirection(sort)"
                >
                  <el-icon :size="14">
                    <CaretTop v-if="sort.direction === 'ASC'" />
                    <CaretBottom v-else />
                  </el-icon>
                </button>
              </el-tooltip>
              <span class="sort-pill-label">{{ fieldLabel(sort.fieldName) }}</span>
              <button
                type="button"
                class="sort-pill-remove"
                :aria-label="t('common.delete')"
                @click="removeSort(idx)"
              >
                <el-icon :size="12"><Close /></el-icon>
              </button>
            </div>
          </div>

          <el-select
            v-if="sortFieldOptions.some(o => !sortConfig.some(s => s.fieldName === o.fieldName))"
            v-model="thenSortField"
            size="small"
            class="then-sort-select"
            :placeholder="sortConfig.length ? t('mainTableView.thenSortBy') : t('mainTableView.sortBy')"
            clearable
            @change="(val: string) => { if (val) addSortField(val) }"
          >
            <el-option
              v-for="opt in sortFieldOptions.filter(o => !sortConfig.some(s => s.fieldName === o.fieldName))"
              :key="opt.fieldName"
              :label="opt.label"
              :value="opt.fieldName"
            />
          </el-select>

        </div>



        <div class="properties-section">

          <label class="section-label">{{ t('mainTableView.filters') }}</label>

          <div

            v-if="displayFilterConditions.length"

            class="tag-list"

          >

            <el-tag

              v-for="(cond, idx) in displayFilterConditions"

              :key="'filter-' + idx"

              closable

              size="default"

              class="config-tag"

              @close="removeDisplayFilterTag(idx)"

            >

              {{ formatFilterTag(cond) }}

            </el-tag>

          </div>

          <el-button

            link

            type="primary"

            class="edit-filters-btn"

            @click="filterDialogVisible = true"

          >

            <el-icon><Filter /></el-icon>

            {{ t('mainTableView.editFilters') }}

          </el-button>

        </div>



        <div class="properties-section">

          <label class="section-label">{{ t('mainTableView.columnSettings') }}</label>

          <div

            v-for="field in viewFields"

            :key="'col-set-' + field.fieldName"

            class="column-setting-row"

          >

            <el-checkbox
              :model-value="field.visible !== false"
              @update:model-value="field.visible = $event === true"
            />

            <el-input

              v-model="field.displayLabel"

              size="small"

              class="col-label-input"

            />

            <el-tag

              v-if="field.systemField"

              size="small"

              type="info"

            >

              {{ t('mainTableView.systemField') }}

            </el-tag>

          </div>

        </div>

      </div>

    </div>



    <MainTableViewFilterEditor
      v-model="filterDialogVisible"
      :filter-config="filterConfig"
      :field-options="sortFieldOptions"
      @save="onFilterEditorSave"
    />
  </div>
</template>



<style scoped lang="scss">
@import "./MainTableViewDesigner.scss";
</style>

