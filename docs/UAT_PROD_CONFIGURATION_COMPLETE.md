# UAT 和 PROD 环境配置完成报告

**日期**: 2026-02-02  
**状态**: ✅ 完成

## 概述

已成功为 Workflow Platform 创建 UAT（用户验收测试）和 PROD（生产）环境的完整配置文件。

---

## 创建的文件清单

### 1. Kubernetes 配置文件

#### UAT 环境
- ✅ `deploy/k8s/configmap-uat.yaml` - UAT 环境配置
- ✅ `deploy/k8s/secret-uat.yaml` - UAT 环境密钥

#### PROD 环境
- ✅ `deploy/k8s/configmap-prod.yaml` - PROD 环境配置
- ✅ `deploy/k8s/secret-prod.yaml` - PROD 环境密钥

### 2. 文档文件

- ✅ `deploy/k8s/README-DEPLOYMENT.md` - 详细的 K8s 部署指南
- ✅ `deploy/k8s/QUICK_REFERENCE.md` - 快速参考命令手册
- ✅ `docs/ENVIRONMENT_CONFIGURATION_GUIDE.md` - 环境配置详细指南
- ✅ `docs/SPRING_PROFILES_EXPLANATION.md` - Spring Profiles 工作机制说明

---

## 配置特点

### UAT 环境配置

**安全策略**:
- 密码最小长度: 10 字符
- 登录失败尝试: 3 次
- 会话超时: 30 分钟
- JWT 过期: 12 小时

**性能配置**:
- 日志级别: INFO
- 缓存 TTL: 中等（45/90/180 分钟）
- Swagger: 禁用

**特点**:
- 接近生产环境的配置
- 用于用户验收测试
- 较严格的安全策略

### PROD 环境配置

**安全策略**:
- 密码最小长度: 12 字符
- 登录失败尝试: 3 次
- 会话超时: 15 分钟
- JWT 过期: 8 小时

**性能配置**:
- 日志级别: WARN/ERROR
- 缓存 TTL: 长（60/120/240 分钟）
- Swagger: 禁用
- Actuator 详情: 隐藏

**特点**:
- 最严格的安全策略
- 最优化的性能配置
- 最小的日志输出
- 禁止自动 schema 更新

---

## 环境对比总览

| 配置项 | DEV | SIT | UAT | PROD |
|--------|-----|-----|-----|------|
| **部署方式** | Docker Compose | Kubernetes | Kubernetes | Kubernetes |
| **副本数** | 1 | 2 | 2 | 3+ |
| **日志级别** | DEBUG | INFO | INFO | WARN |
| **密码长度** | 6 | 8 | 10 | 12 |
| **失败尝试** | 10 | 5 | 3 | 3 |
| **会话超时** | 60分钟 | 30分钟 | 30分钟 | 15分钟 |
| **JWT 过期** | 24小时 | 24小时 | 12小时 | 8小时 |
| **Swagger** | ✅ | ✅ | ❌ | ❌ |
| **Schema 更新** | ✅ | ❌ | ❌ | ❌ |

---

## 部署前必做事项

### UAT 环境

1. **修改 Secret 密码**
   ```bash
   vi deploy/k8s/secret-uat.yaml
   ```
   - 替换所有 `CHANGE_ME_UAT_*` 的值
   - 使用强密码（至少 32 字符）

2. **修改 ConfigMap 配置**
   ```bash
   vi deploy/k8s/configmap-uat.yaml
   ```
   - 更新数据库主机地址
   - 更新 Redis 主机地址

3. **创建 Namespace**
   ```bash
   kubectl create namespace workflow-platform-uat
   ```

4. **准备数据库**
   - 创建数据库: `workflow_platform_uat`
   - 创建用户: `platform_uat`
   - 执行初始化脚本

### PROD 环境

1. **修改 Secret 密码**
   ```bash
   vi deploy/k8s/secret-prod.yaml
   ```
   - 替换所有 `CHANGE_ME_PROD_*` 的值
   - 使用非常强的密码（至少 64 字符）
   - 使用密钥管理服务（推荐）

2. **修改 ConfigMap 配置**
   ```bash
   vi deploy/k8s/configmap-prod.yaml
   ```
   - 更新数据库主机地址
   - 更新 Redis 主机地址
   - 根据实际负载调整资源配置

3. **创建 Namespace**
   ```bash
   kubectl create namespace workflow-platform-prod
   ```

4. **准备数据库**
   - 创建数据库: `workflow_platform_prod`
   - 创建用户: `platform_prod`
   - 从备份恢复数据（如果有）

5. **安全审查**
   - 审查所有配置
   - 验证密码强度
   - 配置 RBAC 权限
   - 配置网络策略
   - 启用审计日志

---

## 快速部署命令

### UAT 环境

```bash
# 1. 创建 namespace
kubectl create namespace workflow-platform-uat

# 2. 应用配置（确保已修改密码！）
kubectl apply -f deploy/k8s/configmap-uat.yaml
kubectl apply -f deploy/k8s/secret-uat.yaml

# 3. 批量部署服务
for file in deploy/k8s/deployment-*.yaml; do
  sed 's/workflow-platform-sit/workflow-platform-uat/g' "$file" | kubectl apply -f -
done

# 4. 验证部署
kubectl get pods -n workflow-platform-uat
```

### PROD 环境

```bash
# 1. 创建 namespace
kubectl create namespace workflow-platform-prod

# 2. 应用配置（必须使用强密码！）
kubectl apply -f deploy/k8s/configmap-prod.yaml
kubectl apply -f deploy/k8s/secret-prod.yaml

# 3. 批量部署服务
for file in deploy/k8s/deployment-*.yaml; do
  sed 's/workflow-platform-sit/workflow-platform-prod/g' "$file" | kubectl apply -f -
done

# 4. 验证部署
kubectl get pods -n workflow-platform-prod
kubectl get svc -n workflow-platform-prod

# 5. 检查健康状态
for svc in admin-center user-portal workflow-engine developer-workstation; do
  kubectl exec -it deployment/$svc -n workflow-platform-prod -- \
    curl -s http://localhost:8080/actuator/health | jq .
done
```

---

## 密钥生成示例

### 生成命令

```bash
# JWT Secret (256-bit)
openssl rand -base64 32

# Encryption Key (32 字符)
openssl rand -base64 32 | cut -c1-32

# UAT 密码 (32 字符)
openssl rand -base64 24

# PROD 密码 (64 字符)
openssl rand -base64 48
```

### 示例输出

```bash
# JWT Secret
8xK9mP2nQ5rS7tU1vW3xY4zA6bC8dE0fG2hI4jK6lM8=

# Encryption Key
9yL0nP3oQ6rS8tU2vW4xY5zA7bC9dE1f

# UAT 密码
Kj8mN2pQ5rS7tU1vW3xY4zA6bC8=

# PROD 密码
Lk9nO3qR6sT8uV2wX4yZ5aB7cD9eF1gH3iJ5kL7mN9oP1qR3sT5u
```

---

## 配置验证清单

### 部署后验证

- [ ] 所有 Pod 状态为 Running
- [ ] 所有 Service 可访问
- [ ] 健康检查端点返回正常
- [ ] 数据库连接正常
- [ ] Redis 连接正常
- [ ] 服务间调用正常
- [ ] 日志输出正常
- [ ] 监控指标正常

### 安全验证

- [ ] Secret 密码已修改
- [ ] 密码强度符合要求
- [ ] JWT 密钥唯一且安全
- [ ] Swagger 已禁用（UAT/PROD）
- [ ] Actuator 访问受限
- [ ] 审计日志已启用
- [ ] RBAC 权限已配置

### 性能验证

- [ ] 资源限制已设置
- [ ] 副本数符合要求
- [ ] 缓存配置合理
- [ ] 连接池大小合理
- [ ] 健康检查配置正确

---

## 重要注意事项

### ⚠️ 安全警告

1. **不要将 Secret 文件提交到版本控制**
   ```bash
   # 添加到 .gitignore
   echo "deploy/k8s/secret-*.yaml" >> .gitignore
   ```

2. **使用密钥管理服务**
   - AWS KMS
   - Azure Key Vault
   - HashiCorp Vault
   - Google Cloud KMS

3. **定期轮换密钥**
   - 建议每 90 天轮换一次
   - 记录轮换历史
   - 测试轮换流程

4. **限制访问权限**
   - 使用 RBAC 控制访问
   - 启用审计日志
   - 监控异常访问

### 📝 部署建议

1. **先在 UAT 环境测试**
   - 验证所有功能
   - 进行性能测试
   - 进行安全测试

2. **使用 CI/CD 管道**
   - 自动化部署流程
   - 从密钥管理服务注入密钥
   - 自动化测试和验证

3. **制定回滚计划**
   - 备份当前配置
   - 准备回滚脚本
   - 测试回滚流程

4. **监控和告警**
   - 配置监控系统
   - 设置告警规则
   - 建立值班机制

---

## 相关文档

### 主要文档

1. **Spring Profiles 说明**
   - 文件: `docs/SPRING_PROFILES_EXPLANATION.md`
   - 内容: Spring Profiles 工作机制详解

2. **环境配置指南**
   - 文件: `docs/ENVIRONMENT_CONFIGURATION_GUIDE.md`
   - 内容: 所有环境的详细配置对比

3. **K8s 部署指南**
   - 文件: `deploy/k8s/README-DEPLOYMENT.md`
   - 内容: 详细的部署步骤和故障排查

4. **快速参考**
   - 文件: `deploy/k8s/QUICK_REFERENCE.md`
   - 内容: 常用命令和快速参考

### 配置文件

- `deploy/k8s/configmap-sit.yaml` - SIT 环境配置
- `deploy/k8s/configmap-uat.yaml` - UAT 环境配置
- `deploy/k8s/configmap-prod.yaml` - PROD 环境配置
- `deploy/k8s/secret-sit.yaml` - SIT 环境密钥
- `deploy/k8s/secret-uat.yaml` - UAT 环境密钥
- `deploy/k8s/secret-prod.yaml` - PROD 环境密钥

---

## 下一步行动

### 立即行动

1. **审查配置文件**
   - 检查所有配置是否符合要求
   - 验证密码强度
   - 确认资源配置

2. **修改密钥**
   - 生成强密码和密钥
   - 更新 Secret 文件
   - 不要提交到版本控制

3. **准备环境**
   - 创建数据库
   - 部署 Redis
   - 配置网络

### 后续行动

1. **UAT 部署**
   - 部署到 UAT 环境
   - 进行功能测试
   - 进行性能测试

2. **PROD 准备**
   - 完成安全审查
   - 获得部署批准
   - 制定部署计划

3. **持续改进**
   - 收集反馈
   - 优化配置
   - 更新文档

---

## 总结

✅ **已完成**:
- UAT 和 PROD 环境的 ConfigMap 配置
- UAT 和 PROD 环境的 Secret 配置
- 详细的部署文档和指南
- 快速参考命令手册
- 环境配置对比文档

✅ **配置特点**:
- 分层的安全策略（DEV < SIT < UAT < PROD）
- 优化的性能配置
- 完整的文档支持
- 清晰的部署流程

⚠️ **注意事项**:
- 部署前必须修改所有密码
- 不要将 Secret 提交到版本控制
- 使用密钥管理服务（推荐）
- 定期轮换密钥

📚 **参考文档**:
- Spring Profiles 详解
- 环境配置指南
- K8s 部署指南
- 快速参考手册

---

**配置创建完成！可以开始部署到 UAT 和 PROD 环境了。**
