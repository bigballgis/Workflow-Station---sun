<template>
  <div class="process-start-page">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="$router.back()">返回</el-button>
      <h1>发起流程 - {{ processName }}</h1>
    </div>

    <div class="portal-card">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
        <el-form-item label="流程标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入流程标题" />
        </el-form-item>
        <el-form-item label="紧急程度" prop="priority">
          <el-radio-group v-model="formData.priority">
            <el-radio value="NORMAL">普通</el-radio>
            <el-radio value="GENERAL">一般</el-radio>
            <el-radio value="URGENT">紧急</el-radio>
            <el-radio value="CRITICAL">特急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="申请说明" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="4" placeholder="请输入申请说明" />
        </el-form-item>
        <el-form-item label="附件">
          <el-upload
            action="#"
            :auto-upload="false"
            :file-list="fileList"
            @change="handleFileChange"
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 PDF、Word、Excel、图片等格式</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
    </div>

    <div class="action-section">
      <div class="action-left">
        <el-button @click="saveDraft">保存草稿</el-button>
        <el-button @click="$router.back()">取消</el-button>
      </div>
      <div class="action-right">
        <el-button type="primary" @click="submitProcess">提交</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, FormInstance, UploadFile } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const processKey = route.params.key as string
const formRef = ref<FormInstance>()
const fileList = ref<UploadFile[]>([])

const processNames: Record<string, string> = {
  leave: '请假申请',
  expense: '报销申请',
  purchase: '采购申请',
  travel: '出差申请',
  overtime: '加班申请',
  contract: '合同审批'
}

const processName = computed(() => processNames[processKey] || '流程')

const formData = reactive({
  title: '',
  priority: 'NORMAL',
  description: ''
})

const rules = {
  title: [{ required: true, message: '请输入流程标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入申请说明', trigger: 'blur' }]
}

const handleFileChange = (file: UploadFile) => {
  fileList.value.push(file)
}

const saveDraft = () => {
  ElMessage.success('草稿保存成功')
}

const submitProcess = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate((valid) => {
    if (valid) {
      ElMessage.success('流程提交成功')
      router.push('/my-applications')
    }
  })
}
</script>

<style lang="scss" scoped>
.process-start-page {
  .page-header {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 20px;
    
    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }
  
  .action-section {
    display: flex;
    justify-content: space-between;
    margin-top: 20px;
    padding: 16px 20px;
    background: white;
    border-radius: 8px;
    border: 1px solid var(--border-color);
  }
}
</style>
