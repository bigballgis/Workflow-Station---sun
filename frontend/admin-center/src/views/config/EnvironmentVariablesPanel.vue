<template>
  <div>
    <div class="env-toolbar">
      <el-button
        type="primary"
        @click="openCreate"
      >
        {{ t('config.envAdd') }}
      </el-button>
      <el-button
        :loading="loading"
        @click="load"
      >
        {{ t('common.refresh') }}
      </el-button>
    </div>
    <el-table
      v-loading="loading"
      :data="rows"
      stripe
    >
      <el-table-column
        prop="varKey"
        :label="t('config.envKey')"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="displayName"
        :label="t('config.envDisplayName')"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column
        prop="valueKind"
        :label="t('config.envKind')"
        width="100"
      />
      <el-table-column
        :label="t('config.envValue')"
        min-width="220"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ valuePreview(row) }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.actions')"
        width="140"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="openEdit(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            link
            type="danger"
            @click="remove(row)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('config.envEdit') : t('config.envAdd')"
      width="520px"
      destroy-on-close
    >
      <el-form
        label-position="top"
        class="env-form"
      >
        <el-form-item
          :label="t('config.envKey')"
          required
        >
          <el-input v-model="form.varKey" />
        </el-form-item>
        <el-form-item
          :label="t('config.envDisplayName')"
          required
        >
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
        <el-form-item
          :label="t('config.envKind')"
          required
        >
          <el-radio-group v-model="form.valueKind">
            <el-radio value="TEXT">
              TEXT
            </el-radio>
            <el-radio value="VAULT">
              VAULT
            </el-radio>
          </el-radio-group>
          <div class="form-tip">
            {{ form.valueKind === 'VAULT' ? t('config.envKindVaultHint') : t('config.envKindTextHint') }}
          </div>
        </el-form-item>
        <template v-if="form.valueKind === 'TEXT'">
          <el-form-item
            :label="t('config.envDefaultValue')"
            required
          >
            <el-input v-model="form.defaultValue" />
          </el-form-item>
          <el-form-item :label="t('config.envCurrentValue')">
            <el-input v-model="form.currentValue" />
            <div class="form-tip">
              {{ t('config.envCurrentHint') }}
            </div>
          </el-form-item>
        </template>
        <el-form-item
          v-else
          :label="t('config.envVaultPath')"
          required
        >
          <el-input
            v-model="form.vaultSecretPath"
            placeholder="workflow/email/qq-inbound"
          />
          <div class="form-tip">
            {{ t('config.envVaultPathHint') }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="save"
        >
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useEnvironmentVariables } from '@/composables/modules/useEnvironmentVariables'
import type { EnvironmentVariable } from '@/api/environment'

const { t } = useI18n()
const {
  loading, saving, rows, dialogVisible, editingId, form,
  load, openCreate, openEdit, save, remove,
} = useEnvironmentVariables()

onMounted(() => {
  void load()
})

function valuePreview(row: EnvironmentVariable): string {
  if (row.valueKind === 'VAULT') {
    return row.vaultSecretPath || ''
  }
  return row.currentValue?.trim() ? row.currentValue : (row.defaultValue || '')
}
</script>

<style scoped>
.env-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.form-tip {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}
</style>
