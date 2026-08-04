<template>
  <div class="design-table-preview">
    <p
      v-if="!tables.length"
      class="design-table-preview__empty"
    >
      {{ t('ai.doc.tablePreviewEmpty') }}
    </p>

    <div
      v-for="table in tables"
      v-else
      :key="table.name"
      class="design-table-preview__card"
    >
      <div class="design-table-preview__head">
        <span class="design-table-preview__name">{{ table.name }}</span>
        <span
          v-if="table.type"
          class="design-table-preview__type"
        >{{ table.type }}</span>
      </div>
      <p
        v-if="table.description"
        class="design-table-preview__desc"
      >
        {{ table.description }}
      </p>

      <table
        v-if="table.fields.length"
        class="design-table-preview__grid"
      >
        <thead>
          <tr>
            <th>{{ t('ai.doc.fieldName') }}</th>
            <th>{{ t('ai.doc.fieldType') }}</th>
            <th>{{ t('ai.doc.fieldDetails') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="field in table.fields"
            :key="field.name"
          >
            <td class="design-table-preview__field">
              {{ field.name }}
            </td>
            <td class="design-table-preview__data-type">
              {{ field.dataType }}
            </td>
            <td>{{ field.details }}</td>
          </tr>
        </tbody>
      </table>
      <p
        v-else
        class="design-table-preview__desc"
      >
        {{ t('ai.doc.tableNoFields') }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { parseDesignTables } from '@/utils/designDocumentPreview'

const props = defineProps<{
  content: string
}>()

const { t } = useI18n()

const tables = computed(() => parseDesignTables(props.content))
</script>

<style lang="scss" scoped>
@use '@/styles/ai-tokens.scss' as ai;

.design-table-preview__empty {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  color: ai.$ai-graphite;
}

.design-table-preview__card {
  border: 1px solid ai.$ai-hairline;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
  background: ai.$ai-paper;
}

.design-table-preview__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.design-table-preview__name {
  font-family: ai.$ai-mono;
  font-size: 12px;
  font-weight: 600;
  color: ai.$ai-ink;
}

.design-table-preview__type {
  font-family: ai.$ai-mono;
  font-size: 10px;
  color: ai.$ai-red;
  border: 1px solid ai.$ai-red;
  border-radius: 3px;
  padding: 0 4px;
}

.design-table-preview__desc {
  margin: 4px 0 8px;
  font-size: 11px;
  color: ai.$ai-graphite;
}

.design-table-preview__grid {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;

  th,
  td {
    text-align: left;
    padding: 4px 6px;
    border-top: 1px solid ai.$ai-hairline;
    vertical-align: top;
  }

  th {
    color: ai.$ai-graphite;
    font-weight: 600;
    white-space: nowrap;
  }
}

.design-table-preview__field {
  font-family: ai.$ai-mono;
  color: ai.$ai-ink;
  white-space: nowrap;
}

.design-table-preview__data-type {
  font-family: ai.$ai-mono;
  color: ai.$ai-graphite;
  white-space: nowrap;
}
</style>
