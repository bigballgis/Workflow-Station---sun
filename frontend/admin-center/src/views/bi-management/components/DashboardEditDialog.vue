<template>
  <el-dialog
    v-model="visible"
    :title="t('bi.dashboard.editDialogTitle')"
    width="500px"
    destroy-on-close
  >
    <el-form
      :model="editForm"
      label-width="auto"
      label-position="left"
    >
      <el-form-item :label="t('bi.dashboard.colDashboardTitle')">
        <span>{{ editForm.dashboardTitle }}</span>
      </el-form-item>
      <el-form-item :label="t('bi.dashboard.colTags')">
        <el-input
          v-model="editForm.tags"
          :placeholder="t('bi.dashboard.tagsPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('bi.dashboard.defaultLanding')">
        <el-switch v-model="editForm.isDefaultLanding" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">
        {{ t('bi.dashboard.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="editLoading"
        @click="emit('submit')"
      >
        {{ t('bi.dashboard.ok') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  editForm: { id: string; dashboardTitle: string; tags: string; isDefaultLanding: boolean }
  editLoading: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'submit': []
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })
</script>
