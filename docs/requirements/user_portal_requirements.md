# 用户工作流门户需求

## 1. 概述

用户工作流门户是低代码工作流平台面向最终用户的操作界面，提供流程发起、任务处理、工作台管理等核心功能。它是用户与工作流系统交互的主要入口，注重用户体验和操作效率。

### 1.1 设计理念
- **用户友好**：直观易用的界面设计，降低学习成本
- **高效便捷**：快速完成日常工作流操作
- **个性化**：支持个人偏好设置和工作台定制
- **移动适配**：响应式设计，支持移动端访问

### 1.2 核心价值
- **提升工作效率**：简化流程操作，减少重复工作
- **增强协作体验**：便捷的任务分配和协作功能
- **实时信息获取**：及时的任务通知和状态更新
- **数据可视化**：清晰的工作统计和进度展示

## 2. 个人工作台

### 2.1 Dashboard概览界面
#### 2.1.1 核心指标展示
```yaml
dashboard_widgets:
  task_summary:
    title: "任务概览"
    metrics:
      - pending_tasks: "待办任务数"
      - overdue_tasks: "逾期任务数"
      - completed_today: "今日完成"
      - avg_processing_time: "平均处理时长"
    
  workflow_summary:
    title: "流程概览"
    metrics:
      - initiated_processes: "发起的流程"
      - in_progress: "进行中"
      - completed_this_month: "本月完成"
      - approval_rate: "审批通过率"
    
  performance_metrics:
    title: "个人绩效"
    metrics:
      - efficiency_score: "效率评分"
      - quality_score: "质量评分"
      - collaboration_score: "协作评分"
      - monthly_ranking: "月度排名"
```

#### 2.1.2 可视化图表
- **任务趋势图**：
  - 近30天任务处理趋势
  - 任务类型分布饼图
  - 处理时长分布柱状图
  - 逾期任务趋势线

- **流程统计图**：
  - 流程发起量统计
  - 流程完成率分析
  - 流程类型使用频率
  - 审批节点耗时分析

#### 2.1.3 快捷操作区
- **常用流程**：
  - 收藏的流程模板
  - 最近使用的流程
  - 一键发起按钮
  - 流程使用统计

- **快捷链接**：
  - 我的待办
  - 我的申请
  - 委托管理
  - 个人设置

### 2.2 个性化设置
#### 2.2.1 界面定制
- **主题设置**：
  - 浅色/深色主题
  - 主题色彩选择
  - 字体大小调节
  - 布局密度设置

- **工作台布局**：
  - 组件拖拽排列
  - 组件显示/隐藏
  - 组件大小调整
  - 布局模板保存

#### 2.2.2 通知偏好
```json
{
  "notification_preferences": {
    "email_notifications": {
      "task_assigned": true,
      "task_overdue": true,
      "process_completed": false,
      "system_maintenance": true
    },
    "browser_notifications": {
      "task_assigned": true,
      "urgent_tasks": true,
      "mentions": true
    },
    "mobile_notifications": {
      "enabled": true,
      "quiet_hours": {
        "start": "22:00",
        "end": "08:00"
      }
    }
  }
}
```

#### 2.2.3 工作偏好
- **语言设置**：中文/英文切换
- **时区设置**：本地时区配置
- **日期格式**：日期时间显示格式
- **分页设置**：列表默认显示条数

## 3. 流程管理

### 3.1 流程发起
#### 3.1.1 流程分类和导航
- **分类体系**：
  - 按业务领域分类（人事、财务、采购等）
  - 按使用频率分类（常用、偶用、新增）
  - 按权限范围分类（个人、部门、公司）
  - 自定义分类标签

- **流程搜索**：
  - 关键词搜索
  - 分类筛选
  - 标签过滤
  - 最近使用排序

#### 3.1.2 流程发起界面
```vue
<template>
  <div class="process-initiation">
    <!-- 流程基本信息 -->
    <el-card class="process-info">
      <div class="process-header">
        <img :src="processIcon" class="process-icon" />
        <div class="process-details">
          <h3>{{ processName }}</h3>
          <p>{{ processDescription }}</p>
          <el-tag>{{ processCategory }}</el-tag>
        </div>
      </div>
    </el-card>
    
    <!-- 动态表单 -->
    <el-card class="process-form">
      <form-create 
        v-model="formData"
        :rule="formRule"
        :option="formOption"
        @submit="submitProcess"
      />
    </el-card>
    
    <!-- 操作按钮 -->
    <div class="action-buttons">
      <el-button @click="saveDraft">保存草稿</el-button>
      <el-button type="primary" @click="submitProcess">提交流程</el-button>
    </div>
  </div>
</template>
```

#### 3.1.3 草稿管理
- **自动保存**：
  - 定时自动保存（每30秒）
  - 表单变更触发保存
  - 页面离开前保存
  - 保存状态提示

- **草稿列表**：
  - 草稿创建时间
  - 流程类型标识
  - 完成进度显示
  - 快速继续编辑

### 3.2 流程跟踪
#### 3.2.1 我的申请
- **申请列表**：
  - 流程标题和编号
  - 当前状态和节点
  - 发起时间
  - 预计完成时间
  - 优先级标识

- **状态筛选**：
  - 进行中
  - 已完成
  - 已撤回
  - 已拒绝
  - 草稿状态

#### 3.2.2 流程详情页面
```yaml
process_detail_sections:
  basic_info:
    - 流程编号
    - 流程标题
    - 发起人信息
    - 发起时间
    - 当前状态
    
  progress_tracking:
    - 流程图展示
    - 节点执行状态
    - 处理人信息
    - 处理时间
    - 处理意见
    
  form_data:
    - 表单数据展示
    - 附件文件列表
    - 数据变更历史
    
  operation_log:
    - 操作时间线
    - 操作类型
    - 操作人员
    - 操作结果
```

#### 3.2.3 流程操作
- **流程撤回**：
  - 撤回条件检查
  - 撤回原因填写
  - 影响范围提示
  - 撤回确认

- **流程催办**：
  - 催办对象选择
  - 催办消息编辑
  - 催办记录查看
  - 催办频率限制

### 3.3 流程统计分析
#### 3.3.1 个人流程统计
- **发起统计**：
  - 按月份统计发起量
  - 按流程类型统计
  - 完成率分析
  - 平均处理时长

- **参与统计**：
  - 作为处理人的任务数
  - 作为抄送人的通知数
  - 协作参与度分析
  - 跨部门协作统计

#### 3.3.2 流程效率分析
```sql
-- 流程效率分析视图
CREATE VIEW process_efficiency_analysis AS
SELECT 
    p.process_definition_key,
    p.initiator_id,
    COUNT(*) as total_processes,
    AVG(EXTRACT(EPOCH FROM (p.end_time - p.start_time))/3600) as avg_duration_hours,
    COUNT(CASE WHEN p.end_time <= p.due_date THEN 1 END) * 100.0 / COUNT(*) as on_time_rate,
    COUNT(CASE WHEN p.status = 'COMPLETED' THEN 1 END) * 100.0 / COUNT(*) as completion_rate
FROM process_instances p
WHERE p.start_time >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY p.process_definition_key, p.initiator_id;
```

## 4. 任务管理

### 4.1 待办任务
#### 4.1.1 任务列表界面
- **列表字段**：
  - 任务标题（支持点击查看详情）
  - 流程类型图标
  - 发起人信息
  - 任务到达时间
  - 截止时间（逾期高亮显示）
  - 优先级标识
  - 任务状态

- **排序和筛选**：
  - 按优先级排序
  - 按到达时间排序
  - 按截止时间排序
  - 流程类型筛选
  - 发起人筛选
  - 时间范围筛选

#### 4.1.2 任务优先级管理
```yaml
priority_levels:
  urgent:
    level: 1
    color: "#ff4d4f"
    icon: "urgent"
    description: "紧急任务，需立即处理"
    sla: "2小时内处理"
    
  high:
    level: 2
    color: "#ff7a45"
    icon: "high"
    description: "高优先级任务"
    sla: "1个工作日内处理"
    
  normal:
    level: 3
    color: "#1890ff"
    icon: "normal"
    description: "普通任务"
    sla: "3个工作日内处理"
    
  low:
    level: 4
    color: "#52c41a"
    icon: "low"
    description: "低优先级任务"
    sla: "1周内处理"
```

#### 4.1.3 批量操作
- **批量审批**：
  - 选择多个同类型任务
  - 统一审批意见
  - 批量通过/拒绝
  - 操作结果反馈

- **批量转办**：
  - 选择转办对象
  - 转办原因说明
  - 转办权限验证
  - 转办通知发送

### 4.2 任务处理
#### 4.2.1 任务详情页面
```vue
<template>
  <div class="task-detail">
    <!-- 任务基本信息 -->
    <el-card class="task-info">
      <div class="task-header">
        <h2>{{ taskTitle }}</h2>
        <el-tag :type="priorityType">{{ priorityText }}</el-tag>
      </div>
      <div class="task-meta">
        <span>发起人：{{ initiator }}</span>
        <span>到达时间：{{ arrivalTime }}</span>
        <span>截止时间：{{ dueTime }}</span>
      </div>
    </el-card>
    
    <!-- 流程图展示 -->
    <el-card class="process-diagram">
      <bpmn-viewer 
        :xml="processXml" 
        :current-activity="currentActivity"
      />
    </el-card>
    
    <!-- 表单数据 -->
    <el-card class="form-data">
      <form-create 
        v-model="formData"
        :rule="formRule"
        :option="formOption"
        :readonly="isReadonly"
      />
    </el-card>
    
    <!-- 处理意见 -->
    <el-card class="comments">
      <el-input
        v-model="comment"
        type="textarea"
        placeholder="请输入处理意见"
        :rows="4"
      />
    </el-card>
    
    <!-- 操作按钮 -->
    <div class="action-buttons">
      <el-button 
        v-for="action in availableActions"
        :key="action.code"
        :type="action.type"
        @click="executeAction(action)"
      >
        {{ action.name }}
      </el-button>
    </div>
  </div>
</template>
```

#### 4.2.2 动作执行
- **默认动作**：
  - 同意/通过
  - 拒绝/驳回
  - 转办
  - 委托
  - 回退
  - 撤回

- **自定义动作**：
  - 业务特定操作
  - 外部系统调用
  - 数据处理动作
  - 通知发送动作

#### 4.2.3 附件管理
- **文件上传**：
  - 拖拽上传
  - 多文件选择
  - 文件类型限制
  - 文件大小限制
  - 上传进度显示

- **文件预览**：
  - 图片在线预览
  - PDF在线查看
  - Office文档预览
  - 文本文件查看

### 4.3 任务历史
#### 4.3.1 处理记录
- **历史任务列表**：
  - 任务完成时间
  - 处理结果
  - 处理耗时
  - 流程最终状态
  - 处理评价

- **统计分析**：
  - 月度处理量统计
  - 平均处理时长
  - 按时完成率
  - 任务类型分布

#### 4.3.2 绩效评估
```sql
-- 个人任务绩效统计
CREATE VIEW personal_task_performance AS
SELECT 
    t.assignee_id,
    DATE_TRUNC('month', t.end_time) as month,
    COUNT(*) as total_tasks,
    COUNT(CASE WHEN t.end_time <= t.due_date THEN 1 END) as on_time_tasks,
    AVG(EXTRACT(EPOCH FROM (t.end_time - t.start_time))/3600) as avg_processing_hours,
    AVG(t.quality_score) as avg_quality_score
FROM task_instances t
WHERE t.end_time IS NOT NULL
GROUP BY t.assignee_id, DATE_TRUNC('month', t.end_time);
```

## 5. 委托管理

### 5.1 委托设置
#### 5.1.1 委托规则配置
- **委托类型**：
  - 全部委托：所有任务都委托给指定人员
  - 部分委托：按流程类型或条件委托
  - 临时委托：指定时间段内的委托
  - 紧急委托：仅委托紧急任务

- **委托条件**：
  - 流程类型筛选
  - 优先级筛选
  - 发起人筛选
  - 金额范围筛选
  - 时间段限制

#### 5.1.2 委托对象管理
```yaml
delegation_config:
  delegate_selection:
    - type: "individual"
      description: "委托给个人"
      validation: "检查被委托人权限"
      
    - type: "role"
      description: "委托给角色"
      validation: "检查角色成员"
      
    - type: "department"
      description: "委托给部门"
      validation: "检查部门负责人"
      
  delegation_rules:
    - max_delegation_depth: 2  # 最大委托层级
    - circular_delegation_check: true  # 循环委托检查
    - delegation_approval_required: false  # 是否需要审批
    - auto_revoke_on_return: true  # 返岗自动撤销
```

#### 5.1.3 委托生效管理
- **立即生效**：设置后立即生效
- **定时生效**：指定开始时间
- **条件生效**：满足特定条件时生效
- **手动激活**：需要手动激活委托

### 5.2 代理处理
#### 5.2.1 代理任务标识
- **任务列表标识**：
  - 代理任务特殊图标
  - 原委托人信息显示
  - 代理权限范围提示
  - 代理有效期显示

- **权限限制**：
  - 只能查看不能修改
  - 可以处理但需要审批
  - 完全代理权限
  - 特定操作限制

#### 5.2.2 代理操作记录
- **操作日志**：
  - 代理操作时间
  - 代理操作内容
  - 代理决策依据
  - 原委托人确认

- **责任追溯**：
  - 代理决策责任
  - 委托授权记录
  - 操作审计跟踪
  - 结果通知机制

### 5.3 委托监控
#### 5.3.1 委托状态监控
- **委托生效状态**：
  - 当前生效的委托
  - 即将到期的委托
  - 已过期的委托
  - 暂停的委托

- **委托使用统计**：
  - 委托任务处理量
  - 委托效果评估
  - 委托满意度调查
  - 委托优化建议

#### 5.3.2 委托审计
```sql
-- 委托操作审计表
CREATE TABLE delegation_audit (
    id UUID PRIMARY KEY,
    delegator_id UUID NOT NULL,        -- 委托人
    delegate_id UUID NOT NULL,         -- 被委托人
    task_id UUID,                      -- 任务ID
    action_type VARCHAR(50) NOT NULL,  -- 操作类型
    action_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    action_result VARCHAR(20),         -- 操作结果
    comments TEXT,                     -- 操作说明
    ip_address INET,                   -- 操作IP
    user_agent TEXT                    -- 用户代理
);
```

## 6. 权限申请

### 6.1 权限申请流程
#### 6.1.1 申请类型
- **功能权限申请**：
  - 模块访问权限
  - 功能操作权限
  - 特殊功能权限
  - 临时权限申请

- **数据权限申请**：
  - 数据查看权限
  - 数据修改权限
  - 数据导出权限
  - 跨部门数据权限

#### 6.1.2 申请表单
```vue
<template>
  <el-form :model="permissionRequest" :rules="rules">
    <el-form-item label="申请类型" prop="requestType">
      <el-select v-model="permissionRequest.requestType">
        <el-option label="功能权限" value="function" />
        <el-option label="数据权限" value="data" />
        <el-option label="临时权限" value="temporary" />
      </el-select>
    </el-form-item>
    
    <el-form-item label="权限范围" prop="permissions">
      <permission-tree 
        v-model="permissionRequest.permissions"
        :permission-type="permissionRequest.requestType"
      />
    </el-form-item>
    
    <el-form-item label="申请理由" prop="reason">
      <el-input 
        v-model="permissionRequest.reason"
        type="textarea"
        :rows="4"
        placeholder="请详细说明申请权限的业务需要"
      />
    </el-form-item>
    
    <el-form-item label="使用期限" prop="duration">
      <el-date-picker
        v-model="permissionRequest.duration"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
      />
    </el-form-item>
  </el-form>
</template>
```

#### 6.1.3 审批流程
- **自动审批**：
  - 低风险权限自动通过
  - 临时权限快速审批
  - 预设规则自动处理

- **人工审批**：
  - 直接上级审批
  - 权限管理员审批
  - 多级审批流程
  - 会签审批机制

### 6.2 权限查看
#### 6.2.1 当前权限展示
- **权限树形结构**：
  - 按模块分类展示
  - 权限层级关系
  - 权限有效期显示
  - 权限来源说明

- **权限详情**：
  - 权限名称和描述
  - 授权时间和人员
  - 权限有效期
  - 使用频率统计

#### 6.2.2 权限使用统计
```sql
-- 权限使用统计视图
CREATE VIEW permission_usage_stats AS
SELECT 
    pu.user_id,
    pu.permission_id,
    p.permission_name,
    COUNT(al.id) as usage_count,
    MAX(al.action_time) as last_used_time,
    AVG(CASE WHEN al.action_time >= CURRENT_DATE - INTERVAL '30 days' 
             THEN 1 ELSE 0 END) as monthly_usage_rate
FROM permission_users pu
LEFT JOIN permissions p ON pu.permission_id = p.id
LEFT JOIN audit_logs al ON al.user_id = pu.user_id 
    AND al.resource_type = p.resource_type
    AND al.action = p.action
GROUP BY pu.user_id, pu.permission_id, p.permission_name;
```

### 6.3 权限生命周期
#### 6.3.1 权限到期提醒
- **提醒机制**：
  - 到期前7天提醒
  - 到期前1天提醒
  - 到期当天提醒
  - 到期后权限自动回收

- **续期申请**：
  - 一键续期申请
  - 续期理由说明
  - 自动续期规则
  - 批量续期处理

#### 6.3.2 权限回收
- **自动回收**：
  - 到期自动回收
  - 离职自动回收
  - 长期未使用回收
  - 违规使用回收

- **手动回收**：
  - 管理员主动回收
  - 用户主动放弃
  - 安全事件回收
  - 组织调整回收

## 7. 消息通知

### 7.1 通知中心
#### 7.1.1 消息分类
- **系统通知**：
  - 系统维护通知
  - 功能更新通知
  - 安全提醒通知
  - 政策变更通知

- **任务通知**：
  - 新任务分配
  - 任务即将到期
  - 任务已逾期
  - 任务状态变更

- **流程通知**：
  - 流程审批结果
  - 流程状态变更
  - 流程异常提醒
  - 流程完成通知

#### 7.1.2 通知展示
```vue
<template>
  <div class="notification-center">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="未读消息" name="unread">
        <notification-list 
          :notifications="unreadNotifications"
          @mark-read="markAsRead"
        />
      </el-tab-pane>
      
      <el-tab-pane label="所有消息" name="all">
        <notification-list 
          :notifications="allNotifications"
          @delete="deleteNotification"
        />
      </el-tab-pane>
      
      <el-tab-pane label="系统通知" name="system">
        <notification-list 
          :notifications="systemNotifications"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
```

### 7.2 通知渠道
#### 7.2.1 站内通知
- **实时推送**：WebSocket实时推送
- **消息提示**：浏览器通知API
- **红点提醒**：未读消息数量显示
- **声音提醒**：可配置提示音

#### 7.2.2 邮件通知
- **邮件模板**：
  - 任务分配邮件模板
  - 审批结果邮件模板
  - 系统通知邮件模板
  - 自定义邮件模板

- **发送策略**：
  - 立即发送
  - 批量发送（每小时汇总）
  - 摘要发送（每日摘要）
  - 紧急单独发送

### 7.3 通知设置
#### 7.3.1 个人通知偏好
- **通知类型开关**：
  - 任务相关通知
  - 流程相关通知
  - 系统相关通知
  - 社交相关通知

- **通知时间设置**：
  - 工作时间通知
  - 免打扰时间段
  - 周末通知设置
  - 节假日通知设置

#### 7.3.2 通知频率控制
```yaml
notification_frequency:
  task_assignment:
    immediate: true
    batch_interval: null
    
  task_reminder:
    immediate: false
    batch_interval: "1 hour"
    
  system_maintenance:
    immediate: true
    advance_notice: "24 hours"
    
  daily_summary:
    enabled: true
    send_time: "18:00"
    include_weekends: false
```

## 8. 移动端支持

### 8.1 响应式设计
#### 8.1.1 断点设计
```css
/* 响应式断点 */
@media (max-width: 768px) {
  /* 移动端样式 */
  .task-list {
    .task-item {
      flex-direction: column;
      padding: 12px;
    }
  }
}

@media (min-width: 769px) and (max-width: 1024px) {
  /* 平板端样式 */
  .dashboard {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1025px) {
  /* 桌面端样式 */
  .dashboard {
    grid-template-columns: repeat(4, 1fr);
  }
}
```

#### 8.1.2 移动端优化
- **触摸友好**：
  - 按钮大小适配触摸
  - 滑动操作支持
  - 长按菜单
  - 手势导航

- **性能优化**：
  - 图片懒加载
  - 虚拟滚动
  - 代码分割
  - 缓存策略

### 8.2 PWA支持
#### 8.2.1 离线功能
- **离线缓存**：
  - 静态资源缓存
  - API数据缓存
  - 离线页面
  - 数据同步

- **后台同步**：
  - 网络恢复时自动同步
  - 冲突解决机制
  - 同步状态提示
  - 失败重试机制

#### 8.2.2 原生体验
```json
{
  "name": "工作流门户",
  "short_name": "Workflow",
  "description": "企业工作流管理平台",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#ffffff",
  "theme_color": "#1890ff",
  "icons": [
    {
      "src": "/icons/icon-192x192.png",
      "sizes": "192x192",
      "type": "image/png"
    },
    {
      "src": "/icons/icon-512x512.png",
      "sizes": "512x512",
      "type": "image/png"
    }
  ]
}
```

## 9. 技术架构

### 9.1 前端技术栈
#### 9.1.1 核心框架
```json
{
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.2.0",
    "pinia": "^2.1.0",
    "element-plus": "^2.4.0",
    "@element-plus/icons-vue": "^2.1.0",
    "axios": "^1.6.0",
    "dayjs": "^1.11.0",
    "echarts": "^5.4.0",
    "form-create": "^3.1.0",
    "bpmn-js": "^17.0.0"
  },
  "devDependencies": {
    "vite": "^5.0.0",
    "typescript": "^5.0.0",
    "@vitejs/plugin-vue": "^4.5.0",
    "unplugin-auto-import": "^0.17.0",
    "unplugin-vue-components": "^0.26.0"
  }
}
```

#### 9.1.2 状态管理
```typescript
// 用户状态管理
export const useUserStore = defineStore('user', {
  state: () => ({
    userInfo: null as UserInfo | null,
    permissions: [] as string[],
    preferences: {} as UserPreferences,
    notifications: [] as Notification[]
  }),
  
  actions: {
    async login(credentials: LoginCredentials) {
      const response = await authApi.login(credentials)
      this.userInfo = response.userInfo
      this.permissions = response.permissions
      return response
    },
    
    async loadNotifications() {
      const notifications = await notificationApi.getNotifications()
      this.notifications = notifications
    },
    
    updatePreferences(preferences: Partial<UserPreferences>) {
      this.preferences = { ...this.preferences, ...preferences }
      userApi.updatePreferences(this.preferences)
    }
  }
})
```

### 9.2 组件架构
#### 9.2.1 通用组件
- **布局组件**：
  - AppLayout：应用主布局
  - PageHeader：页面头部
  - Sidebar：侧边导航
  - Breadcrumb：面包屑导航

- **业务组件**：
  - TaskList：任务列表
  - ProcessDiagram：流程图
  - FormRenderer：表单渲染器
  - NotificationCenter：通知中心

#### 9.2.2 组件通信
```typescript
// 事件总线
export const eventBus = mitt<{
  'task:assigned': TaskAssignedEvent
  'process:completed': ProcessCompletedEvent
  'notification:received': NotificationEvent
}>()

// 组件间通信示例
export default defineComponent({
  setup() {
    // 监听任务分配事件
    eventBus.on('task:assigned', (event) => {
      // 更新任务列表
      refreshTaskList()
      // 显示通知
      showNotification('新任务已分配')
    })
    
    return {}
  }
})
```

### 9.3 性能优化
#### 9.3.1 代码分割
```typescript
// 路由懒加载
const routes = [
  {
    path: '/dashboard',
    component: () => import('@/views/Dashboard.vue')
  },
  {
    path: '/tasks',
    component: () => import('@/views/TaskList.vue')
  },
  {
    path: '/processes',
    component: () => import('@/views/ProcessList.vue')
  }
]
```

#### 9.3.2 缓存策略
```typescript
// API缓存配置
const apiCache = new Map()

export const cachedApi = {
  async getUserInfo(userId: string) {
    const cacheKey = `user:${userId}`
    if (apiCache.has(cacheKey)) {
      return apiCache.get(cacheKey)
    }
    
    const userInfo = await userApi.getUserInfo(userId)
    apiCache.set(cacheKey, userInfo)
    
    // 5分钟后清除缓存
    setTimeout(() => {
      apiCache.delete(cacheKey)
    }, 5 * 60 * 1000)
    
    return userInfo
  }
}
```

## 10. 非功能需求

### 10.1 性能要求
- **页面加载时间**：首屏加载 < 2秒
- **操作响应时间**：用户操作响应 < 500ms
- **并发用户支持**：支持1000个并发用户
- **数据加载**：列表数据加载 < 1秒

### 10.2 用户体验
- **界面友好性**：直观易用的界面设计
- **操作便捷性**：减少用户操作步骤
- **错误处理**：友好的错误提示和处理
- **帮助支持**：完善的帮助文档和引导

### 10.3 兼容性
- **浏览器兼容**：Chrome 90+, Firefox 88+, Safari 14+, Edge 90+
- **设备兼容**：桌面端、平板端、移动端
- **分辨率适配**：1920x1080, 1366x768, 375x667等主流分辨率
- **网络适配**：支持3G/4G/5G/WiFi等网络环境
## 11. 详细界面设计和交互规范

### 11.1 个人工作台详细设计

#### 11.1.1 欢迎横幅区域设计
**位置**：页面顶部，占据全宽
**高度**：120像素
**背景**：渐变色背景，从主题色到浅色

**左侧用户信息区域**：
- 用户头像：圆形头像，直径60像素，如果没有头像则显示用户姓名首字母
- 问候语：根据时间显示"早上好"、"下午好"、"晚上好"，后跟用户姓名
- 日期信息：显示当前日期和用户所属部门，格式为"2024年1月2日 | 技术部"

**右侧统计信息区域**：
- 三个统计卡片，水平排列
- 今日完成：显示当天已完成的任务数量
- 待处理：显示当前待处理的任务数量
- 逾期任务：显示已逾期的任务数量，如果有逾期任务则数字显示为红色

#### 11.1.2 可拖拽组件网格系统
**网格规格**：
- 12列网格布局
- 每行高度60像素
- 组件间距10像素
- 支持拖拽调整位置和大小

**编辑模式功能**：
- 右下角浮动编辑按钮，点击进入编辑模式
- 编辑模式下组件边框显示虚线，四角显示调整手柄
- 顶部显示编辑工具栏，包含"重置布局"、"添加组件"、"保存并退出"按钮

#### 11.1.3 任务概览组件详细设计
**组件尺寸**：默认占据6列宽度，8行高度
**组件标题**：左上角显示"任务概览"，右上角显示"查看全部"链接

**上半部分 - 任务统计指标**：
- 三个指标卡片水平排列
- 紧急任务：红色背景，显示紧急优先级任务数量
- 高优先级：橙色背景，显示高优先级任务数量  
- 普通任务：蓝色背景，显示普通优先级任务数量

**下半部分 - 最近任务列表**：
- 显示最近5条任务
- 每条任务显示：任务标题、优先级标签、到达时间、处理按钮
- 任务标题最多显示30个字符，超出显示省略号
- 优先级标签使用不同颜色：紧急(红色)、高(橙色)、普通(蓝色)、低(灰色)
- 到达时间显示相对时间，如"2小时前"、"昨天"
- 处理按钮为蓝色主要按钮，点击直接跳转到任务处理页面

#### 11.1.4 流程统计组件详细设计
**组件尺寸**：默认占据6列宽度，8行高度
**组件标题**：显示"流程统计"

**上半部分 - 统计图表**：
- 使用饼图显示本月流程类型分布
- 图表高度200像素
- 支持点击图表区域查看详细数据
- 图例显示在图表右侧

**下半部分 - 统计摘要**：
- 三行统计信息，每行包含标签和数值
- 本月发起：显示当月发起的流程总数
- 平均耗时：显示流程平均处理时长，单位为小时
- 完成率：显示流程完成百分比

#### 11.1.5 快捷操作组件详细设计
**组件尺寸**：默认占据4列宽度，6行高度
**组件标题**：显示"快捷操作"

**操作项网格布局**：
- 2列3行网格，每个操作项占据一个网格
- 每个操作项包含：图标、标签、徽章（可选）
- 图标大小24像素，居中显示
- 标签显示在图标下方，最多显示8个字符
- 徽章显示在右上角，用于显示数量或状态

**默认快捷操作**：
- 发起流程：显示流程图标，点击跳转到流程发起页面
- 我的待办：显示任务图标，徽章显示待办数量
- 我的申请：显示文档图标，徽章显示进行中的申请数量
- 委托管理：显示用户图标
- 权限申请：显示钥匙图标
- 个人设置：显示设置图标

#### 11.1.6 通知中心组件详细设计
**组件尺寸**：默认占据8列宽度，6行高度
**组件标题**：显示"最新通知"，右侧显示"查看全部"链接和未读数量徽章

**通知列表**：
- 显示最近5条通知
- 每条通知包含：类型图标、标题、内容、时间
- 未读通知背景色为浅蓝色
- 类型图标根据通知类型显示不同颜色：
  - 系统通知：蓝色信息图标
  - 任务通知：绿色任务图标
  - 流程通知：橙色流程图标
  - 安全通知：红色警告图标

**通知交互**：
- 点击通知项标记为已读并跳转到相关页面
- 支持右键菜单：标记已读、删除、查看详情
    
    <!-- 可拖拽的组件网格 -->
    <grid-layout
      v-model:layout="dashboardLayout"
      :col-num="12"
      :row-height="60"
      :is-draggable="editMode"
      :is-resizable="editMode"
      :margin="[10, 10]"
      @layout-updated="saveDashboardLayout"
    >
      <!-- 任务概览组件 -->
      <grid-item
        v-if="isComponentVisible('taskSummary')"
        :x="getComponentLayout('taskSummary').x"
        :y="getComponentLayout('taskSummary').y"
        :w="getComponentLayout('taskSummary').w"
        :h="getComponentLayout('taskSummary').h"
        :i="'taskSummary'"
      >
        <dashboard-card title="任务概览" icon="task">
          <template #actions>
            <el-button text @click="goToTasks">查看全部</el-button>
          </template>
          
          <div class="task-summary-content">
            <div class="summary-metrics">
              <div class="metric-item urgent">
                <div class="metric-value">{{ taskMetrics.urgent }}</div>
                <div class="metric-label">紧急任务</div>
              </div>
              <div class="metric-item high">
                <div class="metric-value">{{ taskMetrics.high }}</div>
                <div class="metric-label">高优先级</div>
              </div>
              <div class="metric-item normal">
                <div class="metric-value">{{ taskMetrics.normal }}</div>
                <div class="metric-label">普通任务</div>
              </div>
            </div>
            
            <div class="recent-tasks">
              <div
                v-for="task in recentTasks"
                :key="task.id"
                class="task-item"
                @click="openTask(task)"
              >
                <div class="task-info">
                  <div class="task-title">{{ task.title }}</div>
                  <div class="task-meta">
                    <el-tag :type="getPriorityType(task.priority)" size="small">
                      {{ getPriorityText(task.priority) }}
                    </el-tag>
                    <span class="task-time">{{ formatRelativeTime(task.createdAt) }}</span>
                  </div>
                </div>
                <div class="task-actions">
                  <el-button size="small" type="primary">处理</el-button>
                </div>
              </div>
            </div>
          </div>
        </dashboard-card>
      </grid-item>
      
      <!-- 流程统计组件 -->
      <grid-item
        v-if="isComponentVisible('processStats')"
        :x="getComponentLayout('processStats').x"
        :y="getComponentLayout('processStats').y"
        :w="getComponentLayout('processStats').w"
        :h="getComponentLayout('processStats').h"
        :i="'processStats'"
      >
        <dashboard-card title="流程统计" icon="workflow">
          <div class="process-stats-content">
            <div class="stats-chart">
              <echarts-component
                :option="processChartOption"
                :height="200"
                @chart-click="handleChartClick"
              />
            </div>
            
            <div class="stats-summary">
              <div class="summary-item">
                <span class="label">本月发起：</span>
                <span class="value">{{ processStats.monthlyInitiated }}</span>
              </div>
              <div class="summary-item">
                <span class="label">平均耗时：</span>
                <span class="value">{{ processStats.avgDuration }}小时</span>
              </div>
              <div class="summary-item">
                <span class="label">完成率：</span>
                <span class="value">{{ processStats.completionRate }}%</span>
              </div>
            </div>
          </div>
        </dashboard-card>
      </grid-item>
      
      <!-- 快捷操作组件 -->
      <grid-item
        v-if="isComponentVisible('quickActions')"
        :x="getComponentLayout('quickActions').x"
        :y="getComponentLayout('quickActions').y"
        :w="getComponentLayout('quickActions').w"
        :h="getComponentLayout('quickActions').h"
        :i="'quickActions'"
      >
        <dashboard-card title="快捷操作" icon="lightning">
          <div class="quick-actions-grid">
            <div
              v-for="action in quickActions"
              :key="action.id"
              class="action-item"
              @click="executeQuickAction(action)"
            >
              <div class="action-icon">
                <el-icon :size="24">
                  <component :is="action.icon" />
                </el-icon>
              </div>
              <div class="action-label">{{ action.label }}</div>
              <div v-if="action.badge" class="action-badge">
                {{ action.badge }}
              </div>
            </div>
          </div>
        </dashboard-card>
      </grid-item>
      
      <!-- 通知中心组件 -->
      <grid-item
        v-if="isComponentVisible('notifications')"
        :x="getComponentLayout('notifications').x"
        :y="getComponentLayout('notifications').y"
        :w="getComponentLayout('notifications').w"
        :h="getComponentLayout('notifications').h"
        :i="'notifications'"
      >
        <dashboard-card title="最新通知" icon="bell">
          <template #actions>
            <el-badge :value="unreadCount" :hidden="unreadCount === 0">
              <el-button text @click="openNotificationCenter">查看全部</el-button>
            </el-badge>
          </template>
          
          <div class="notifications-list">
            <div
              v-for="notification in recentNotifications"
              :key="notification.id"
              class="notification-item"
              :class="{ unread: !notification.read }"
              @click="handleNotificationClick(notification)"
            >
              <div class="notification-icon">
                <el-icon :color="getNotificationColor(notification.type)">
                  <component :is="getNotificationIcon(notification.type)" />
                </el-icon>
              </div>
              <div class="notification-content">
                <div class="notification-title">{{ notification.title }}</div>
                <div class="notification-message">{{ notification.message }}</div>
                <div class="notification-time">{{ formatRelativeTime(notification.createdAt) }}</div>
              </div>
            </div>
          </div>
        </dashboard-card>
      </grid-item>
    </grid-layout>
    
    <!-- 编辑模式工具栏 -->
    <div v-if="editMode" class="edit-toolbar">
      <el-button @click="resetLayout">重置布局</el-button>
      <el-button @click="addComponent">添加组件</el-button>
      <el-button type="primary" @click="saveAndExit">保存并退出</el-button>
    </div>
    
    <!-- 浮动编辑按钮 -->
    <el-button
      v-if="!editMode"
      class="edit-button"
      type="primary"
      circle
      @click="enterEditMode"
    >
      <el-icon><Edit /></el-icon>
    </el-button>
  </div>
</template>

<script setup lang="ts">
interface DashboardComponent {
  id: string
  name: string
  icon: string
  defaultLayout: GridItemLayout
  visible: boolean
}

interface GridItemLayout {
  x: number
  y: number
  w: number
  h: number
}

const dashboardComponents: DashboardComponent[] = [
  {
    id: 'taskSummary',
    name: '任务概览',
    icon: 'task',
    defaultLayout: { x: 0, y: 0, w: 6, h: 8 },
    visible: true
  },
  {
    id: 'processStats',
    name: '流程统计',
    icon: 'workflow',
    defaultLayout: { x: 6, y: 0, w: 6, h: 8 },
    visible: true
  },
  {
    id: 'quickActions',
    name: '快捷操作',
    icon: 'lightning',
    defaultLayout: { x: 0, y: 8, w: 4, h: 6 },
    visible: true
  },
  {
    id: 'notifications',
    name: '最新通知',
    icon: 'bell',
    defaultLayout: { x: 4, y: 8, w: 8, h: 6 },
    visible: true
  }
]

const editMode = ref(false)
const dashboardLayout = ref<GridItemLayout[]>([])
const userInfo = inject('userInfo')
const todayStats = ref({
  completed: 0,
  pending: 0,
  overdue: 0
})

// 加载用户自定义布局
onMounted(async () => {
  await loadDashboardData()
  await loadUserLayout()
})

const loadUserLayout = async () => {
  try {
    const savedLayout = await userPreferenceApi.getDashboardLayout()
    if (savedLayout) {
      dashboardLayout.value = savedLayout
    } else {
      // 使用默认布局
      dashboardLayout.value = dashboardComponents.map(comp => ({
        i: comp.id,
        ...comp.defaultLayout
      }))
    }
  } catch (error) {
    console.error('加载布局失败:', error)
  }
}

const saveDashboardLayout = async (layout: GridItemLayout[]) => {
  try {
    await userPreferenceApi.saveDashboardLayout(layout)
  } catch (error) {
    ElMessage.error('保存布局失败')
  }
}
</script>
```

### 11.2 个性化设置界面详细设计

#### 11.2.1 设置界面整体布局
**界面结构**：采用标签页布局，包含四个主要设置分类
**页面宽度**：最大宽度1200像素，居中显示
**标签页位置**：顶部水平排列

#### 11.2.2 主题设置标签页
**主题模式选择区域**：
- 标题："主题模式"
- 三个单选按钮水平排列：浅色模式、深色模式、跟随系统
- 选中后立即应用主题变化
- 默认选择"浅色模式"

**主题色彩选择区域**：
- 标题："主题色彩"
- 预设颜色网格：3行4列，共12个预设颜色
- 每个颜色选项包含：颜色预览圆形、颜色名称
- 预设颜色包括：蓝色、绿色、紫色、红色、橙色、青色等
- 自定义颜色选择器：支持用户选择任意颜色
- 选中的颜色选项显示选中状态边框

**界面设置区域**：
- 字体大小调节：滑块控件，范围12-18像素，步长1像素
- 布局密度选择：三个单选按钮（紧凑、默认、宽松）
- 圆角风格开关：开启后界面元素显示圆角效果

#### 11.2.3 工作台布局标签页
**组件显示控制区域**：
- 标题："组件显示"
- 每个可用组件一行，包含：开关按钮、组件图标、组件名称
- 可控制的组件：任务概览、流程统计、快捷操作、通知中心、日历视图、数据报表
- 关闭的组件在工作台中隐藏

**布局预设区域**：
- 标题："布局预设"
- 四个预设布局选项：
  - 经典布局：传统的左右分栏布局
  - 简洁布局：只显示核心组件
  - 数据导向：突出统计和图表组件
  - 任务导向：突出任务和待办组件
- 每个预设显示缩略图预览
- 点击预设立即应用到工作台

#### 11.2.4 通知设置标签页
**通知方式设置区域**：
- 浏览器通知开关：开启后请求浏览器通知权限
- 邮件通知开关：控制是否发送邮件通知
- 短信通知开关：控制是否发送短信通知

**通知类型设置区域**：
- 每种通知类型单独配置：
  - 任务分配通知：新任务分配时的通知
  - 任务到期提醒：任务即将到期的提醒
  - 流程状态变更：流程审批结果通知
  - 系统维护通知：系统维护和更新通知
- 每种类型可分别设置浏览器、邮件、短信三种方式

**免打扰时间设置区域**：
- 启用开关：控制是否启用免打扰功能
- 时间段选择：开始时间和结束时间选择器
- 应用日期：复选框组选择周一到周日
- 免打扰期间只发送紧急通知

#### 11.2.5 语言和地区标签页
**语言设置区域**：
- 显示语言下拉选择：简体中文、English、繁體中文
- 选择后立即切换界面语言

**地区设置区域**：
- 时区选择：下拉选择器，支持搜索时区名称
- 日期格式选择：YYYY-MM-DD、MM/DD/YYYY、DD/MM/YYYY、YYYY年MM月DD日
- 时间格式选择：24小时制、12小时制
- 数字格式选择：不同地区的数字和货币显示格式

**设置保存**：
- 页面底部固定保存按钮区域
- 包含"恢复默认"和"保存设置"两个按钮
- 保存时显示加载状态，成功后显示成功提示
          
          <div class="setting-group">
            <h3>主题色彩</h3>
            <div class="color-picker-grid">
              <div
                v-for="color in themeColors"
                :key="color.name"
                class="color-option"
                :class="{ active: preferences.theme.primaryColor === color.value }"
                @click="selectThemeColor(color.value)"
              >
                <div class="color-preview" :style="{ backgroundColor: color.value }"></div>
                <span class="color-name">{{ color.name }}</span>
              </div>
            </div>
            
            <div class="custom-color">
              <el-color-picker
                v-model="preferences.theme.primaryColor"
                @change="applyTheme"
              />
              <span>自定义颜色</span>
            </div>
          </div>
          
          <div class="setting-group">
            <h3>界面设置</h3>
            <el-form label-width="120px">
              <el-form-item label="字体大小">
                <el-slider
                  v-model="preferences.theme.fontSize"
                  :min="12"
                  :max="18"
                  :step="1"
                  show-stops
                  @change="applyTheme"
                />
              </el-form-item>
              
              <el-form-item label="布局密度">
                <el-radio-group v-model="preferences.theme.density" @change="applyTheme">
                  <el-radio label="compact">紧凑</el-radio>
                  <el-radio label="default">默认</el-radio>
                  <el-radio label="comfortable">宽松</el-radio>
                </el-radio-group>
              </el-form-item>
              
              <el-form-item label="圆角风格">
                <el-switch
                  v-model="preferences.theme.roundedCorners"
                  @change="applyTheme"
                />
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 工作台布局 -->
      <el-tab-pane label="工作台布局" name="layout">
        <div class="layout-settings">
          <div class="setting-group">
            <h3>组件显示</h3>
            <div class="component-toggles">
              <div
                v-for="component in dashboardComponents"
                :key="component.id"
                class="component-toggle"
              >
                <el-switch
                  v-model="component.visible"
                  @change="updateComponentVisibility(component)"
                />
                <div class="component-info">
                  <el-icon><component :is="component.icon" /></el-icon>
                  <span>{{ component.name }}</span>
                </div>
              </div>
            </div>
          </div>
          
          <div class="setting-group">
            <h3>布局预设</h3>
            <div class="layout-presets">
              <div
                v-for="preset in layoutPresets"
                :key="preset.id"
                class="preset-option"
                @click="applyLayoutPreset(preset)"
              >
                <div class="preset-preview">
                  <div
                    v-for="item in preset.layout"
                    :key="item.i"
                    class="preview-item"
                    :style="getPreviewItemStyle(item)"
                  ></div>
                </div>
                <div class="preset-name">{{ preset.name }}</div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 通知设置 -->
      <el-tab-pane label="通知设置" name="notifications">
        <div class="notification-settings">
          <div class="setting-group">
            <h3>通知方式</h3>
            <el-form label-width="150px">
              <el-form-item label="浏览器通知">
                <el-switch
                  v-model="preferences.notifications.browser"
                  @change="requestNotificationPermission"
                />
              </el-form-item>
              
              <el-form-item label="邮件通知">
                <el-switch v-model="preferences.notifications.email" />
              </el-form-item>
              
              <el-form-item label="短信通知">
                <el-switch v-model="preferences.notifications.sms" />
              </el-form-item>
            </el-form>
          </div>
          
          <div class="setting-group">
            <h3>通知类型</h3>
            <div class="notification-types">
              <div
                v-for="type in notificationTypes"
                :key="type.id"
                class="notification-type"
              >
                <div class="type-header">
                  <el-icon><component :is="type.icon" /></el-icon>
                  <span class="type-name">{{ type.name }}</span>
                </div>
                <div class="type-settings">
                  <el-checkbox
                    v-model="type.browser"
                    :disabled="!preferences.notifications.browser"
                  >
                    浏览器
                  </el-checkbox>
                  <el-checkbox
                    v-model="type.email"
                    :disabled="!preferences.notifications.email"
                  >
                    邮件
                  </el-checkbox>
                  <el-checkbox
                    v-model="type.sms"
                    :disabled="!preferences.notifications.sms"
                  >
                    短信
                  </el-checkbox>
                </div>
              </div>
            </div>
          </div>
          
          <div class="setting-group">
            <h3>免打扰时间</h3>
            <el-form label-width="100px">
              <el-form-item label="启用">
                <el-switch v-model="preferences.notifications.quietHours.enabled" />
              </el-form-item>
              
              <el-form-item
                v-if="preferences.notifications.quietHours.enabled"
                label="时间段"
              >
                <el-time-picker
                  v-model="preferences.notifications.quietHours.start"
                  placeholder="开始时间"
                />
                <span style="margin: 0 10px">至</span>
                <el-time-picker
                  v-model="preferences.notifications.quietHours.end"
                  placeholder="结束时间"
                />
              </el-form-item>
              
              <el-form-item
                v-if="preferences.notifications.quietHours.enabled"
                label="应用范围"
              >
                <el-checkbox-group v-model="preferences.notifications.quietHours.days">
                  <el-checkbox label="1">周一</el-checkbox>
                  <el-checkbox label="2">周二</el-checkbox>
                  <el-checkbox label="3">周三</el-checkbox>
                  <el-checkbox label="4">周四</el-checkbox>
                  <el-checkbox label="5">周五</el-checkbox>
                  <el-checkbox label="6">周六</el-checkbox>
                  <el-checkbox label="0">周日</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 语言和地区 -->
      <el-tab-pane label="语言和地区" name="locale">
        <div class="locale-settings">
          <el-form label-width="120px">
            <el-form-item label="显示语言">
              <el-select v-model="preferences.locale.language" @change="changeLanguage">
                <el-option label="简体中文" value="zh-CN" />
                <el-option label="English" value="en-US" />
                <el-option label="繁體中文" value="zh-TW" />
              </el-select>
            </el-form-item>
            
            <el-form-item label="时区">
              <timezone-selector v-model="preferences.locale.timezone" />
            </el-form-item>
            
            <el-form-item label="日期格式">
              <el-select v-model="preferences.locale.dateFormat">
                <el-option label="YYYY-MM-DD" value="YYYY-MM-DD" />
                <el-option label="MM/DD/YYYY" value="MM/DD/YYYY" />
                <el-option label="DD/MM/YYYY" value="DD/MM/YYYY" />
                <el-option label="YYYY年MM月DD日" value="YYYY年MM月DD日" />
              </el-select>
            </el-form-item>
            
            <el-form-item label="时间格式">
              <el-radio-group v-model="preferences.locale.timeFormat">
                <el-radio label="24">24小时制</el-radio>
                <el-radio label="12">12小时制</el-radio>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item label="数字格式">
              <el-select v-model="preferences.locale.numberFormat">
                <el-option label="1,234.56" value="en" />
                <el-option label="1 234,56" value="fr" />
                <el-option label="1.234,56" value="de" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>
    
    <!-- 保存按钮 -->
    <div class="settings-footer">
      <el-button @click="resetToDefault">恢复默认</el-button>
      <el-button type="primary" @click="savePreferences" :loading="saving">
        保存设置
      </el-button>
    </div>
  </div>
</template>
```

### 11.3 流程管理详细设计

#### 11.3.1 流程发起界面详细规范

**页面整体布局**：
- 左侧流程分类导航区域，宽度240像素
- 右侧流程列表和详情区域，占据剩余宽度
- 顶部搜索和筛选工具栏，高度60像素

**流程分类导航设计**：
- 分类标签页垂直排列，每个标签高度48像素
- 每个分类显示：图标、分类名称、流程数量徽章
- 支持的分类包括：
  - 人力资源：包含请假、招聘、培训等流程
  - 财务管理：包含报销、采购、预算等流程
  - 行政管理：包含用车、会议室预订等流程
  - IT服务：包含设备申请、权限申请等流程
  - 客户服务：包含客户投诉、服务请求等流程
- 选中的分类高亮显示，未选中的分类显示灰色

**搜索和筛选工具栏**：
- 搜索框：宽度300像素，支持按流程名称和描述搜索
- 排序选择器：支持按最近使用、使用频率、名称、创建时间排序
- 视图切换按钮：网格视图和列表视图切换

**常用流程区域**（如果有收藏的流程）：
- 区域标题："常用流程"，右侧显示"管理"链接
- 流程卡片网格布局，每行最多4个卡片
- 每个卡片右上角显示星形收藏图标
- 卡片显示使用统计：使用次数、平均处理时长

**流程列表区域**：
- 区域标题显示当前分类名称和流程总数
- 网格布局，每行3-4个流程卡片（根据屏幕宽度调整）
- 每个流程卡片包含：
  - 流程图标：64x64像素，居中显示
  - 流程名称：最多显示两行，超出显示省略号
  - 流程描述：最多显示三行，超出显示省略号
  - 流程标签：显示相关业务标签
  - 预计时长：显示流程预计处理时间
  - 参与人数：显示流程涉及的角色数量
  - 操作按钮：主要的"发起流程"按钮
  - 更多操作：下拉菜单包含收藏、查看历史、使用帮助

#### 11.3.2 流程发起对话框详细设计

**对话框规格**：
- 宽度：屏幕宽度的80%，最大1200像素
- 高度：自适应内容，最大屏幕高度的90%
- 位置：屏幕居中显示
- 支持拖拽移动和调整大小

**对话框头部**：
- 标题：显示"发起流程 - [流程名称]"
- 关闭按钮：右上角X按钮

**流程信息展示区域**：
- 流程头部信息：
  - 左侧：流程大图标（128x128像素）
  - 右侧：流程名称（大标题）、流程描述、分类标签、预计时长标签
- 流程步骤预览：
  - 水平步骤条显示流程主要节点
  - 每个步骤显示节点名称和简要说明
  - 当前步骤（发起步骤）高亮显示

**表单填写区域**：
- 区域标题："填写申请信息"
- 动态表单根据流程定义生成
- 表单字段支持：
  - 文本输入框：单行和多行文本
  - 数字输入框：支持最小值、最大值限制
  - 日期选择器：日期、时间、日期时间范围
  - 下拉选择器：单选和多选
  - 文件上传：支持多文件上传
  - 关联数据选择：从其他系统选择数据
- 必填字段标红星标记
- 字段验证实时提示错误信息

**附件上传区域**：
- 区域标题："相关附件"
- 拖拽上传区域：支持拖拽文件到指定区域
- 文件选择按钮：点击选择本地文件
- 文件列表：显示已上传的文件
- 每个文件显示：文件名、大小、上传进度、删除按钮
- 支持的文件类型：PDF、Word、Excel、图片、压缩包
- 单个文件大小限制：50MB
- 总附件大小限制：200MB

**紧急程度设置区域**：
- 区域标题："紧急程度"
- 四个单选按钮：普通、一般、紧急、特急
- 每个级别显示不同颜色和说明：
  - 普通：灰色，常规业务流程
  - 一般：蓝色，需要及时处理
  - 紧急：橙色，优先处理
  - 特急：红色，立即处理

**对话框底部操作区域**：
- 左侧：保存草稿按钮（灰色次要按钮）
- 右侧：取消按钮、提交申请按钮（蓝色主要按钮）
- 提交按钮在表单验证通过前保持禁用状态
- 操作按钮显示加载状态和结果反馈

#### 11.3.3 流程跟踪界面详细设计

**筛选和搜索区域**：
- 水平表单布局，包含多个筛选条件
- 状态筛选：多选下拉框，包含进行中、已完成、已撤回、已拒绝、已暂停
- 流程类型筛选：单选下拉框，显示所有可用的流程类型
- 时间范围筛选：日期范围选择器，支持快捷选择（今天、本周、本月、本季度）
- 搜索按钮和重置按钮

**流程列表设计**：
- 每个流程项占据一行，高度120像素
- 左侧流程信息区域：
  - 流程图标：48x48像素
  - 流程标题：大字体显示，最多两行
  - 流程元信息：流程编号、流程类型、发起时间，小字体灰色显示
- 右侧状态和操作区域：
  - 流程状态标签：不同状态显示不同颜色
  - 优先级标签：非普通优先级时显示
  - 操作按钮组：查看详情、撤回（条件显示）、催办（条件显示）、更多操作

**流程进度显示**：
- 当前节点信息：显示"当前节点：[节点名称]"
- 处理人信息：显示"处理人：[处理人姓名]"或"待分配"
- 进度条：显示流程完成百分比，不同状态显示不同颜色
- 已完成流程显示绿色进度条
- 进行中流程显示蓝色进度条
- 异常流程显示红色进度条

**操作功能说明**：
- 查看详情：跳转到流程详情页面
- 撤回：仅在流程未开始处理时显示，点击后确认撤回
- 催办：仅在流程超时或接近超时时显示，点击后发送催办通知
- 更多操作：下拉菜单包含导出PDF、打印、分享链接等功能

**分页控件**：
- 页面底部居中显示
- 显示总记录数、每页条数选择器、页码导航
- 支持跳转到指定页码
      
      <!-- 搜索和筛选 -->
      <div class="process-filters">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索流程..."
          prefix-icon="Search"
          clearable
          @input="filterProcesses"
        />
        
        <el-select v-model="sortBy" placeholder="排序方式" @change="sortProcesses">
          <el-option label="最近使用" value="recent" />
          <el-option label="使用频率" value="frequency" />
          <el-option label="名称" value="name" />
          <el-option label="创建时间" value="created" />
        </el-select>
      </div>
    </div>
    
    <!-- 流程列表 -->
    <div class="process-list">
      <!-- 常用流程 -->
      <div v-if="favoriteProcesses.length > 0" class="process-section">
        <div class="section-header">
          <h3>常用流程</h3>
          <el-button text @click="manageFavorites">管理</el-button>
        </div>
        <div class="process-grid">
          <div
            v-for="process in favoriteProcesses"
            :key="process.id"
            class="process-card favorite"
            @click="initiateProcess(process)"
          >
            <div class="card-header">
              <img :src="process.icon" class="process-icon" />
              <el-icon class="favorite-icon"><StarFilled /></el-icon>
            </div>
            <div class="card-content">
              <h4 class="process-name">{{ process.name }}</h4>
              <p class="process-description">{{ process.description }}</p>
              <div class="process-stats">
                <span class="usage-count">使用 {{ process.usageCount }} 次</span>
                <span class="avg-duration">平均 {{ process.avgDuration }} 小时</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 分类流程 -->
      <div class="process-section">
        <div class="section-header">
          <h3>{{ getCurrentCategoryName() }}</h3>
          <span class="process-count">{{ filteredProcesses.length }} 个流程</span>
        </div>
        
        <div class="process-grid">
          <div
            v-for="process in filteredProcesses"
            :key="process.id"
            class="process-card"
            @click="initiateProcess(process)"
          >
            <div class="card-header">
              <img :src="process.icon" class="process-icon" />
              <el-dropdown @command="handleProcessAction">
                <el-button text circle size="small">
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :command="{action: 'favorite', process}">
                      {{ process.isFavorite ? '取消收藏' : '添加收藏' }}
                    </el-dropdown-item>
                    <el-dropdown-item :command="{action: 'history', process}">
                      查看历史
                    </el-dropdown-item>
                    <el-dropdown-item :command="{action: 'help', process}">
                      使用帮助
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            
            <div class="card-content">
              <h4 class="process-name">{{ process.name }}</h4>
              <p class="process-description">{{ process.description }}</p>
              
              <div class="process-tags">
                <el-tag
                  v-for="tag in process.tags"
                  :key="tag"
                  size="small"
                  type="info"
                >
                  {{ tag }}
                </el-tag>
              </div>
              
              <div class="process-meta">
                <div class="meta-item">
                  <el-icon><Clock /></el-icon>
                  <span>预计 {{ process.estimatedDuration }} 小时</span>
                </div>
                <div class="meta-item">
                  <el-icon><User /></el-icon>
                  <span>{{ process.participantCount }} 人参与</span>
                </div>
              </div>
            </div>
            
            <div class="card-footer">
              <el-button type="primary" size="small">
                发起流程
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 流程发起对话框 -->
    <el-dialog
      v-model="initiationDialogVisible"
      :title="`发起流程 - ${selectedProcess?.name}`"
      width="80%"
      :before-close="handleDialogClose"
    >
      <div v-if="selectedProcess" class="initiation-content">
        <!-- 流程信息 -->
        <div class="process-info-section">
          <div class="process-header">
            <img :src="selectedProcess.icon" class="large-icon" />
            <div class="process-details">
              <h2>{{ selectedProcess.name }}</h2>
              <p>{{ selectedProcess.description }}</p>
              <div class="process-badges">
                <el-tag type="success">{{ selectedProcess.category }}</el-tag>
                <el-tag type="info">预计 {{ selectedProcess.estimatedDuration }} 小时</el-tag>
              </div>
            </div>
          </div>
          
          <!-- 流程步骤预览 -->
          <div class="process-steps">
            <h4>流程步骤</h4>
            <el-steps :active="0" align-center>
              <el-step
                v-for="(step, index) in selectedProcess.steps"
                :key="index"
                :title="step.name"
                :description="step.description"
              />
            </el-steps>
          </div>
        </div>
        
        <!-- 动态表单 -->
        <div class="form-section">
          <h4>填写申请信息</h4>
          <form-create
            v-model="formData"
            :rule="formRule"
            :option="formOption"
            @change="handleFormChange"
          />
        </div>
        
        <!-- 附件上传 -->
        <div class="attachment-section">
          <h4>相关附件</h4>
          <file-upload
            v-model="attachments"
            :multiple="true"
            :accept="allowedFileTypes"
            :max-size="maxFileSize"
            @upload-success="handleUploadSuccess"
          />
        </div>
        
        <!-- 紧急程度 -->
        <div class="priority-section">
          <h4>紧急程度</h4>
          <el-radio-group v-model="processData.priority">
            <el-radio-button label="low">普通</el-radio-button>
            <el-radio-button label="normal">一般</el-radio-button>
            <el-radio-button label="high">紧急</el-radio-button>
            <el-radio-button label="urgent">特急</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="saveDraft" :loading="savingDraft">
            保存草稿
          </el-button>
          <el-button @click="initiationDialogVisible = false">
            取消
          </el-button>
          <el-button
            type="primary"
            @click="submitProcess"
            :loading="submitting"
            :disabled="!isFormValid"
          >
            提交申请
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
```

#### 11.2.2 流程跟踪界面
```vue
<template>
  <div class="process-tracking">
    <!-- 筛选和搜索 -->
    <div class="tracking-filters">
      <el-form :model="filterForm" inline>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" multiple placeholder="全部状态">
            <el-option label="进行中" value="running" />
            <el-option label="已完成" value="completed" />
            <el-option label="已撤回" value="withdrawn" />
            <el-option label="已拒绝" value="rejected" />
            <el-option label="已暂停" value="suspended" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="流程类型">
          <el-select v-model="filterForm.processType" placeholder="全部类型">
            <el-option
              v-for="type in processTypes"
              :key="type.value"
              :label="type.label"
              :value="type.value"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="searchProcesses">搜索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <!-- 流程列表 -->
    <div class="process-list">
      <div
        v-for="process in processList"
        :key="process.id"
        class="process-item"
        @click="viewProcessDetail(process)"
      >
        <div class="process-header">
          <div class="process-info">
            <img :src="process.icon" class="process-icon" />
            <div class="process-details">
              <h4 class="process-title">{{ process.title }}</h4>
              <div class="process-meta">
                <span class="process-id">{{ process.processNumber }}</span>
                <span class="process-type">{{ process.processType }}</span>
                <span class="process-time">{{ formatDateTime(process.startTime) }}</span>
              </div>
            </div>
          </div>
          
          <div class="process-status">
            <el-tag :type="getStatusType(process.status)">
              {{ getStatusText(process.status) }}
            </el-tag>
            <el-tag v-if="process.priority !== 'normal'" :type="getPriorityType(process.priority)">
              {{ getPriorityText(process.priority) }}
            </el-tag>
          </div>
        </div>
        
        <!-- 流程进度 -->
        <div class="process-progress">
          <div class="progress-info">
            <span>当前节点：{{ process.currentNode?.name || '已结束' }}</span>
            <span v-if="process.currentNode">
              处理人：{{ process.currentNode.assignee?.name || '待分配' }}
            </span>
          </div>
          
          <el-progress
            :percentage="process.progressPercentage"
            :status="getProgressStatus(process.status)"
            :stroke-width="6"
          />
        </div>
        
        <!-- 操作按钮 -->
        <div class="process-actions">
          <el-button size="small" @click.stop="viewDetail(process)">
            查看详情
          </el-button>
          
          <el-button
            v-if="canWithdraw(process)"
            size="small"
            type="warning"
            @click.stop="withdrawProcess(process)"
          >
            撤回
          </el-button>
          
          <el-button
            v-if="canUrge(process)"
            size="small"
            @click.stop="urgeProcess(process)"
          >
            催办
          </el-button>
          
          <el-dropdown @command="handleProcessAction">
            <el-button size="small">
              更多<el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :command="{action: 'export', process}">
                  导出PDF
                </el-dropdown-item>
                <el-dropdown-item :command="{action: 'print', process}">
                  打印
                </el-dropdown-item>
                <el-dropdown-item :command="{action: 'share', process}">
                  分享链接
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>
    
    <!-- 分页 -->
    <el-pagination
      v-model:current-page="pagination.page"
      v-model:page-size="pagination.pageSize"
      :total="pagination.total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
    />
  </div>
</template>
```

### 11.4 任务管理详细设计

#### 11.4.1 任务统计面板设计

**面板布局**：
- 水平排列四个统计卡片，每个卡片宽度相等
- 卡片高度100像素，圆角设计
- 卡片间距16像素

**统计卡片设计规范**：
- 紧急任务卡片：
  - 背景色：红色渐变（#ff4d4f到#ff7875）
  - 图标：警告图标，白色
  - 数字：大字体白色显示紧急任务数量
  - 标签：白色文字"紧急任务"
- 逾期任务卡片：
  - 背景色：橙色渐变（#fa8c16到#ffa940）
  - 图标：时钟图标，白色
  - 数字：大字体白色显示逾期任务数量
  - 标签：白色文字"逾期任务"
- 今日到期卡片：
  - 背景色：黄色渐变（#fadb14到#fff566）
  - 图标：日历图标，深色
  - 数字：大字体深色显示今日到期任务数量
  - 标签：深色文字"今日到期"
- 全部任务卡片：
  - 背景色：蓝色渐变（#1890ff到#40a9ff）
  - 图标：列表图标，白色
  - 数字：大字体白色显示全部任务数量
  - 标签：白色文字"全部任务"

**交互功能**：
- 点击任何统计卡片自动应用对应的筛选条件
- 鼠标悬停时卡片轻微上浮效果
- 数字变化时显示动画效果

#### 11.4.2 智能筛选标签页设计

**筛选标签布局**：
- 水平排列的标签页，支持滚动
- 每个标签包含：图标、名称、数量徽章
- 选中的标签高亮显示，未选中的标签显示灰色

**预设筛选标签**：
- 全部任务：显示所有任务，徽章显示总数
- 待我处理：显示分配给当前用户的任务
- 我发起的：显示当前用户发起的流程中的任务
- 紧急任务：显示紧急和特急优先级的任务
- 逾期任务：显示已超过截止时间的任务
- 今日到期：显示今天截止的任务
- 本周任务：显示本周内需要处理的任务
- 已完成：显示已完成的任务

**筛选操作区域**：
- 高级筛选按钮：点击打开高级筛选抽屉
- 批量操作按钮：选中任务后显示，显示选中数量
- 刷新按钮：手动刷新任务列表
- 视图切换：列表视图和卡片视图切换

#### 11.4.3 任务列表详细设计

**列表头部控制区域**：
- 全选复选框：支持全选和反选所有任务
- 选中状态显示：显示"已选择X个任务"
- 排序控制：下拉选择排序方式
  - 优先级排序：紧急任务优先显示
  - 到期时间排序：即将到期的任务优先显示
  - 创建时间排序：最新任务优先显示
  - 流程类型排序：按流程类型字母顺序排序

**任务项详细设计**：
- 任务项高度：120像素
- 左侧复选框：用于批量选择任务
- 任务内容区域：
  - 任务标题：大字体显示，最多两行，超出显示省略号
  - 任务标签区域：
    - 优先级标签：紧急(红色)、高(橙色)、普通(蓝色)、低(灰色)
    - 状态标签：已逾期(红色)、今日到期(黄色)
  - 任务元信息：
    - 流程类型：显示所属流程的类型
    - 发起人：显示"发起人：[姓名]"
    - 到达时间：显示相对时间，如"2小时前到达"
- 右侧操作区域：
  - 主要操作按钮："处理"按钮，蓝色主要样式
  - 更多操作：下拉菜单包含委托、转办、查看历史等

**任务状态视觉设计**：
- 普通任务：白色背景，灰色边框
- 紧急任务：浅红色背景，红色左边框
- 逾期任务：浅红色背景，红色边框，标题文字为红色
- 今日到期：浅黄色背景，黄色左边框
- 已选中任务：浅蓝色背景，蓝色边框

#### 11.4.4 高级筛选抽屉设计

**抽屉规格**：
- 从右侧滑出，宽度400像素
- 标题："高级筛选"
- 支持拖拽调整宽度

**筛选条件表单**：
- 流程类型筛选：
  - 多选下拉框，支持搜索
  - 显示所有可用的流程类型
  - 支持全选和反选
- 优先级筛选：
  - 复选框组：紧急、高、普通、低
  - 支持多选组合
- 发起人筛选：
  - 用户选择器，支持搜索用户
  - 支持选择多个发起人
- 到达时间筛选：
  - 日期时间范围选择器
  - 支持快捷选择：今天、昨天、本周、上周、本月、上月
- 截止时间筛选：
  - 日期时间范围选择器
  - 支持快捷选择和自定义范围
- 关键词搜索：
  - 文本输入框
  - 支持搜索任务标题和描述内容
  - 支持模糊匹配

**筛选操作按钮**：
- 重置按钮：清空所有筛选条件
- 应用筛选按钮：执行筛选并关闭抽屉
- 保存筛选：将当前筛选条件保存为自定义筛选器

#### 11.4.5 批量操作功能设计

**批量操作触发**：
- 选中一个或多个任务后，顶部显示批量操作工具栏
- 工具栏显示选中的任务数量
- 提供批量操作按钮

**支持的批量操作**：
- 批量标记已读：将选中的任务标记为已读状态
- 批量委托：将选中的任务委托给指定用户
- 批量转办：将选中的任务转办给其他用户
- 批量设置优先级：修改选中任务的优先级
- 批量导出：将选中任务的信息导出为Excel文件

**批量操作确认**：
- 执行批量操作前显示确认对话框
- 显示将要操作的任务数量和操作类型
- 提供操作原因输入框（可选）
- 显示操作可能的影响和后果
- 确认后显示操作进度和结果

**操作结果反馈**：
- 操作成功：显示成功消息和处理的任务数量
- 部分成功：显示成功和失败的任务数量，提供详细报告
- 操作失败：显示错误信息和失败原因
- 支持撤销最近的批量操作（如果可能）
      
      <div class="stat-card overdue">
        <div class="stat-icon">
          <el-icon><Clock /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-number">{{ taskStats.overdue }}</div>
          <div class="stat-label">逾期任务</div>
        </div>
      </div>
      
      <div class="stat-card today">
        <div class="stat-icon">
          <el-icon><Calendar /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-number">{{ taskStats.dueToday }}</div>
          <div class="stat-label">今日到期</div>
        </div>
      </div>
      
      <div class="stat-card total">
        <div class="stat-icon">
          <el-icon><List /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-number">{{ taskStats.total }}</div>
          <div class="stat-label">全部任务</div>
        </div>
      </div>
    </div>
    
    <!-- 智能筛选 -->
    <div class="smart-filters">
      <div class="filter-tabs">
        <div
          v-for="filter in smartFilters"
          :key="filter.id"
          class="filter-tab"
          :class="{ active: activeFilter === filter.id }"
          @click="applySmartFilter(filter.id)"
        >
          <el-icon><component :is="filter.icon" /></el-icon>
          <span>{{ filter.name }}</span>
          <el-badge v-if="filter.count > 0" :value="filter.count" />
        </div>
      </div>
      
      <div class="filter-actions">
        <el-button @click="showAdvancedFilter">
          <el-icon><Filter /></el-icon>
          高级筛选
        </el-button>
        
        <el-button @click="showBatchActions" :disabled="selectedTasks.length === 0">
          <el-icon><Operation /></el-icon>
          批量操作 ({{ selectedTasks.length }})
        </el-button>
      </div>
    </div>
    
    <!-- 任务列表 -->
    <div class="task-list">
      <div class="list-header">
        <el-checkbox
          v-model="selectAll"
          :indeterminate="isIndeterminate"
          @change="handleSelectAll"
        >
          全选
        </el-checkbox>
        
        <div class="sort-options">
          <el-dropdown @command="handleSort">
            <el-button text>
              排序：{{ getSortText() }}<el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="priority">优先级</el-dropdown-item>
                <el-dropdown-item command="dueDate">到期时间</el-dropdown-item>
                <el-dropdown-item command="createTime">创建时间</el-dropdown-item>
                <el-dropdown-item command="processType">流程类型</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      
      <div class="task-items">
        <div
          v-for="task in taskList"
          :key="task.id"
          class="task-item"
          :class="{
            selected: selectedTasks.includes(task.id),
            urgent: task.priority === 'urgent',
            overdue: isOverdue(task),
            'due-today': isDueToday(task)
          }"
          @click="selectTask(task)"
        >
          <div class="task-checkbox">
            <el-checkbox
              :model-value="selectedTasks.includes(task.id)"
              @change="toggleTaskSelection(task.id)"
              @click.stop
            />
          </div>
          
          <div class="task-content" @click="openTask(task)">
            <div class="task-header">
              <div class="task-title">
                <h4>{{ task.title }}</h4>
                <div class="task-badges">
                  <el-tag
                    :type="getPriorityType(task.priority)"
                    size="small"
                  >
                    {{ getPriorityText(task.priority) }}
                  </el-tag>
                  
                  <el-tag
                    v-if="isOverdue(task)"
                    type="danger"
                    size="small"
                  >
                    已逾期
                  </el-tag>
                  
                  <el-tag
                    v-else-if="isDueToday(task)"
                    type="warning"
                    size="small"
                  >
                    今日到期
                  </el-tag>
                </div>
              </div>
              
              <div class="task-meta">
                <span class="process-type">{{ task.processType }}</span>
                <span class="initiator">发起人：{{ task.initiator?.name }}</span>
                <span class="arrive-time">{{ formatRelativeTime(task.arriveTime) }}</span>
              </div>
            </div>
            
            <div class="task-details">
              <div class="task-description">
                {{ task.description || '暂无描述' }}
              </div>
              
              <div class="task-timeline">
                <div class="timeline-item">
                  <el-icon><Clock /></el-icon>
                  <span>到达：{{ formatDateTime(task.arriveTime) }}</span>
                </div>
                
                <div v-if="task.dueTime" class="timeline-item">
                  <el-icon><AlarmClock /></el-icon>
                  <span>截止：{{ formatDateTime(task.dueTime) }}</span>
                </div>
              </div>
            </div>
          </div>
          
          <div class="task-actions">
            <el-button
              type="primary"
              size="small"
              @click.stop="handleTask(task)"
            >
              处理
            </el-button>
            
            <el-dropdown @command="handleTaskAction">
              <el-button size="small">
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :command="{action: 'delegate', task}">
                    委托
                  </el-dropdown-item>
                  <el-dropdown-item :command="{action: 'transfer', task}">
                    转办
                  </el-dropdown-item>
                  <el-dropdown-item :command="{action: 'history', task}">
                    查看历史
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 高级筛选对话框 -->
    <el-drawer
      v-model="advancedFilterVisible"
      title="高级筛选"
      size="400px"
    >
      <div class="advanced-filter-content">
        <el-form :model="advancedFilter" label-width="80px">
          <el-form-item label="流程类型">
            <el-select v-model="advancedFilter.processTypes" multiple>
              <el-option
                v-for="type in processTypes"
                :key="type.value"
                :label="type.label"
                :value="type.value"
              />
            </el-select>
          </el-form-item>
          
          <el-form-item label="优先级">
            <el-checkbox-group v-model="advancedFilter.priorities">
              <el-checkbox label="urgent">紧急</el-checkbox>
              <el-checkbox label="high">高</el-checkbox>
              <el-checkbox label="normal">普通</el-checkbox>
              <el-checkbox label="low">低</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          
          <el-form-item label="发起人">
            <user-selector v-model="advancedFilter.initiators" multiple />
          </el-form-item>
          
          <el-form-item label="到达时间">
            <el-date-picker
              v-model="advancedFilter.arriveTimeRange"
              type="datetimerange"
              range-separator="至"
            />
          </el-form-item>
          
          <el-form-item label="截止时间">
            <el-date-picker
              v-model="advancedFilter.dueTimeRange"
              type="datetimerange"
              range-separator="至"
            />
          </el-form-item>
          
          <el-form-item label="关键词">
            <el-input
              v-model="advancedFilter.keyword"
              placeholder="搜索任务标题或描述"
            />
          </el-form-item>
        </el-form>
        
        <div class="filter-actions">
          <el-button @click="resetAdvancedFilter">重置</el-button>
          <el-button type="primary" @click="applyAdvancedFilter">
            应用筛选
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>
```

这样我们就完成了用户门户的详细界面设计和交互规范。整个需求文档现在包含了：

1. **开发人员工作站**：完整的功能单元管理、流程设计器、表设计器、表单设计器、动作设计器的详细规范
2. **管理员中心**：权限管理、组织架构、功能单元部署、系统监控的详细设计
3. **用户门户**：个人工作台、流程管理、任务管理的详细界面和交互设计

所有三个模块都包含了：
- 详细的界面设计规范
- 完整的技术架构
- 数据库设计
- API接口定义
- 安全架构
- 性能优化策略
- 错误处理机制

这些详细的需求规范为开发团队提供了完整的实施指导，确保各个模块能够独立开发并完美集成。