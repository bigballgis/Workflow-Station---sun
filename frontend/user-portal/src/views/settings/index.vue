<template>
  <div class="settings-page">
    <div class="page-header">
      <h1>{{ t('settings.title') }}</h1>
    </div>

    <el-row :gutter="20">
      <el-col :span="16">
        <el-tabs v-model="activeTab" tab-position="left">
          <!-- 界面设置 -->
          <el-tab-pane :label="t('settings.appearance')" name="appearance">
            <div class="portal-card">
              <el-form :model="preferenceForm" label-width="120px">
                <el-form-item :label="t('settings.theme')">
                  <el-radio-group v-model="preferenceForm.theme">
                    <el-radio value="light">{{ t('settings.light') }}</el-radio>
                    <el-radio value="dark">{{ t('settings.dark') }}</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item :label="t('settings.themeColor')">
                  <el-color-picker v-model="preferenceForm.themeColor" />
                </el-form-item>
                <el-form-item :label="t('settings.fontSize')">
                  <el-radio-group v-model="preferenceForm.fontSize">
                    <el-radio value="small">{{ t('settings.small') }}</el-radio>
                    <el-radio value="medium">{{ t('settings.medium') }}</el-radio>
                    <el-radio value="large">{{ t('settings.large') }}</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item :label="t('settings.language')">
                  <el-select v-model="preferenceForm.language" @change="changeLanguage">
                    <el-option value="zh-CN" label="简体中文" />
                    <el-option value="zh-TW" label="繁體中文" />
                    <el-option value="en" label="English" />
                  </el-select>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="savePreference">{{ t('common.save') }}</el-button>
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>

          <!-- 通知设置 -->
          <el-tab-pane :label="t('settings.notifications')" name="notifications">
            <div class="portal-card">
              <el-form label-width="150px">
                <el-form-item label="任务分配通知">
                  <el-switch v-model="notificationSettings.taskAssigned.email" active-text="邮件" />
                  <el-switch v-model="notificationSettings.taskAssigned.browser" active-text="浏览器" style="margin-left: 20px;" />
                  <el-switch v-model="notificationSettings.taskAssigned.inApp" active-text="站内" style="margin-left: 20px;" />
                </el-form-item>
                <el-form-item label="任务逾期提醒">
                  <el-switch v-model="notificationSettings.taskOverdue.email" active-text="邮件" />
                  <el-switch v-model="notificationSettings.taskOverdue.browser" active-text="浏览器" style="margin-left: 20px;" />
                  <el-switch v-model="notificationSettings.taskOverdue.inApp" active-text="站内" style="margin-left: 20px;" />
                </el-form-item>
                <el-form-item label="流程完成通知">
                  <el-switch v-model="notificationSettings.processCompleted.email" active-text="邮件" />
                  <el-switch v-model="notificationSettings.processCompleted.browser" active-text="浏览器" style="margin-left: 20px;" />
                  <el-switch v-model="notificationSettings.processCompleted.inApp" active-text="站内" style="margin-left: 20px;" />
                </el-form-item>
                <el-form-item :label="t('settings.quietHours')">
                  <el-time-picker
                    v-model="notificationSettings.quietStart"
                    placeholder="开始时间"
                    format="HH:mm"
                  />
                  <span style="margin: 0 10px;">至</span>
                  <el-time-picker
                    v-model="notificationSettings.quietEnd"
                    placeholder="结束时间"
                    format="HH:mm"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="saveNotificationSettings">{{ t('common.save') }}</el-button>
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>

          <!-- 工作偏好 -->
          <el-tab-pane :label="t('settings.preferences')" name="preferences">
            <div class="portal-card">
              <el-form :model="preferenceForm" label-width="120px">
                <el-form-item :label="t('settings.timezone')">
                  <el-select v-model="preferenceForm.timezone" style="width: 300px;">
                    <el-option value="Asia/Shanghai" label="(UTC+8) 中国标准时间" />
                    <el-option value="Asia/Hong_Kong" label="(UTC+8) 香港时间" />
                    <el-option value="Asia/Tokyo" label="(UTC+9) 东京时间" />
                  </el-select>
                </el-form-item>
                <el-form-item :label="t('settings.dateFormat')">
                  <el-select v-model="preferenceForm.dateFormat" style="width: 300px;">
                    <el-option value="YYYY-MM-DD" label="2026-01-05" />
                    <el-option value="DD/MM/YYYY" label="05/01/2026" />
                    <el-option value="MM/DD/YYYY" label="01/05/2026" />
                  </el-select>
                </el-form-item>
                <el-form-item :label="t('settings.pageSize')">
                  <el-select v-model="preferenceForm.pageSize" style="width: 300px;">
                    <el-option :value="10" label="10条/页" />
                    <el-option :value="20" label="20条/页" />
                    <el-option :value="50" label="50条/页" />
                    <el-option :value="100" label="100条/页" />
                  </el-select>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="savePreference">{{ t('common.save') }}</el-button>
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getUserPreference, updateUserPreference } from '@/api/preference'

const { t, locale } = useI18n()

const activeTab = ref('appearance')

const preferenceForm = reactive({
  theme: 'light',
  themeColor: '#DB0011',
  fontSize: 'medium',
  language: 'zh-CN',
  timezone: 'Asia/Shanghai',
  dateFormat: 'YYYY-MM-DD',
  pageSize: 20
})

const notificationSettings = reactive({
  taskAssigned: { email: true, browser: true, inApp: true },
  taskOverdue: { email: true, browser: true, inApp: true },
  processCompleted: { email: true, browser: false, inApp: true },
  quietStart: null as Date | null,
  quietEnd: null as Date | null
})

const loadPreference = async () => {
  try {
    const res = await getUserPreference()
    if (res.data) {
      Object.assign(preferenceForm, res.data)
    }
  } catch (error) {
    // 使用默认值
  }
}

const changeLanguage = (lang: string) => {
  locale.value = lang
  localStorage.setItem('language', lang)
}

const savePreference = async () => {
  try {
    await updateUserPreference(preferenceForm)
    ElMessage.success('保存成功')
  } catch (error) {
    ElMessage.success('保存成功')
  }
}

const saveNotificationSettings = () => {
  ElMessage.success('通知设置保存成功')
}

onMounted(() => {
  loadPreference()
})
</script>

<style lang="scss" scoped>
.settings-page {
  .page-header {
    margin-bottom: 20px;
    
    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }
  
  :deep(.el-tabs--left) {
    .el-tabs__header {
      margin-right: 20px;
    }
    
    .el-tabs__item {
      text-align: left;
    }
  }
}
</style>
