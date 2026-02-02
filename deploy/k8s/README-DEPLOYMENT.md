# Kubernetes 部署指南

## 环境说明

本项目支持以下环境的 Kubernetes 部署：

- **SIT** (System Integration Testing) - 系统集成测试环境
- **UAT** (User Acceptance Testing) - 用户验收测试环境
- **PROD** (Production) - 生产环境

## 文件结构

```
deploy/k8s/
├── configmap-sit.yaml          # SIT 环境配置
├── configmap-uat.yaml          # UAT 环境配置
├── configmap-prod.yaml         # PROD 环境配置
├── secret-sit.yaml             # SIT 环境密钥
├── secret-uat.yaml             # UAT 环境密钥
├── secret-prod.yaml            # PROD 环境密钥
├── deployment-admin-center.yaml
├── deployment-user-portal.yaml
├── deployment-workflow-engine.yaml
├── deployment-developer-workstation.yaml
├── deployment-api-gateway.yaml
├── deployment-frontend.yaml
├── ingress.yaml
└── README-DEPLOYMENT.md        # 本文件
```

## 部署前准备

### 1. 创建 Namespace

```bash
# SIT 环境
kubectl create namespace workflow-platform-sit

# UAT 环境
kubectl create namespace workflow-platform-uat

# PROD 环境
kubectl create namespace workflow-platform-prod
```

### 2. 修改 Secret 配置

⚠️ **重要**: 部署前必须修改所有 `CHANGE_ME` 开头的密码和密钥！

#### 生成强密码和密钥

```bash
# 生成 JWT Secret (256-bit)
openssl rand -base64 32

# 生成 Encryption Key (32 字符)
openssl rand -base64 32 | cut -c1-32

# 生成强密码 (64 字符)
openssl rand -base64 48
```

#### 编辑 Secret 文件

```bash
# UAT 环境
vi deploy/k8s/secret-uat.yaml

# PROD 环境
vi deploy/k8s/secret-prod.yaml
```

替换所有 `CHANGE_ME_*` 的值。

### 3. 修改 ConfigMap 配置

根据实际环境修改数据库和 Redis 的主机地址：

```bash
# UAT 环境
vi deploy/k8s/configmap-uat.yaml

# PROD 环境
vi deploy/k8s/configmap-prod.yaml
```

需要修改的配置项：
- `SPRING_DATASOURCE_URL`: 数据库连接地址
- `SPRING_REDIS_HOST`: Redis 主机地址

### 4. 修改 Deployment 文件

将现有的 deployment 文件复制并修改 namespace：

```bash
# 示例：为 UAT 环境创建 deployment
sed 's/workflow-platform-sit/workflow-platform-uat/g' \
    deploy/k8s/deployment-admin-center.yaml > \
    deploy/k8s/deployment-admin-center-uat.yaml
```

或者手动编辑每个 deployment 文件，将 `namespace: workflow-platform-sit` 改为对应的环境。

## 部署步骤

### SIT 环境部署

```bash
# 1. 应用 ConfigMap
kubectl apply -f deploy/k8s/configmap-sit.yaml

# 2. 应用 Secret
kubectl apply -f deploy/k8s/secret-sit.yaml

# 3. 部署后端服务
kubectl apply -f deploy/k8s/deployment-admin-center.yaml
kubectl apply -f deploy/k8s/deployment-user-portal.yaml
kubectl apply -f deploy/k8s/deployment-workflow-engine.yaml
kubectl apply -f deploy/k8s/deployment-developer-workstation.yaml
kubectl apply -f deploy/k8s/deployment-api-gateway.yaml

# 4. 部署前端
kubectl apply -f deploy/k8s/deployment-frontend.yaml

# 5. 配置 Ingress
kubectl apply -f deploy/k8s/ingress.yaml

# 6. 验证部署
kubectl get pods -n workflow-platform-sit
kubectl get svc -n workflow-platform-sit
```

### UAT 环境部署

```bash
# 1. 应用 ConfigMap
kubectl apply -f deploy/k8s/configmap-uat.yaml

# 2. 应用 Secret（确保已修改密码！）
kubectl apply -f deploy/k8s/secret-uat.yaml

# 3. 部署服务（需要先创建 UAT 版本的 deployment 文件）
# 方法1: 使用 sed 批量替换 namespace
for file in deployment-*.yaml; do
  sed 's/workflow-platform-sit/workflow-platform-uat/g' "$file" | \
  kubectl apply -f -
done

# 方法2: 手动创建 UAT deployment 文件后部署
kubectl apply -f deploy/k8s/deployment-admin-center-uat.yaml
kubectl apply -f deploy/k8s/deployment-user-portal-uat.yaml
# ... 其他服务

# 4. 验证部署
kubectl get pods -n workflow-platform-uat
```

### PROD 环境部署

⚠️ **生产环境部署需要额外的审批和验证流程！**

```bash
# 1. 应用 ConfigMap
kubectl apply -f deploy/k8s/configmap-prod.yaml

# 2. 应用 Secret（必须使用强密码！）
kubectl apply -f deploy/k8s/secret-prod.yaml

# 3. 部署服务
for file in deployment-*.yaml; do
  sed 's/workflow-platform-sit/workflow-platform-prod/g' "$file" | \
  kubectl apply -f -
done

# 4. 验证部署
kubectl get pods -n workflow-platform-prod
kubectl get svc -n workflow-platform-prod

# 5. 监控日志
kubectl logs -f deployment/admin-center -n workflow-platform-prod
```

## 环境配置对比

| 配置项 | SIT | UAT | PROD |
|--------|-----|-----|------|
| **Replicas** | 2 | 2 | 3+ |
| **日志级别** | INFO | INFO | WARN |
| **SQL 日志** | WARN | WARN | ERROR |
| **密码长度** | 8 | 10 | 12 |
| **失败尝试** | 5 | 3 | 3 |
| **会话超时** | 30分钟 | 30分钟 | 15分钟 |
| **JWT 过期** | 24小时 | 12小时 | 8小时 |
| **Swagger** | ✅ | ❌ | ❌ |
| **Health Details** | always | when_authorized | never |
| **Schema Update** | true | false | false |

## 验证部署

### 检查 Pod 状态

```bash
# 查看所有 Pod
kubectl get pods -n workflow-platform-{sit|uat|prod}

# 查看 Pod 详情
kubectl describe pod <pod-name> -n workflow-platform-{sit|uat|prod}

# 查看 Pod 日志
kubectl logs <pod-name> -n workflow-platform-{sit|uat|prod}
```

### 检查 Service

```bash
# 查看所有 Service
kubectl get svc -n workflow-platform-{sit|uat|prod}

# 测试 Service 连接
kubectl run -it --rm debug --image=busybox --restart=Never -n workflow-platform-{sit|uat|prod} -- \
  wget -O- http://admin-center-service:8080/actuator/health
```

### 检查 ConfigMap 和 Secret

```bash
# 查看 ConfigMap
kubectl get configmap workflow-platform-config -n workflow-platform-{sit|uat|prod} -o yaml

# 查看 Secret（不显示值）
kubectl get secret workflow-platform-secrets -n workflow-platform-{sit|uat|prod}

# 查看 Secret 的 keys
kubectl get secret workflow-platform-secrets -n workflow-platform-{sit|uat|prod} -o jsonpath='{.data}'
```

## 更新配置

### 更新 ConfigMap

```bash
# 1. 修改 configmap 文件
vi deploy/k8s/configmap-{sit|uat|prod}.yaml

# 2. 应用更新
kubectl apply -f deploy/k8s/configmap-{sit|uat|prod}.yaml

# 3. 重启 Pod 使配置生效
kubectl rollout restart deployment/admin-center -n workflow-platform-{sit|uat|prod}
kubectl rollout restart deployment/user-portal -n workflow-platform-{sit|uat|prod}
# ... 其他服务
```

### 更新 Secret

```bash
# 1. 修改 secret 文件
vi deploy/k8s/secret-{sit|uat|prod}.yaml

# 2. 应用更新
kubectl apply -f deploy/k8s/secret-{sit|uat|prod}.yaml

# 3. 重启 Pod 使配置生效
kubectl rollout restart deployment/admin-center -n workflow-platform-{sit|uat|prod}
```

## 回滚部署

```bash
# 查看部署历史
kubectl rollout history deployment/admin-center -n workflow-platform-{sit|uat|prod}

# 回滚到上一个版本
kubectl rollout undo deployment/admin-center -n workflow-platform-{sit|uat|prod}

# 回滚到指定版本
kubectl rollout undo deployment/admin-center --to-revision=2 -n workflow-platform-{sit|uat|prod}
```

## 扩缩容

```bash
# 手动扩容
kubectl scale deployment/admin-center --replicas=3 -n workflow-platform-{sit|uat|prod}

# 查看扩容状态
kubectl get deployment admin-center -n workflow-platform-{sit|uat|prod}
```

## 故障排查

### Pod 无法启动

```bash
# 查看 Pod 事件
kubectl describe pod <pod-name> -n workflow-platform-{sit|uat|prod}

# 查看 Pod 日志
kubectl logs <pod-name> -n workflow-platform-{sit|uat|prod}

# 查看上一次运行的日志（如果 Pod 重启了）
kubectl logs <pod-name> --previous -n workflow-platform-{sit|uat|prod}
```

### 配置问题

```bash
# 进入 Pod 检查环境变量
kubectl exec -it <pod-name> -n workflow-platform-{sit|uat|prod} -- env | grep SPRING

# 进入 Pod 检查配置文件
kubectl exec -it <pod-name> -n workflow-platform-{sit|uat|prod} -- cat /app/config/application.yml
```

### 网络问题

```bash
# 测试 Service 连接
kubectl run -it --rm debug --image=busybox --restart=Never -n workflow-platform-{sit|uat|prod} -- \
  wget -O- http://admin-center-service:8080/actuator/health

# 测试 DNS 解析
kubectl run -it --rm debug --image=busybox --restart=Never -n workflow-platform-{sit|uat|prod} -- \
  nslookup admin-center-service
```

## 安全最佳实践

### 1. Secret 管理

- ✅ 使用强密码（至少 32 字符）
- ✅ 定期轮换密钥（建议每 90 天）
- ✅ 不要将 Secret 文件提交到版本控制
- ✅ 使用密钥管理服务（AWS KMS, Azure Key Vault, HashiCorp Vault）
- ✅ 限制对 Secret 的访问权限

### 2. RBAC 配置

```bash
# 创建只读角色
kubectl create role pod-reader --verb=get,list,watch --resource=pods -n workflow-platform-prod

# 绑定角色到用户
kubectl create rolebinding read-pods --role=pod-reader --user=jane -n workflow-platform-prod
```

### 3. 网络策略

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-same-namespace
  namespace: workflow-platform-prod
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector: {}
```

### 4. 资源限制

生产环境建议配置：

```yaml
resources:
  requests:
    memory: "1Gi"
    cpu: "1000m"
  limits:
    memory: "2Gi"
    cpu: "2000m"
```

## 监控和告警

### 健康检查

```bash
# 检查所有服务的健康状态
for svc in admin-center user-portal workflow-engine developer-workstation; do
  echo "Checking $svc..."
  kubectl exec -it deployment/$svc -n workflow-platform-prod -- \
    curl -s http://localhost:8080/actuator/health | jq .
done
```

### 日志聚合

建议使用 ELK Stack 或 Loki 进行日志聚合：

```bash
# 查看最近的错误日志
kubectl logs -l app=admin-center -n workflow-platform-prod --tail=100 | grep ERROR
```

## 备份和恢复

### 备份配置

```bash
# 备份所有配置
kubectl get all,configmap,secret -n workflow-platform-prod -o yaml > backup-prod-$(date +%Y%m%d).yaml

# 备份特定资源
kubectl get configmap workflow-platform-config -n workflow-platform-prod -o yaml > configmap-backup.yaml
```

### 恢复配置

```bash
# 从备份恢复
kubectl apply -f backup-prod-20260202.yaml
```

## 联系方式

如有问题，请联系：
- DevOps 团队: devops@example.com
- 平台团队: platform@example.com
