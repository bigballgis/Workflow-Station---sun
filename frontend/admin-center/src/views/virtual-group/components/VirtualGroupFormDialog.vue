<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="isEdit ? '编辑虚拟组' : '创建虚拟组'" width="500px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="编码" prop="code">
        <el-input v-model="form.code" :disabled="isEdit" />
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-select v-model="form.type">
          <el-option label="项目组" value="PROJECT" />
          <el-option label="临时组" value="TEMPORARY" />
          <el-option label="跨部门组" value="CROSS_DEPT" />
        </el-select>
      </el-form-item>
      <el-form-item label="有效期">
        <el-date-picker v-model="form.dateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="3" />
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

const props = defineProps<{ modelValue: boolean; group: any }>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = computed(() => !!props.group)

const form = reactive({ name: '', code: '', type: 'PROJECT', dateRange: null as any, description: '' })

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

watch(() => props.modelValue, (val) => {
  if (val && props.group) {
    Object.assign(form, { name: props.group.name, code: props.group.code, type: props.group.type, dateRange: [props.group.validFrom, props.group.validTo], description: props.group.description || '' })
  } else if (val) {
    Object.assign(form, { name: '', code: '', type: 'PROJECT', dateRange: null, description: '' })
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
