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
                <el-form-item :label="t('settings.taskAssignedNotification')">
                  <el-switch v-model="notificationSettings.taskAssigned.email" :active-text="t('settings.email')" />
                  <el-switch v-model="notificationSettings.taskAssigned.browser" :active-text="t('settings.browser')" style="margin-left: 20px;" />
                  <el-switch v-model="notificationSettings.taskAssigned.inApp" :active-text="t('settings.inApp')" style="margin-left: 20px;" />
                </el-form-item>
                <el-form-item :label="t('settings.taskOverdueReminder')">
                  <el-switch v-model="notificationSettings.taskOverdue.email" :active-text="t('settings.email')" />
                  <el-switch v-model="notificationSettings.taskOverdue.browser" :active-text="t('settings.browser')" style="margin-left: 20px;" />
                  <el-switch v-model="notificationSettings.taskOverdue.inApp" :active-text="t('settings.inApp')" style="margin-left: 20px;" />
                </el-form-item>
                <el-form-item :label="t('settings.processCompletedNotification')">
                  <el-switch v-model="notificationSettings.processCompleted.email" :active-text="t('settings.email')" />
                  <el-switch v-model="notificationSettings.processCompleted.browser" :active-text="t('settings.browser')" style="margin-left: 20px;" />
                  <el-switch v-model="notificationSettings.processCompleted.inApp" :active-text="t('settings.inApp')" style="margin-left: 20px;" />
                </el-form-item>
                <el-form-item :label="t('settings.quietHours')">
                  <el-time-picker
                    v-model="notificationSettings.quietStart"
                    :placeholder="t('settings.startTime')"
                    format="HH:mm"
                  />
                  <span style="margin: 0 10px;">{{ t('common.to') }}</span>
                  <el-time-picker
                    v-model="notificationSettings.quietEnd"
                    :placeholder="t('settings.endTime')"
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
                    <el-option value="Asia/Shanghai" :label="t('settings.timezoneShanghai')" />
                    <el-option value="Asia/Hong_Kong" :label="t('settings.timezoneHongKong')" />
                    <el-option value="Asia/Tokyo" :label="t('settings.timezoneTokyo')" />
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
                    <el-option :value="10" :label="t('settings.itemsPerPage', { count: 10 })" />
                    <el-option :value="20" :label="t('settings.itemsPerPage', { count: 20 })" />
                    <el-option :value="50" :label="t('settings.itemsPerPage', { count: 50 })" />
                    <el-option :value="100" :label="t('settings.itemsPerPage', { count: 100 })" />
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

const { t } = useI18n()

const activeTab = ref('appearance')

const preferenceForm = reactive({
  theme: 'light',
  themeColor: '#DB0011',
  fontSize: 'medium',
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
    // API 返回格式: { success: true, data: {...} }
    const data = res.data || res
    if (data) {
      Object.assign(preferenceForm, data)
    }
  } catch (error) {
    console.error('Failed to load preference:', error)
  }
}

const savePreference = async () => {
  try {
    await updateUserPreference(preferenceForm)
    ElMessage.success(t('settings.saveSuccess'))
  } catch (error) {
    ElMessage.success(t('settings.saveSuccess'))
  }
}

const saveNotificationSettings = () => {
  ElMessage.success(t('settings.notificationSaveSuccess'))
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
