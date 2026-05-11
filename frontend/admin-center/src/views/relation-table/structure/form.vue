<template>
  <div class="page-container">
    <PageHeader :title="isEdit ? 'Edit Table Structure' : 'Create Table Structure'">
      <template #actions>
        <el-button @click="router.back()">
          Back
        </el-button>
      </template>
    </PageHeader>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="140px"
      label-position="left"
      style="max-width: 800px;"
    >
      <el-form-item
        label="Table Name"
        prop="tableName"
      >
        <el-input
          v-model="form.tableName"
          placeholder="e.g. my_table"
          :disabled="isEdit"
        />
      </el-form-item>
      <el-form-item
        label="Display Name"
        prop="displayName"
      >
        <el-input
          v-model="form.displayName"
          placeholder="Display name"
        />
      </el-form-item>
      <el-form-item
        label="Description"
        prop="description"
      >
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="Table description"
        />
      </el-form-item>
    </el-form>

    <div style="margin-top: 24px;">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
        <span style="font-size: 16px; font-weight: 600;">Field Definitions</span>
        <el-button
          type="primary"
          size="small"
          @click="addField"
        >
          <el-icon><Plus /></el-icon>Add Field
        </el-button>
      </div>

      <el-table
        :data="form.fieldDefinitions"
        border
      >
        <el-table-column
          label="#"
          width="50"
          align="center"
        >
          <template #default="{ $index }">
            {{ $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column
          label="Field Name"
          min-width="140"
        >
          <template #default="{ row }">
            <el-input
              v-model="row.fieldName"
              placeholder="field_name"
              size="small"
              :disabled="isAuditField(row)"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="Data Type"
          width="140"
        >
          <template #default="{ row }">
            <el-select
              v-model="row.dataType"
              placeholder="Type"
              size="small"
              style="width: 100%;"
              :disabled="isAuditField(row)"
            >
              <el-option
                v-for="dt in dataTypes"
                :key="dt"
                :label="dt"
                :value="dt"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column
          label="Length"
          width="90"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.length"
              :min="0"
              size="small"
              controls-position="right"
              style="width: 100%;"
              :disabled="isAuditField(row)"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="Nullable"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <el-switch
              v-model="row.nullable"
              size="small"
              :disabled="isAuditField(row)"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="Primary Key"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-switch
              v-model="row.isPrimaryKey"
              size="small"
              :disabled="isAuditField(row)"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="Default Value"
          width="130"
        >
          <template #default="{ row }">
            <el-input
              v-model="row.defaultValue"
              placeholder=""
              size="small"
              :disabled="isAuditField(row)"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="Comment"
          min-width="140"
        >
          <template #default="{ row }">
            <el-input
              v-model="row.comment"
              placeholder=""
              size="small"
              :disabled="isAuditField(row)"
            />
          </template>
        </el-table-column>
        <el-table-column
          label=""
          width="60"
          align="center"
        >
          <template #default="{ row, $index }">
            <el-button
              v-if="!isAuditField(row)"
              link
              type="danger"
              size="small"
              @click="removeField($index)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div style="margin-top: 24px;">
      <el-button
        type="primary"
        :loading="submitting"
        @click="handleSubmit"
      >
        {{ isEdit ? 'Save Changes' : 'Create Table' }}
      </el-button>
      <el-button @click="router.back()">
        Cancel
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, toRef } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Plus, Delete } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { useTableStructureForm } from '@/composables/modules/useTableStructureForm'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)
const tableId = computed(() => Number(route.params.id))
const formRef = ref<FormInstance>()

const { form, rules, submitting, dataTypes, isAuditField, addField, removeField, loadTableData, submit }
  = useTableStructureForm({ tableId, isEdit: toRef(isEdit) })

const handleSubmit = async () => {
  if (!await formRef.value?.validate().catch(() => false)) return
  if (await submit()) router.push('/relation-tables/structure')
}

onMounted(loadTableData)
</script>

<style scoped>
.page-container {
  padding: 20px;
}
</style>
