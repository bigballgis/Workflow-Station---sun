# Kubernetes 部署指南

本目录包含将 Workflow Platform 部署到 Kubernetes 集群所需的所有配置文件。

## 📁 文件结构

```
deploy/k8s/
├── README.md                              # 本文件
├── configmap-sit.yaml                     # SIT 环境 ConfigMap
├── secret-sit.yaml                        # SIT 环境 Secret（需要修改密码）
├── deployment-workflow-engine.yaml        # Workflow Engine 部署配置
├── deployment-admin-center.yaml           # Admin Center 部署配置
├── deployment-user-portal.yaml            # User Portal 部署配置
├── deployment-developer-workstation.yaml  # Developer Workstation 部署配置
├── deployment-api-gateway.yaml            # API Gateway 部署配置
├── deployment-frontend.yaml               # 所有前端服务部署配置
└── ingress.yaml                           # Ingress 配置（外部访问）
```

## 🚀 快速开始

### 前提条件

1. **Kubernetes 集群**: 已有可用的 K8s 集群
2. **kubectl**: 已安装并配置好 kubectl
3. **命名空间**: 创建命名空间 `workflow-platform-sit`
4. **外部资源**: 
   - PostgreSQL 数据库（SIT 环境）
   - Redis 缓存（SIT 环境）
5. **镜像仓库**: 已构建并推送所有服务的 Docker 镜像

### 部署步骤

#### 1. 创建命名空间

```bash
kubectl create namespace workflow-platform-sit
```

#### 2. 修改配置文件

**修改 `configmap-sit.yaml`**:
- 更新 `SPRING_DATASOURCE_URL` 为实际的数据库地址
- 更新 `SPRING_REDIS_HOST` 为实际的 Redis 地址

**修改 `secret-sit.yaml`**:
- 更新所有 `CHANGE_ME_*` 占位符为实际的密码和密钥
- 生成密钥的命令：
  ```bash
  # JWT 密钥（256-bit）
  openssl rand -base64 32
  
  # 加密密钥（32 字节）
  openssl rand -base64 32 | cut -c1-32
  ```

**修改所有 deployment 文件**:
- 更新 `image:` 字段为实际的镜像地址
- 例如: `your-registry/workflow-engine:latest` → `harbor.company.com/workflow/workflow-engine:v1.0.0`

**修改 `ingress.yaml`**:
- 更新所有域名为实际的域名
- 例如: `admin-sit.your-domain.com` → `admin-sit.company.com`

#### 3. 应用配置

```bash
# 进入 k8s 目录
cd deploy/k8s

# 1. 创建 ConfigMap
kubectl apply -f configmap-sit.yaml

# 2. 创建 Secret
kubectl apply -f secret-sit.yaml

# 3. 部署后端服务
kubectl apply -f deployment-workflow-engine.yaml
kubectl apply -f deployment-admin-center.yaml
kubectl apply -f deployment-user-portal.yaml
kubectl apply -f deployment-developer-workstation.yaml
kubectl apply -f deployment-api-gateway.yaml

# 4. 部署前端服务
kubectl apply -f deployment-frontend.yaml

# 5. 创建 Ingress（如果需要外部访问）
kubectl apply -f ingress.yaml
```

#### 4. 验证部署

```bash
# 查看所有 Pod 状态
kubectl get pods -n workflow-platform-sit

# 查看所有 Service
kubectl get svc -n workflow-platform-sit

# 查看 Ingress
kubectl get ingress -n workflow-platform-sit

# 查看某个 Pod 的日志
kubectl logs -f <pod-name> -n workflow-platform-sit

# 查看 Pod 详细信息
kubectl describe pod <pod-name> -n workflow-platform-sit
```

#### 5. 测试访问

```bash
# 测试后端服务健康检查
kubectl port-forward svc/workflow-engine-service 8080:8080 -n workflow-platform-sit
curl http://localhost:8080/actuator/health

# 测试前端服务
kubectl port-forward svc/admin-center-frontend-service 8080:80 -n workflow-platform-sit
# 浏览器访问 http://localhost:8080
```

## 🔧 配置说明

### ConfigMap 配置项

| 配置项 | 说明 | 示例值 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | Spring Profile | `sit`, `uat`, `prod` |
| `SERVER_PORT` | 服务端口 | `8080` |
| `SPRING_DATASOURCE_URL` | 数据库连接 URL | `jdbc:postgresql://db-host:5432/workflow_platform` |
| `SPRING_REDIS_HOST` | Redis 主机 | `redis-host` |
| `SPRING_REDIS_PORT` | Redis 端口 | `6379` |
| `ADMIN_CENTER_URL` | Admin Center 服务 URL | `http://admin-center-service:8080` |
| `WORKFLOW_ENGINE_URL` | Workflow Engine 服务 URL | `http://workflow-engine-service:8080` |
| `LOG_LEVEL_ROOT` | 根日志级别 | `INFO`, `WARN`, `DEBUG` |

### Secret 配置项

| 配置项 | 说明 | 生成方法 |
|--------|------|---------|
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | 从 DBA 获取 |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | 从 DBA 获取 |
| `SPRING_REDIS_PASSWORD` | Redis 密码 | 从运维获取 |
| `JWT_SECRET` | JWT 签名密钥 | `openssl rand -base64 32` |
| `ENCRYPTION_SECRET_KEY` | 数据加密密钥 | `openssl rand -base64 32 \| cut -c1-32` |

### 资源配置

每个后端服务的默认资源配置：

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

每个前端服务的默认资源配置：

```yaml
resources:
  requests:
    memory: "128Mi"
    cpu: "100m"
  limits:
    memory: "256Mi"
    cpu: "200m"
```

根据实际负载调整这些值。

## 🔍 故障排查

### Pod 无法启动

```bash
# 查看 Pod 事件
kubectl describe pod <pod-name> -n workflow-platform-sit

# 查看 Pod 日志
kubectl logs <pod-name> -n workflow-platform-sit

# 查看上一次运行的日志（如果 Pod 重启了）
kubectl logs <pod-name> -n workflow-platform-sit --previous
```

### 常见问题

#### 1. ImagePullBackOff

**原因**: 无法拉取镜像

**解决**:
- 检查镜像地址是否正确
- 检查镜像仓库凭证是否配置
- 检查网络连接

```bash
# 创建镜像拉取凭证
kubectl create secret docker-registry regcred \
  --docker-server=<your-registry-server> \
  --docker-username=<your-name> \
  --docker-password=<your-password> \
  --docker-email=<your-email> \
  -n workflow-platform-sit

# 在 Deployment 中添加 imagePullSecrets
spec:
  template:
    spec:
      imagePullSecrets:
      - name: regcred
```

#### 2. CrashLoopBackOff

**原因**: 应用启动失败

**解决**:
- 查看应用日志找出错误原因
- 检查数据库连接配置
- 检查 Redis 连接配置
- 检查环境变量是否正确

```bash
# 查看详细日志
kubectl logs -f <pod-name> -n workflow-platform-sit
```

#### 3. 数据库连接失败

**原因**: 无法连接到数据库

**解决**:
- 检查数据库地址是否正确
- 检查数据库用户名密码是否正确
- 检查网络连接（K8s 集群是否能访问数据库）
- 检查数据库防火墙规则

```bash
# 在 Pod 中测试数据库连接
kubectl exec -it <pod-name> -n workflow-platform-sit -- /bin/sh
# 在 Pod 中执行
nc -zv <db-host> 5432
```

#### 4. Redis 连接失败

**原因**: 无法连接到 Redis

**解决**:
- 检查 Redis 地址是否正确
- 检查 Redis 密码是否正确
- 检查网络连接

```bash
# 在 Pod 中测试 Redis 连接
kubectl exec -it <pod-name> -n workflow-platform-sit -- /bin/sh
# 在 Pod 中执行
nc -zv <redis-host> 6379
```

## 📊 监控和日志

### 查看日志

```bash
# 实时查看日志
kubectl logs -f <pod-name> -n workflow-platform-sit

# 查看最近 100 行日志
kubectl logs --tail=100 <pod-name> -n workflow-platform-sit

# 查看多个 Pod 的日志（使用标签选择器）
kubectl logs -l app=workflow-engine -n workflow-platform-sit
```

### 健康检查

所有后端服务都配置了健康检查端点：

```bash
# 通过 port-forward 访问健康检查
kubectl port-forward svc/<service-name> 8080:8080 -n workflow-platform-sit
curl http://localhost:8080/actuator/health
```

### Metrics

如果配置了 Prometheus，可以访问 metrics 端点：

```bash
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/prometheus
```

## 🔄 更新部署

### 更新镜像

```bash
# 方法 1: 修改 YAML 文件后重新应用
kubectl apply -f deployment-workflow-engine.yaml

# 方法 2: 直接设置新镜像
kubectl set image deployment/workflow-engine \
  workflow-engine=your-registry/workflow-engine:v1.0.1 \
  -n workflow-platform-sit

# 查看滚动更新状态
kubectl rollout status deployment/workflow-engine -n workflow-platform-sit
```

### 回滚部署

```bash
# 查看部署历史
kubectl rollout history deployment/workflow-engine -n workflow-platform-sit

# 回滚到上一个版本
kubectl rollout undo deployment/workflow-engine -n workflow-platform-sit

# 回滚到指定版本
kubectl rollout undo deployment/workflow-engine --to-revision=2 -n workflow-platform-sit
```

### 更新配置

```bash
# 更新 ConfigMap
kubectl apply -f configmap-sit.yaml

# 更新 Secret
kubectl apply -f secret-sit.yaml

# 重启 Pod 以应用新配置
kubectl rollout restart deployment/workflow-engine -n workflow-platform-sit
```

## 🔐 安全最佳实践

1. **Secret 管理**:
   - 不要将 Secret 文件提交到 Git
   - 使用 `.gitignore` 忽略 Secret 文件
   - 考虑使用 Sealed Secrets 或 External Secrets Operator

2. **RBAC**:
   - 为应用创建专用的 ServiceAccount
   - 配置最小权限原则

3. **网络策略**:
   - 配置 NetworkPolicy 限制 Pod 间通信
   - 只允许必要的流量

4. **镜像安全**:
   - 使用私有镜像仓库
   - 定期扫描镜像漏洞
   - 使用特定版本标签，避免使用 `latest`

## 📈 扩缩容

### 手动扩缩容

```bash
# 扩容到 3 个副本
kubectl scale deployment/workflow-engine --replicas=3 -n workflow-platform-sit

# 缩容到 1 个副本
kubectl scale deployment/workflow-engine --replicas=1 -n workflow-platform-sit
```

### 自动扩缩容（HPA）

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: workflow-engine-hpa
  namespace: workflow-platform-sit
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: workflow-engine
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

## 🌍 多环境部署

### SIT 环境

```bash
kubectl apply -f configmap-sit.yaml
kubectl apply -f secret-sit.yaml
kubectl apply -f deployment-*.yaml
```

### UAT 环境

1. 复制 SIT 配置文件
2. 修改命名空间为 `workflow-platform-uat`
3. 修改配置值（数据库、Redis 等）
4. 应用配置

### PROD 环境

1. 复制 UAT 配置文件
2. 修改命名空间为 `workflow-platform-prod`
3. 修改配置值
4. 增加副本数（建议至少 3 个）
5. 配置 HPA
6. 配置监控和告警
7. 应用配置

## 📚 参考文档

- [Kubernetes 官方文档](https://kubernetes.io/docs/)
- [Spring Boot on Kubernetes](https://spring.io/guides/gs/spring-boot-kubernetes/)
- [K8s 环境变量完整清单](../../K8S_ENVIRONMENT_VARIABLES_CHECKLIST.md)
- [K8s 部署环境变量审计报告](../../K8S_DEPLOYMENT_ENV_AUDIT.md)

## 🆘 获取帮助

如果遇到问题，请：

1. 查看本文档的故障排查部分
2. 查看 Pod 日志和事件
3. 联系 DevOps 团队
4. 查阅相关文档

---

**文档版本**: 1.0  
**创建日期**: 2026-02-02  
**维护人员**: DevOps Team
