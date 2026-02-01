<template>
  <div class="login-container">
    <div class="login-card">
      <h2 class="login-title">{{ t('login.title') }}</h2>
      
      <!-- 测试用户快速选择 (仅开发环境) -->
      <div v-if="isDev" class="test-user-section">
        <el-divider content-position="center">
          <span class="test-user-label">{{ t('login.testUserHint') }}</span>
        </el-divider>
        <el-select 
          v-model="selectedTestUser" 
          :placeholder="t('login.selectTestUser')" 
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
          <el-input v-model="form.username" :placeholder="t('login.usernamePlaceholder')" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" :placeholder="t('login.passwordPlaceholder')" 
                    prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" class="login-btn">
            {{ t('login.login') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance } from 'element-plus'
import { login as authLogin, saveTokens, saveUser } from '@/api/auth'

const { t } = useI18n()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

// 开发环境检测
const isDev = import.meta.env.DEV

// 测试用户数据 (仅开发环境使用) - IT部门开发人员
const testUsers = [
  { username: 'tech.director', password: 'admin123', name: 'Robert Sun', role: 'Tech Director', tagType: 'danger' as const },
  { username: 'core.lead', password: 'admin123', name: 'Kevin Huang', role: 'Core Lead', tagType: 'warning' as const },
  { username: 'channel.lead', password: 'admin123', name: 'Grace Lin', role: 'Channel Lead', tagType: 'warning' as const },
  { username: 'risk.lead', password: 'admin123', name: 'Tony Chen', role: 'Risk Lead', tagType: 'warning' as const },
  { username: 'dev.john', password: 'admin123', name: 'John Smith', role: 'Senior Dev', tagType: 'success' as const },
  { username: 'dev.mary', password: 'admin123', name: 'Mary Johnson', role: 'Developer', tagType: 'success' as const },
]

const selectedTestUser = ref('')

const form = reactive({
  username: '',
  password: ''
})

const rules = computed(() => ({
  username: [{ required: true, message: t('login.usernamePlaceholder'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordPlaceholder'), trigger: 'blur' }]
}))

// 选择测试用户时自动填充
const onTestUserSelect = (username: string) => {
  const user = testUsers.find(u => u.username === username)
  if (user) {
    form.username = user.username
    form.password = user.password
    ElMessage.info(`Selected: ${user.name}`)
  }
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    // Call real login API
    const response = await authLogin({
      username: form.username,
      password: form.password
    })
    
    // Save tokens and user info
    saveTokens(response.accessToken, response.refreshToken)
    saveUser(response.user)
    localStorage.setItem('userId', response.user.userId)
    
    router.push('/')
    ElMessage.success(t('common.success'))
  } catch (error: any) {
    const data = error.response?.data
    const message = data?.error?.message || data?.message || error.message || t('common.error')
    ElMessage.error(message)
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
  position: relative;
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
