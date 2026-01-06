<template>
  <div class="login-container">
    <div class="login-card">
      <h2 class="login-title">开发者工作站</h2>
      
      <!-- 测试用户快速选择 (仅开发环境) -->
      <div v-if="isDev" class="test-user-section">
        <el-divider content-position="center">
          <span class="test-user-label">测试用户快速登录</span>
        </el-divider>
        <el-select 
          v-model="selectedTestUser" 
          placeholder="选择测试用户" 
          @change="onTestUserSelect"
          class="test-user-select"
        >
          <el-option
            v-for="user in testUsers"
            :key="user.username"
            :label="`${user.name} (${user.role})`"
            :value="user.username"
          >
            <div class="test-user-option">
              <span class="user-name">{{ user.name }}</span>
              <el-tag size="small" :type="user.tagType">{{ user.role }}</el-tag>
            </div>
          </el-option>
        </el-select>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" 
                    prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" class="login-btn">
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

// 开发环境检测
const isDev = import.meta.env.DEV

// 测试用户数据 (仅开发环境使用)
const testUsers = [
  { username: 'dev_lead', password: 'dev123', name: '开发组长', role: '开发组长', tagType: 'danger' as const },
  { username: 'senior_dev', password: 'dev123', name: '高级开发', role: '高级开发', tagType: 'warning' as const },
  { username: 'developer', password: 'dev123', name: '开发人员', role: '开发人员', tagType: 'success' as const },
  { username: 'designer', password: 'dev123', name: '流程设计师', role: '设计师', tagType: 'info' as const },
  { username: 'tester', password: 'dev123', name: '测试人员', role: '测试', tagType: 'primary' as const },
]

const selectedTestUser = ref('')

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 选择测试用户时自动填充
const onTestUserSelect = (username: string) => {
  const user = testUsers.find(u => u.username === username)
  if (user) {
    form.username = user.username
    form.password = user.password
    ElMessage.info(`已选择: ${user.name}`)
  }
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    // Mock login for development
    await new Promise(resolve => setTimeout(resolve, 500))
    localStorage.setItem('token', 'mock_token_' + Date.now())
    localStorage.setItem('userId', form.username)
    router.push('/')
    ElMessage.success('登录成功')
  } catch (e) {
    ElMessage.error('登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #DB0011 0%, #8B0000 100%);
}

.login-card {
  width: 400px;
  padding: 40px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.login-title {
  text-align: center;
  margin-bottom: 30px;
  color: #333;
}

.login-btn {
  width: 100%;
}

.test-user-section {
  margin-bottom: 10px;
}

.test-user-label {
  font-size: 12px;
  color: #909399;
}

.test-user-select {
  width: 100%;
  margin-bottom: 16px;
}

.test-user-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.user-name {
  font-size: 14px;
}
</style>
