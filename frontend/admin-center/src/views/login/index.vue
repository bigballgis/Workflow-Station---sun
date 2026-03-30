<template>
  <div class="login-container">
    <!-- Right side dark red background shape (matches logon.html) -->
    <div class="bg-shape"></div>

    <!-- Right side login card -->
    <div class="login-right">
      <div class="login-card">
        <div class="login-header">
          <!-- HerMes H logo -->
          <div class="brand-logo">
            <img src="/hermes-logo.svg" alt="HerMes" class="brand-logo-img" />
          </div>
          <h2 class="login-title">Admin Centre</h2>
        </div>

        <!-- Test user quick select (dev only) -->
        <div v-if="isDev" class="test-user-section">
          <el-divider content-position="center">
            <span class="test-user-label">🚀 {{ t('login.testUserHint') }}</span>
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

        <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin" class="login-form">
          <el-form-item prop="username">
            <label class="field-label" style="line-height: normal;">Username</label>
            <el-input
              v-model="form.username"
              placeholder="Enter Your Username"
              prefix-icon="User"
              size="large"
            />
          </el-form-item>
          <el-form-item prop="password">
            <label class="field-label" style="line-height: normal;" >Password</label>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="Enter Your Password"
              prefix-icon="Lock"
              show-password
              size="large"
            />
          </el-form-item>
          <el-form-item class="btn-item">
            <el-button
              type="primary"
              native-type="submit"
              :loading="loading"
              class="login-btn"
              size="large"
            >
              {{ loading ? t('common.loading') : 'Log in' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="workflow-link">
          <a href="#" class="workflow-text">Workflow Platform</a>
        </div>

        <div class="login-footer">
          <span>© 2026 HerMes</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, FormInstance } from 'element-plus'
import { login as authLogin, saveTokens, saveUser } from '@/api/auth'

const { t } = useI18n()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const isDev = import.meta.env.DEV

const testUsers = [
  { username: 'admin', password: 'admin123', name: 'System Admin', role: 'Admin', tagType: 'danger' as const },
]

const selectedTestUser = ref('')
const form = reactive({ username: '', password: '' })
const rules = computed(() => ({
  username: [{ required: true, message: t('login.usernamePlaceholder'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordPlaceholder'), trigger: 'blur' }]
}))

const onTestUserSelect = (username: string) => {
  const user = testUsers.find(u => u.username === username)
  if (user) {
    form.username = user.username
    form.password = user.password
    ElMessage.info(`Selected: ${user.name}`)
  }
}

const handleLogin = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return

  loading.value = true
  try {
    const response = await authLogin({
      username: form.username,
      password: form.password
    })

    saveTokens(response.accessToken, response.refreshToken)
    saveUser(response.user)
    localStorage.setItem('userId', response.user.userId)
    localStorage.setItem('username', response.user.username || response.user.displayName || response.user.userId)

    ElMessage.success(t('common.success'))
    router.push('/dashboard')
  } catch (error: any) {
    const message = error.response?.data?.message || error.message || t('common.failed')
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
/* Match logon.html design - DM Sans, #D82028, #C60C12 */
$primary: #C60C12;
$primary-hover: #A00A0F;
$bg-base: #D82028;
$title-color: #2F2F2F;
$label-color: #666666;
$input-border: #E0E0E0;
$placeholder-color: #969696;

.login-container {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background-color: $bg-base;
  position: relative;
  font-family: 'DM Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* Right side dark red background shape (matches logon.html) */
.bg-shape {
  left: 5%;
  position: absolute;
  top: 0;
  right: 0;
  width: 54%;
  height: 100%;
  background: linear-gradient(90deg, #60050A 0%, #D82028 82.69%);
  clip-path: polygon(59% 0, 100% 0, 100% 100%, 59% 100%, 0% 50%);
  z-index: 1;
}

/* Login card - right: 20%, width: 380px */
.login-right {
  position: absolute;
  top: 50%;
  right: 20%;
  transform: translateY(-50%);
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.login-card {
  width: 120%;
  padding: 40px 45px;
  background: #FFFFFF;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  text-align: center;
}

/* Brand logo - HerMes SVG */
.login-header {
  text-align: center;
  margin-bottom: 20px;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.brand-logo-img {
  height: 48px;
  width: auto;
  object-fit: contain;
}

/* Title - #2F2F2F, 36px, 800 weight */
.login-title {
  font-size: 36px;
  font-weight: 800;
  color: $title-color;
  margin: 0 0 30px;
}

/* Form - match logon.html labels & inputs */
.login-form {
  .field-label {
    display: block;
    font-size: 16px;
    font-weight: 600;
    color: $label-color;
    margin-bottom: 8px;
    text-align: left;
  }

  :deep(.el-form-item) {
    margin-bottom: 20px;
    flex-direction: column;
    align-items: flex-start;
  }

  :deep(.el-form-item__content) {
    width: 100%;
  }

  :deep(.el-input__wrapper) {
    border-radius: 6px;
    border: 1px solid $input-border;
    box-shadow: none;
    padding: 12px 15px;
    font-size: 16px;
    font-weight: 500;

    &:hover, &.is-focus {
      border-color: $primary;
      box-shadow: none;
    }
  }

  :deep(.el-input__inner) {
    font-size: 16px;
    font-weight: 500;

    &::placeholder {
      color: $placeholder-color;
    }
  }
}

.btn-item {
  margin-top: 10px;
  margin-bottom: 0 !important;
}

/* Login button - #C60C12, 18px, 600 weight */
.login-btn {
  width: 100%;
  padding: 14px;
  height: auto;
  font-size: 18px;
  font-weight: 600;
  border-radius: 6px;
  background: $primary;
  border-color: $primary;

  &:hover, &:focus {
    background: $primary-hover;
    border-color: $primary-hover;
  }

  &:active {
    background: #8a080c;
    border-color: #8a080c;
  }
}

/* Workflow link */
.workflow-link {
  text-align: center;
  margin-top: 16px;
}

.workflow-text {
  font-size: 14px;
  font-weight: 600;
  color: $primary;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

/* Footer */
.login-footer {
  text-align: center;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;

  span {
    font-size: 12px;
    color: #bbb;
  }
}

/* Test user section */
.test-user-section {
  margin-bottom: 16px;

  :deep(.el-divider__text) {
    background: white;
  }
}

.test-user-label {
  font-size: 12px;
  color: #909399;
}

.test-user-select {
  width: 100%;
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
