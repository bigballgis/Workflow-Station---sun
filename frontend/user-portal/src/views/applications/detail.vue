<template>
  <div class="application-detail-page">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="$router.back()">返回</el-button>
      <h1>申请详情</h1>
    </div>

    <el-row :gutter="20">
      <el-col :span="24">
        <div class="portal-card">
          <div class="card-header">
            <span class="card-title">基本信息</span>
          </div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="流程标题">请假申请-2026年1月</el-descriptions-item>
            <el-descriptions-item label="流程类型">请假流程</el-descriptions-item>
            <el-descriptions-item label="发起时间">2026-01-05 10:00</el-descriptions-item>
            <el-descriptions-item label="当前状态">
              <el-tag type="warning">进行中</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="当前节点">部门经理审批</el-descriptions-item>
            <el-descriptions-item label="当前处理人">王经理</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="14">
        <div class="portal-card">
          <div class="card-header">
            <span class="card-title">流程图</span>
          </div>
          <div class="diagram-placeholder">
            <el-empty description="流程图" />
          </div>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="portal-card">
          <div class="card-header">
            <span class="card-title">流转历史</span>
          </div>
          <el-timeline>
            <el-timeline-item timestamp="2026-01-05 10:00" type="primary">
              <div>提交申请</div>
              <div class="history-user">张三</div>
            </el-timeline-item>
            <el-timeline-item timestamp="2026-01-05 11:30" type="success">
              <div>直属领导审批</div>
              <div class="history-user">李主管</div>
              <div class="history-comment">同意</div>
            </el-timeline-item>
            <el-timeline-item timestamp="待处理" type="warning">
              <div>部门经理审批</div>
              <div class="history-user">王经理</div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft } from '@element-plus/icons-vue'
</script>

<style lang="scss" scoped>
.application-detail-page {
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
  
  .diagram-placeholder {
    height: 300px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  
  .history-user {
    font-size: 12px;
    color: var(--text-secondary);
    margin-top: 4px;
  }
  
  .history-comment {
    font-size: 13px;
    color: var(--text-secondary);
    margin-top: 8px;
    padding: 8px;
    background: var(--background-light);
    border-radius: 4px;
  }
}
</style>
