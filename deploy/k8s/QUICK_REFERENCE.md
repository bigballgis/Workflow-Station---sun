# Kubernetes 部署快速参考

## 一键部署命令

### SIT 环境

```bash
# 完整部署
kubectl create namespace workflow-platform-sit
kubectl apply -f deploy/k8s/configmap-sit.yaml
kubectl apply -f deploy/k8s/secret-sit.yaml
kubectl apply -f deploy/k8s/deployment-admin-center.yaml
kubectl apply -f deploy/k8s/deployment-user-portal.yaml
kubectl apply -f deploy/k8s/deployment-workflow-engine.yaml
kubectl apply -f deploy/k8s/deployment-developer-workstation.yaml
kubectl apply -f deploy/k8s/deployment-api-gateway.yaml
kubectl apply -f deploy/k8s/deployment-frontend.yaml
kubectl apply -f deploy/k8s/ingress.yaml
```

### UAT 环境

```bash
# 创建 namespace
kubectl create namespace workflow-platform-uat

# 应用配置
kubectl apply -f deploy/k8s/configmap-uat.yaml
kubectl apply -f deploy/k8s/secret-uat.yaml

# 批量部署（替换 namespace）
for file in deploy/k8s/deployment-*.yaml; do
  sed 's/workflow-platform-sit/workflow-platform-uat/g' "$file" | kubectl apply -f -
done
```

### PROD 环境

```bash
# 创建 namespace
kubectl create namespace workflow-platform-prod

# 应用配置
kubectl apply -f deploy/k8s/configmap-prod.yaml
kubectl apply -f deploy/k8s/secret-prod.yaml

# 批量部署（替换 namespace）
for file in deploy/k8s/deployment-*.yaml; do
  sed 's/workflow-platform-sit/workflow-platform-prod/g' "$file" | kubectl apply -f -
done
```

---

## 常用命令

### 查看状态

```bash
# 查看所有 Pod
kubectl get pods -n workflow-platform-{sit|uat|prod}

# 查看所有 Service
kubectl get svc -n workflow-platform-{sit|uat|prod}

# 查看所有资源
kubectl get all -n workflow-platform-{sit|uat|prod}

# 实时监控 Pod 状态
watch kubectl get pods -n workflow-platform-{sit|uat|prod}
```

### 查看日志

```bash
# 查看 Pod 日志
kubectl logs <pod-name> -n workflow-platform-{sit|uat|prod}

# 实时跟踪日志
kubectl logs -f <pod-name> -n workflow-platform-{sit|uat|prod}

# 查看上一次运行的日志
kubectl logs <pod-name> --previous -n workflow-platform-{sit|uat|prod}

# 查看所有 admin-center Pod 的日志
kubectl logs -l app=admin-center -n workflow-platform-{sit|uat|prod}
```

### 重启服务

```bash
# 重启单个服务
kubectl rollout restart deployment/admin-center -n workflow-platform-{sit|uat|prod}

# 重启所有服务
kubectl rollout restart deployment -n workflow-platform-{sit|uat|prod}

# 查看重启状态
kubectl rollout status deployment/admin-center -n workflow-platform-{sit|uat|prod}
```

### 扩缩容

```bash
# 扩容到 3 个副本
kubectl scale deployment/admin-center --replicas=3 -n workflow-platform-{sit|uat|prod}

# 缩容到 1 个副本
kubectl scale deployment/admin-center --replicas=1 -n workflow-platform-{sit|uat|prod}
```

### 更新配置

```bash
# 更新 ConfigMap
kubectl apply -f deploy/k8s/configmap-{sit|uat|prod}.yaml

# 更新 Secret
kubectl apply -f deploy/k8s/secret-{sit|uat|prod}.yaml

# 重启服务使配置生效
kubectl rollout restart deployment -n workflow-platform-{sit|uat|prod}
```

### 调试

```bash
# 进入 Pod
kubectl exec -it <pod-name> -n workflow-platform-{sit|uat|prod} -- /bin/bash

# 查看 Pod 详情
kubectl describe pod <pod-name> -n workflow-platform-{sit|uat|prod}

# 查看 Pod 事件
kubectl get events -n workflow-platform-{sit|uat|prod} --sort-by='.lastTimestamp'

# 端口转发（本地访问）
kubectl port-forward <pod-name> 8080:8080 -n workflow-platform-{sit|uat|prod}
```

### 删除资源

```bash
# 删除单个 Deployment
kubectl delete deployment admin-center -n workflow-platform-{sit|uat|prod}

# 删除所有 Deployment
kubectl delete deployment --all -n workflow-platform-{sit|uat|prod}

# 删除整个 Namespace（谨慎使用！）
kubectl delete namespace workflow-platform-{sit|uat|prod}
```

---

## 健康检查

```bash
# 检查所有服务健康状态
for svc in admin-center user-portal workflow-engine developer-workstation; do
  echo "=== $svc ==="
  kubectl exec -it deployment/$svc -n workflow-platform-{sit|uat|prod} -- \
    curl -s http://localhost:8080/actuator/health | jq .
done
```

---

## 密钥生成

```bash
# JWT Secret (256-bit)
openssl rand -base64 32

# Encryption Key (32 字符)
openssl rand -base64 32 | cut -c1-32

# 强密码 (64 字符)
openssl rand -base64 48
```

---

## 环境变量对比

| 变量 | DEV | SIT | UAT | PROD |
|------|-----|-----|-----|------|
| SPRING_PROFILES_ACTIVE | dev | sit | uat | prod |
| LOG_LEVEL_ROOT | DEBUG | INFO | INFO | WARN |
| SECURITY_PASSWORD_MIN_LENGTH | 6 | 8 | 10 | 12 |
| SECURITY_LOGIN_MAX_FAILED_ATTEMPTS | 10 | 5 | 3 | 3 |
| SECURITY_SESSION_TIMEOUT_MINUTES | 60 | 30 | 30 | 15 |
| JWT_EXPIRATION | 86400000 | 86400000 | 43200000 | 28800000 |
| SWAGGER_ENABLED | true | true | false | false |

---

## 故障排查速查

### Pod 无法启动

```bash
# 1. 查看 Pod 状态
kubectl get pods -n workflow-platform-{sit|uat|prod}

# 2. 查看 Pod 详情
kubectl describe pod <pod-name> -n workflow-platform-{sit|uat|prod}

# 3. 查看日志
kubectl logs <pod-name> -n workflow-platform-{sit|uat|prod}

# 4. 查看事件
kubectl get events -n workflow-platform-{sit|uat|prod} --sort-by='.lastTimestamp'
```

### 配置问题

```bash
# 查看 ConfigMap
kubectl get configmap workflow-platform-config -n workflow-platform-{sit|uat|prod} -o yaml

# 查看 Secret keys
kubectl get secret workflow-platform-secrets -n workflow-platform-{sit|uat|prod} -o jsonpath='{.data}' | jq 'keys'

# 查看 Pod 环境变量
kubectl exec <pod-name> -n workflow-platform-{sit|uat|prod} -- env | grep SPRING
```

### 网络问题

```bash
# 测试 Service 连接
kubectl run -it --rm debug --image=busybox --restart=Never -n workflow-platform-{sit|uat|prod} -- \
  wget -O- http://admin-center-service:8080/actuator/health

# 测试 DNS
kubectl run -it --rm debug --image=busybox --restart=Never -n workflow-platform-{sit|uat|prod} -- \
  nslookup admin-center-service
```

---

## 备份和恢复

```bash
# 备份所有配置
kubectl get all,configmap,secret -n workflow-platform-{sit|uat|prod} -o yaml > backup-$(date +%Y%m%d).yaml

# 恢复配置
kubectl apply -f backup-20260202.yaml
```

---

## 监控命令

```bash
# 查看资源使用情况
kubectl top pods -n workflow-platform-{sit|uat|prod}
kubectl top nodes

# 查看 Pod 资源限制
kubectl describe pod <pod-name> -n workflow-platform-{sit|uat|prod} | grep -A 5 "Limits:"

# 查看 Deployment 状态
kubectl get deployment -n workflow-platform-{sit|uat|prod} -o wide
```

---

## 安全检查

```bash
# 查看 RBAC 权限
kubectl get rolebindings -n workflow-platform-{sit|uat|prod}
kubectl get roles -n workflow-platform-{sit|uat|prod}

# 查看网络策略
kubectl get networkpolicies -n workflow-platform-{sit|uat|prod}

# 查看 Pod 安全策略
kubectl get psp
```

---

## 有用的别名

添加到 `~/.bashrc` 或 `~/.zshrc`:

```bash
# Kubernetes 别名
alias k='kubectl'
alias kgp='kubectl get pods'
alias kgs='kubectl get svc'
alias kgd='kubectl get deployment'
alias kl='kubectl logs'
alias kd='kubectl describe'
alias ke='kubectl exec -it'

# 环境特定别名
alias k-sit='kubectl -n workflow-platform-sit'
alias k-uat='kubectl -n workflow-platform-uat'
alias k-prod='kubectl -n workflow-platform-prod'

# 常用组合
alias kgp-sit='kubectl get pods -n workflow-platform-sit'
alias kgp-uat='kubectl get pods -n workflow-platform-uat'
alias kgp-prod='kubectl get pods -n workflow-platform-prod'
```

---

## 紧急联系方式

- **DevOps 团队**: devops@example.com
- **平台团队**: platform@example.com
- **On-Call**: +1-xxx-xxx-xxxx

---

## 相关文档

- [详细部署指南](README-DEPLOYMENT.md)
- [环境配置指南](../docs/ENVIRONMENT_CONFIGURATION_GUIDE.md)
- [Spring Profiles 说明](../docs/SPRING_PROFILES_EXPLANATION.md)
