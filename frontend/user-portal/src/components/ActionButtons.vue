<template>
  <div class="action-buttons" :class="{ 'is-fixed': fixed }">
    <div class="button-container" :class="align">
      <!-- 主要操作按钮 -->
      <template v-for="action in primaryActions" :key="action.key">
        <el-button
          v-if="!action.hidden"
          :type="action.type || 'primary'"
          :icon="action.icon"
          :loading="loadingKey === action.key"
          :disabled="action.disabled || loading"
          @click="handleAction(action)"
        >
          {{ action.label }}
        </el-button>
      </template>

      <!-- 次要操作按钮 -->
      <template v-for="action in secondaryActions" :key="action.key">
        <el-button
          v-if="!action.hidden"
          :type="action.type || 'default'"
          :icon="action.icon"
          :loading="loadingKey === action.key"
          :disabled="action.disabled || loading"
          plain
          @click="handleAction(action)"
        >
          {{ action.label }}
        </el-button>
      </template>

      <!-- 更多操作下拉菜单 -->
      <el-dropdown
        v-if="moreActions.length > 0"
        trigger="click"
        @command="handleCommand"
      >
        <el-button :disabled="loading">
          {{ $t('common.more') }}
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="action in moreActions"
              :key="action.key"
              :command="action.key"
              :disabled="action.disabled"
              :divided="action.divided"
            >
              <el-icon v-if="action.icon"><component :is="action.icon" /></el-icon>
              {{ action.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 取消/返回按钮 -->
      <el-button
        v-if="showCancel"
        :disabled="loading"
        @click="handleCancel"
      >
        {{ cancelText || $t('common.cancel') }}
      </el-button>
    </div>

    <!-- 审批意见对话框 -->
    <el-dialog
      v-model="commentDialogVisible"
      :title="commentDialogTitle"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="commentFormRef" :model="commentForm" :rules="commentRules">
        <el-form-item :label="$t('task.comment')" prop="comment">
          <el-input
            v-model="commentForm.comment"
            type="textarea"
            :rows="4"
            :placeholder="$t('task.commentPlaceholder')"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item v-if="showSignature" :label="$t('task.signature')">
          <div class="signature-area">
            <canvas ref="signatureCanvas" width="400" height="150"></canvas>
            <el-button size="small" @click="clearSignature">
              {{ $t('common.clear') }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="commentDialogVisible = false">
          {{ $t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitComment"
        >
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 转办对话框 -->
    <el-dialog
      v-model="transferDialogVisible"
      :title="$t('task.transfer')"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="transferFormRef" :model="transferForm" :rules="transferRules">
        <el-form-item :label="$t('task.transferTo')" prop="targetUserId">
          <el-select
            v-model="transferForm.targetUserId"
            filterable
            remote
            :remote-method="searchUsers"
            :placeholder="$t('task.selectUser')"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="user.name"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('task.reason')" prop="reason">
          <el-input
            v-model="transferForm.reason"
            type="textarea"
            :rows="3"
            :placeholder="$t('task.transferReasonPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferDialogVisible = false">
          {{ $t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitTransfer"
        >
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowDown } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

export interface ActionButton {
  key: string
  label: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'default'
  icon?: any
  disabled?: boolean
  hidden?: boolean
  divided?: boolean
  needComment?: boolean
  needSignature?: boolean
  confirmMessage?: string
  category?: 'primary' | 'secondary' | 'more'
}

interface Props {
  actions: ActionButton[]
  loading?: boolean
  fixed?: boolean
  align?: 'left' | 'center' | 'right'
  showCancel?: boolean
  cancelText?: string
  showSignature?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  actions: () => [],
  loading: false,
  fixed: false,
  align: 'right',
  showCancel: true,
  showSignature: false
})

const emit = defineEmits<{
  (e: 'action', key: string, data?: any): void
  (e: 'cancel'): void
}>()

const { t } = useI18n()

const loadingKey = ref('')
const submitting = ref(false)

// 分类按钮
const primaryActions = computed(() =>
  props.actions.filter(a => a.category === 'primary' || (!a.category && a.type === 'primary'))
)
const secondaryActions = computed(() =>
  props.actions.filter(a => a.category === 'secondary')
)
const moreActions = computed(() =>
  props.actions.filter(a => a.category === 'more')
)

// 审批意见对话框
const commentDialogVisible = ref(false)
const commentDialogTitle = ref('')
const commentFormRef = ref<FormInstance>()
const commentForm = ref({
  comment: '',
  actionKey: ''
})
const commentRules: FormRules = {
  comment: [{ required: true, message: t('task.commentRequired'), trigger: 'blur' }]
}

// 转办对话框
const transferDialogVisible = ref(false)
const transferFormRef = ref<FormInstance>()
const transferForm = ref({
  targetUserId: '',
  reason: ''
})
const transferRules: FormRules = {
  targetUserId: [{ required: true, message: t('task.selectUserRequired'), trigger: 'change' }]
}
const userOptions = ref<Array<{ id: string; name: string }>>([])

// 签名画布
const signatureCanvas = ref<HTMLCanvasElement>()

// 处理按钮点击
const handleAction = async (action: ActionButton) => {
  // 确认提示
  if (action.confirmMessage) {
    try {
      await ElMessageBox.confirm(action.confirmMessage, t('common.confirm'), {
        type: 'warning'
      })
    } catch {
      return
    }
  }

  // 需要填写意见
  if (action.needComment) {
    commentForm.value.actionKey = action.key
    commentDialogTitle.value = action.label
    commentDialogVisible.value = true
    return
  }

  // 转办操作
  if (action.key === 'transfer') {
    transferDialogVisible.value = true
    return
  }

  // 直接执行
  emit('action', action.key)
}

// 处理下拉菜单命令
const handleCommand = (key: string) => {
  const action = moreActions.value.find(a => a.key === key)
  if (action) {
    handleAction(action)
  }
}

// 提交审批意见
const submitComment = async () => {
  if (!commentFormRef.value) return
  try {
    await commentFormRef.value.validate()
    submitting.value = true

    let signatureData = ''
    if (props.showSignature && signatureCanvas.value) {
      signatureData = signatureCanvas.value.toDataURL()
    }

    emit('action', commentForm.value.actionKey, {
      comment: commentForm.value.comment,
      signature: signatureData
    })

    commentDialogVisible.value = false
    commentForm.value = { comment: '', actionKey: '' }
  } catch {
    // 验证失败
  } finally {
    submitting.value = false
  }
}

// 提交转办
const submitTransfer = async () => {
  if (!transferFormRef.value) return
  try {
    await transferFormRef.value.validate()
    submitting.value = true

    emit('action', 'transfer', {
      targetUserId: transferForm.value.targetUserId,
      reason: transferForm.value.reason
    })

    transferDialogVisible.value = false
    transferForm.value = { targetUserId: '', reason: '' }
  } catch {
    // 验证失败
  } finally {
    submitting.value = false
  }
}

// 搜索用户
const searchUsers = async (query: string) => {
  if (query.length < 2) return
  // 调用API搜索用户
  // userOptions.value = await userApi.search(query)
}

// 清除签名
const clearSignature = () => {
  if (signatureCanvas.value) {
    const ctx = signatureCanvas.value.getContext('2d')
    if (ctx) {
      ctx.clearRect(0, 0, signatureCanvas.value.width, signatureCanvas.value.height)
    }
  }
}

// 取消
const handleCancel = () => {
  emit('cancel')
}

// 设置加载状态
const setLoading = (key: string) => {
  loadingKey.value = key
}

const clearLoading = () => {
  loadingKey.value = ''
}

defineExpose({
  setLoading,
  clearLoading
})
</script>

<style scoped lang="scss">
.action-buttons {
  padding: 16px 0;

  &.is-fixed {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    background: white;
    padding: 16px 24px;
    box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.1);
    z-index: 100;
  }

  .button-container {
    display: flex;
    gap: 12px;

    &.left {
      justify-content: flex-start;
    }

    &.center {
      justify-content: center;
    }

    &.right {
      justify-content: flex-end;
    }
  }

  .signature-area {
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    padding: 10px;

    canvas {
      display: block;
      background: #fafafa;
      border: 1px dashed #e4e7ed;
      margin-bottom: 10px;
    }
  }
}
</style>
