<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="isEdit ? '编辑字典项' : '添加字典项'" width="500px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="显示名称" prop="label">
        <el-input v-model="form.label" />
      </el-form-item>
      <el-form-item label="值" prop="value">
        <el-input v-model="form.value" :disabled="isEdit" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="form.status" active-value="ACTIVE" inactive-value="INACTIVE" />
      </el-form-item>
      <el-form-item label="多语言">
        <el-tabs type="border-card">
          <el-tab-pane label="简体中文">
            <el-input v-model="form.translations['zh-CN']" />
          </el-tab-pane>
          <el-tab-pane label="繁體中文">
            <el-input v-model="form.translations['zh-TW']" />
          </el-tab-pane>
          <el-tab-pane label="English">
            <el-input v-model="form.translations['en']" />
          </el-tab-pane>
        </el-tabs>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, FormInstance } from 'element-plus'

const props = defineProps<{ modelValue: boolean; item: any; parent: any; dictionaryId: string | undefined }>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = computed(() => !!props.item)

const form = reactive({ label: '', value: '', sortOrder: 0, status: 'ACTIVE', translations: { 'zh-CN': '', 'zh-TW': '', 'en': '' } })

const rules = {
  label: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  value: [{ required: true, message: '请输入值', trigger: 'blur' }]
}

watch(() => props.modelValue, (val) => {
  if (val && props.item) {
    Object.assign(form, { ...props.item, translations: props.item.translations || { 'zh-CN': '', 'zh-TW': '', 'en': '' } })
  } else if (val) {
    Object.assign(form, { label: '', value: '', sortOrder: 0, status: 'ACTIVE', translations: { 'zh-CN': '', 'zh-TW': '', 'en': '' } })
  }
})

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  setTimeout(() => {
    loading.value = false
    ElMessage.success('操作成功')
    emit('update:modelValue', false)
    emit('success')
  }, 500)
}
</script>
