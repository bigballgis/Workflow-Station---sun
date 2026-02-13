<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.config') }}</span>
    </div>
    
    <el-tabs v-model="activeTab">
      <el-tab-pane :label="t('config.systemParams')" name="system">
        <el-form :model="systemConfig" label-width="150px">
          <el-form-item :label="t('config.sessionTimeout')">
            <el-input-number v-model="systemConfig.sessionTimeout" :min="5" :max="120" />
            <span class="form-tip">{{ t('config.minutes') }}</span>
          </el-form-item>
          <el-form-item :label="t('config.fileUploadLimit')">
            <el-input-number v-model="systemConfig.maxFileSize" :min="1" :max="100" />
            <span class="form-tip">MB</span>
          </el-form-item>
          <el-form-item :label="t('config.mailServer')">
            <el-input v-model="systemConfig.smtpServer" style="width: 300px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveConfig('system')">{{ t('common.save') }}</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <el-tab-pane :label="t('config.businessParams')" name="business">
        <el-form :model="businessConfig" label-width="150px">
          <el-form-item :label="t('config.processTimeout')">
            <el-input-number v-model="businessConfig.processTimeout" :min="1" :max="30" />
            <span class="form-tip">{{ t('config.days') }}</span>
          </el-form-item>
          <el-form-item :label="t('config.taskAssignRule')">
            <el-select v-model="businessConfig.taskAssignRule">
              <el-option :label="t('config.roundRobin')" value="ROUND_ROBIN" />
              <el-option :label="t('config.loadBalance')" value="LOAD_BALANCE" />
              <el-option :label="t('config.random')" value="RANDOM" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveConfig('business')">{{ t('common.save') }}</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { configApi, type SystemConfig } from '@/api/config'

const { t } = useI18n()
const activeTab = ref('system')
const loading = ref(false)

const systemConfig = reactive({ sessionTimeout: 30, maxFileSize: 10, smtpServer: '' })
const businessConfig = reactive({ processTimeout: 7, taskAssignRule: 'ROUND_ROBIN' })

const loadConfigs = async () => {
  loading.value = true
  try {
    const configs = await configApi.getAll()
    configs.forEach((config: SystemConfig) => {
      if (config.configKey === 'session.timeout') systemConfig.sessionTimeout = parseInt(config.configValue) || 30
      if (config.configKey === 'file.maxSize') systemConfig.maxFileSize = parseInt(config.configValue) || 10
      if (config.configKey === 'smtp.server') systemConfig.smtpServer = config.configValue || ''
      if (config.configKey === 'process.timeout') businessConfig.processTimeout = parseInt(config.configValue) || 7
      if (config.configKey === 'task.assignRule') businessConfig.taskAssignRule = config.configValue || 'ROUND_ROBIN'
    })
  } catch (e) {
    console.error('Failed to load configs:', e)
  } finally {
    loading.value = false
  }
}

const saveConfig = async (type: string) => {
  try {
    if (type === 'system') {
      await Promise.all([
        configApi.update('session.timeout', { configValue: String(systemConfig.sessionTimeout) }),
        configApi.update('file.maxSize', { configValue: String(systemConfig.maxFileSize) }),
        configApi.update('smtp.server', { configValue: systemConfig.smtpServer })
      ])
    } else {
      await Promise.all([
        configApi.update('process.timeout', { configValue: String(businessConfig.processTimeout) }),
        configApi.update('task.assignRule', { configValue: businessConfig.taskAssignRule })
      ])
    }
    ElMessage.success(type === 'system' ? t('config.systemSaveSuccess') : t('config.businessSaveSuccess'))
  } catch (e) {
    console.error('Failed to save config:', e)
    ElMessage.error(t('config.saveFailed'))
  }
}

onMounted(loadConfigs)
</script>

<style scoped>
.form-tip { margin-left: 10px; color: #909399; }
</style>
