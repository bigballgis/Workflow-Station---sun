# 非功能需求

## 1. 性能需求

### 1.1 响应时间要求
#### 1.1.1 用户界面响应时间
- **页面加载时间**：首次加载 < 3秒，后续页面切换 < 1秒
- **表单提交响应**：< 2秒
- **文件上传响应**：小文件(< 10MB) < 5秒，大文件(< 100MB) < 30秒
- **搜索查询响应**：< 1秒
- **流程图渲染**：复杂流程(< 50个节点) < 2秒

#### 1.1.2 API响应时间
- **查询接口**：95%的请求 < 500ms，99%的请求 < 1秒
- **创建/更新接口**：95%的请求 < 1秒，99%的请求 < 2秒
- **文件操作接口**：根据文件大小，平均传输速度 > 10MB/s
- **批量操作接口**：处理1000条记录 < 10秒

#### 1.1.3 工作流执行性能
- **流程启动时间**：< 1秒
- **任务分配时间**：< 500ms
- **流程流转时间**：< 2秒
- **并行任务处理**：支持同时处理100个并行任务

### 1.2 吞吐量要求
#### 1.2.1 并发用户支持
- **在线用户数**：支持1000个并发在线用户
- **峰值并发**：支持2000个并发用户（短时间峰值）
- **同时活跃会话**：支持500个同时活跃的用户会话
- **新用户注册**：支持每分钟100个新用户注册

#### 1.2.2 事务处理能力
- **流程启动**：每分钟支持1000个新流程启动
- **任务处理**：每分钟支持5000个任务处理操作
- **数据查询**：每秒支持10000个查询请求
- **文件上传**：每分钟支持200个文件上传操作

#### 1.2.3 数据处理能力
- **数据库连接**：支持最大200个并发数据库连接
- **内存使用**：单个应用实例内存使用 < 2GB
- **CPU使用率**：正常负载下CPU使用率 < 70%
- **磁盘I/O**：支持每秒1000次磁盘读写操作

### 1.3 资源使用限制
#### 1.3.1 服务器资源
```yaml
# 最小配置要求
minimum_requirements:
  cpu: 4 cores
  memory: 8GB
  disk: 100GB SSD
  network: 1Gbps

# 推荐配置
recommended_requirements:
  cpu: 8 cores
  memory: 16GB
  disk: 500GB SSD
  network: 10Gbps

# 高可用配置
high_availability:
  cpu: 16 cores
  memory: 32GB
  disk: 1TB SSD (RAID 10)
  network: 10Gbps (双网卡)
```

#### 1.3.2 数据库资源
```sql
-- PostgreSQL 配置建议
-- 内存配置
shared_buffers = '2GB'                    -- 25% of total RAM
effective_cache_size = '6GB'              -- 75% of total RAM
work_mem = '256MB'                        -- Per connection
maintenance_work_mem = '512MB'            -- Maintenance operations

-- 连接配置
max_connections = 200                     -- Maximum connections
max_prepared_transactions = 100           -- Prepared transactions

-- 性能配置
checkpoint_completion_target = 0.9        -- Checkpoint target
wal_buffers = '64MB'                      -- WAL buffer size
random_page_cost = 1.1                    -- SSD optimized
```

## 2. 可用性需求

### 2.1 系统可用性
#### 2.1.1 可用性指标
- **系统可用性**：99.5%（年停机时间 < 43.8小时）
- **计划内维护**：每月不超过4小时，在非工作时间进行
- **计划外停机**：每次不超过30分钟，每月不超过2小时
- **数据恢复时间**：RTO (Recovery Time Objective) < 1小时
- **数据恢复点**：RPO (Recovery Point Objective) < 15分钟

#### 2.1.2 故障恢复
- **自动故障检测**：系统故障检测时间 < 30秒
- **自动故障切换**：主备切换时间 < 2分钟
- **服务恢复时间**：单个服务恢复时间 < 5分钟
- **数据一致性检查**：故障恢复后数据一致性验证 < 10分钟

### 2.2 高可用架构
#### 2.2.1 应用层高可用
```yaml
# Kubernetes 高可用配置
apiVersion: apps/v1
kind: Deployment
metadata:
  name: workflow-platform
spec:
  replicas: 3                             # 多实例部署
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0                   # 零停机部署
  template:
    spec:
      containers:
      - name: app
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:                    # 健康检查
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:                   # 就绪检查
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
```

#### 2.2.2 数据库高可用
```yaml
# PostgreSQL 主从配置
postgresql_cluster:
  primary:
    host: postgres-primary
    port: 5432
    replication_slots: 2
  
  standby:
    - host: postgres-standby-1
      port: 5432
      sync_mode: synchronous
    - host: postgres-standby-2
      port: 5432
      sync_mode: asynchronous
  
  failover:
    automatic: true
    timeout: 30s
    check_interval: 5s
```

### 2.3 备份和恢复
#### 2.3.1 数据备份策略
```bash
# 备份策略配置
backup_strategy:
  full_backup:
    frequency: daily
    time: "02:00"
    retention: 30 days
  
  incremental_backup:
    frequency: hourly
    retention: 7 days
  
  transaction_log_backup:
    frequency: every 15 minutes
    retention: 24 hours

# 备份验证
backup_verification:
  integrity_check: daily
  restore_test: weekly
  disaster_recovery_drill: monthly
```

#### 2.3.2 恢复程序
```sql
-- 数据库恢复程序
-- 1. 停止应用服务
-- 2. 恢复数据库
pg_restore --clean --create --verbose \
  --host=localhost --port=5432 \
  --username=postgres \
  --dbname=workflow_platform \
  /backup/workflow_platform_backup.sql

-- 3. 验证数据完整性
SELECT COUNT(*) FROM function_units;
SELECT COUNT(*) FROM workflow_definitions;
SELECT COUNT(*) FROM users;

-- 4. 启动应用服务
-- 5. 验证系统功能
```

## 3. 可扩展性需求

### 3.1 水平扩展能力
#### 3.1.1 应用服务扩展
- **无状态设计**：应用服务完全无状态，支持任意数量实例
- **负载均衡**：支持基于CPU、内存、请求数的自动扩缩容
- **会话管理**：使用Redis集中式会话存储
- **文件存储**：使用MinIO分布式对象存储

#### 3.1.2 数据库扩展
- **读写分离**：支持一主多从的读写分离架构
- **分库分表**：支持按业务域或数据量进行分库分表
- **连接池**：支持动态调整数据库连接池大小
- **缓存层**：多级缓存减少数据库压力

### 3.2 垂直扩展能力
#### 3.2.1 资源动态调整
```yaml
# Kubernetes HPA 配置
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: workflow-platform-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: workflow-platform
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
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Percent
        value: 100
        periodSeconds: 15
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60
```

### 3.3 功能扩展能力
#### 3.3.1 插件架构
- **热插拔**：支持运行时加载和卸载插件
- **API扩展**：插件可以扩展REST API端点
- **UI扩展**：插件可以扩展前端组件和页面
- **工作流扩展**：插件可以添加自定义节点类型和动作

#### 3.3.2 多租户支持
- **数据隔离**：支持基于租户的数据隔离
- **权限隔离**：支持租户级别的权限管理
- **资源隔离**：支持租户级别的资源配额
- **定制化**：支持租户级别的界面和功能定制

## 4. 安全性需求

### 4.1 身份认证和授权
#### 4.1.1 认证机制
- **多因素认证**：支持用户名密码 + 短信/邮箱验证码
- **单点登录**：支持SAML 2.0和OAuth 2.0协议
- **会话管理**：会话超时时间可配置，默认8小时
- **密码策略**：
  - 最小长度8位，包含大小写字母、数字、特殊字符
  - 密码有效期90天
  - 不能重复使用最近5次密码
  - 连续5次登录失败锁定账户30分钟

#### 4.1.2 授权控制
```java
// RBAC 权限模型
@PreAuthorize("hasRole('ADMIN') or hasPermission(#functionUnitId, 'FunctionUnit', 'WRITE')")
public void updateFunctionUnit(UUID functionUnitId, UpdateRequest request) {
    // 实现逻辑
}

// 数据权限控制
@PostFilter("hasPermission(filterObject, 'READ')")
public List<FunctionUnit> getFunctionUnits() {
    // 实现逻辑
}
```

#### 4.1.3 权限模型
```sql
-- 权限表结构
CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    resource_type VARCHAR(50) NOT NULL,    -- 资源类型
    resource_id UUID,                      -- 资源ID
    action VARCHAR(50) NOT NULL,           -- 操作类型
    principal_type VARCHAR(20) NOT NULL,   -- 主体类型(USER/ROLE/GROUP)
    principal_id UUID NOT NULL,            -- 主体ID
    granted BOOLEAN NOT NULL DEFAULT true, -- 是否授权
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 权限检查索引
CREATE INDEX idx_permissions_lookup ON permissions 
(resource_type, resource_id, action, principal_type, principal_id);
```

### 4.2 数据安全
#### 4.2.1 数据加密
- **传输加密**：所有HTTP通信使用TLS 1.3
- **存储加密**：敏感数据使用AES-256加密存储
- **密钥管理**：使用专用密钥管理服务(KMS)
- **数据脱敏**：日志和导出数据自动脱敏处理

#### 4.2.2 数据访问控制
```java
// 数据访问审计
@Aspect
@Component
public class DataAccessAuditAspect {
    
    @Around("@annotation(Auditable)")
    public Object auditDataAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        String operation = joinPoint.getSignature().getName();
        String resource = extractResourceInfo(joinPoint.getArgs());
        
        // 记录访问日志
        auditService.logDataAccess(userId, operation, resource, System.currentTimeMillis());
        
        try {
            Object result = joinPoint.proceed();
            auditService.logAccessSuccess(userId, operation, resource);
            return result;
        } catch (Exception e) {
            auditService.logAccessFailure(userId, operation, resource, e.getMessage());
            throw e;
        }
    }
}
```

### 4.3 网络安全
#### 4.3.1 网络防护
- **防火墙规则**：只开放必要端口(80, 443, 22)
- **DDoS防护**：支持每秒10万次请求的DDoS攻击防护
- **IP白名单**：支持基于IP地址的访问控制
- **API限流**：每个用户每分钟最多1000次API调用

#### 4.3.2 安全配置
```yaml
# Nginx 安全配置
server {
    listen 443 ssl http2;
    server_name workflow.hsbc.com;
    
    # SSL 配置
    ssl_certificate /etc/ssl/certs/workflow.crt;
    ssl_certificate_key /etc/ssl/private/workflow.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;
    ssl_prefer_server_ciphers off;
    
    # 安全头
    add_header Strict-Transport-Security "max-age=63072000" always;
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    add_header X-XSS-Protection "1; mode=block";
    add_header Content-Security-Policy "default-src 'self'";
    
    # 限流配置
    limit_req_zone $binary_remote_addr zone=api:10m rate=100r/m;
    limit_req zone=api burst=20 nodelay;
    
    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## 5. 可维护性需求

### 5.1 代码质量
#### 5.1.1 代码规范
- **代码覆盖率**：单元测试覆盖率 > 80%，集成测试覆盖率 > 60%
- **代码复杂度**：圈复杂度 < 10，方法长度 < 50行
- **代码重复率**：重复代码 < 5%
- **技术债务**：SonarQube技术债务评级 A级

#### 5.1.2 文档要求
- **API文档**：所有API接口必须有完整的OpenAPI文档
- **代码注释**：关键业务逻辑必须有详细注释，注释覆盖率 > 30%
- **架构文档**：系统架构、数据库设计、部署指南等文档
- **用户手册**：面向最终用户的操作手册和培训材料

### 5.2 监控和诊断
#### 5.2.1 应用监控
```java
// 自定义监控指标
@Component
public class BusinessMetrics {
    
    private final Counter functionUnitCreated = Counter.builder("function.unit.created")
        .description("Number of function units created")
        .register(Metrics.globalRegistry);
    
    private final Timer workflowExecutionTime = Timer.builder("workflow.execution.time")
        .description("Workflow execution time")
        .register(Metrics.globalRegistry);
    
    private final Gauge activeUsers = Gauge.builder("users.active")
        .description("Number of active users")
        .register(Metrics.globalRegistry, this, BusinessMetrics::getActiveUserCount);
    
    @EventListener
    public void onFunctionUnitCreated(FunctionUnitCreatedEvent event) {
        functionUnitCreated.increment(
            Tags.of("user", event.getUserId(), "type", event.getType())
        );
    }
}
```

#### 5.2.2 日志管理
```xml
<!-- 结构化日志配置 -->
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <version/>
                <logLevel/>
                <message/>
                <mdc/>
                <arguments/>
                <stackTrace/>
            </providers>
        </encoder>
    </appender>
    
    <!-- 业务日志 -->
    <logger name="com.hsbc.workflow.business" level="INFO" additivity="false">
        <appender-ref ref="BUSINESS_LOG"/>
    </logger>
    
    <!-- 安全日志 -->
    <logger name="com.hsbc.workflow.security" level="INFO" additivity="false">
        <appender-ref ref="SECURITY_LOG"/>
    </logger>
    
    <!-- 性能日志 -->
    <logger name="com.hsbc.workflow.performance" level="DEBUG" additivity="false">
        <appender-ref ref="PERFORMANCE_LOG"/>
    </logger>
</configuration>
```

### 5.3 部署和运维
#### 5.3.1 CI/CD流水线
```yaml
# GitLab CI 配置
stages:
  - test
  - build
  - security-scan
  - deploy-test
  - deploy-prod

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"

cache:
  paths:
    - .m2/repository/
    - node_modules/

test:
  stage: test
  script:
    - mvn clean test
    - npm run test:unit
  coverage: '/Total.*?([0-9]{1,3})%/'
  artifacts:
    reports:
      junit:
        - target/surefire-reports/TEST-*.xml
        - target/failsafe-reports/TEST-*.xml
      coverage_report:
        coverage_format: cobertura
        path: target/site/cobertura/coverage.xml

security-scan:
  stage: security-scan
  script:
    - mvn org.owasp:dependency-check-maven:check
    - npm audit --audit-level moderate
  artifacts:
    reports:
      dependency_scanning: target/dependency-check-report.json

deploy-prod:
  stage: deploy-prod
  script:
    - kubectl apply -f k8s/
    - kubectl rollout status deployment/workflow-platform
  environment:
    name: production
    url: https://workflow.hsbc.com
  when: manual
  only:
    - main
```

#### 5.3.2 健康检查
```java
// 自定义健康检查
@Component
public class WorkflowHealthIndicator implements HealthIndicator {
    
    @Autowired
    private FlowableProcessEngine processEngine;
    
    @Autowired
    private DataSource dataSource;
    
    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        try {
            // 检查工作流引擎
            long processCount = processEngine.getRepositoryService()
                .createProcessDefinitionQuery().count();
            builder.withDetail("workflow.processes", processCount);
            
            // 检查数据库连接
            try (Connection connection = dataSource.getConnection()) {
                boolean valid = connection.isValid(5);
                builder.withDetail("database.connection", valid ? "UP" : "DOWN");
            }
            
            // 检查外部依赖
            checkExternalDependencies(builder);
            
            return builder.up().build();
            
        } catch (Exception e) {
            return builder.down(e).build();
        }
    }
    
    private void checkExternalDependencies(Health.Builder builder) {
        // 检查Redis连接
        try {
            redisTemplate.opsForValue().get("health-check");
            builder.withDetail("redis.connection", "UP");
        } catch (Exception e) {
            builder.withDetail("redis.connection", "DOWN");
        }
        
        // 检查MinIO连接
        try {
            minioClient.bucketExists(BucketExistsArgs.builder().bucket("health-check").build());
            builder.withDetail("minio.connection", "UP");
        } catch (Exception e) {
            builder.withDetail("minio.connection", "DOWN");
        }
    }
}
```

## 6. 兼容性需求

### 6.1 浏览器兼容性
#### 6.1.1 支持的浏览器
- **Chrome**：版本90及以上
- **Firefox**：版本88及以上  
- **Safari**：版本14及以上
- **Edge**：版本90及以上
- **不支持**：Internet Explorer

#### 6.1.2 响应式设计
- **桌面端**：1920x1080, 1366x768, 1440x900
- **平板端**：1024x768, 768x1024
- **移动端**：375x667, 414x896, 360x640

### 6.2 系统兼容性
#### 6.2.1 操作系统支持
- **Linux**：Ubuntu 20.04+, CentOS 8+, RHEL 8+
- **Windows**：Windows Server 2019+
- **容器**：Docker 20.10+, Kubernetes 1.20+

#### 6.2.2 数据库版本
- **PostgreSQL**：12.x, 13.x, 14.x, 15.x
- **Redis**：6.x, 7.x
- **MinIO**：RELEASE.2023-01-02T09-40-09Z及以上

## 7. 国际化需求

### 7.1 多语言支持
#### 7.1.1 支持语言
- **中文**：简体中文(zh-CN)
- **英文**：美式英语(en-US)
- **扩展性**：支持添加其他语言包

#### 7.1.2 国际化实现
```javascript
// 前端国际化配置
import { createI18n } from 'vue-i18n'

const messages = {
  'zh-CN': {
    common: {
      save: '保存',
      cancel: '取消',
      confirm: '确认',
      delete: '删除'
    },
    functionUnit: {
      title: '功能单元',
      create: '创建功能单元',
      edit: '编辑功能单元'
    }
  },
  'en-US': {
    common: {
      save: 'Save',
      cancel: 'Cancel', 
      confirm: 'Confirm',
      delete: 'Delete'
    },
    functionUnit: {
      title: 'Function Unit',
      create: 'Create Function Unit',
      edit: 'Edit Function Unit'
    }
  }
}

const i18n = createI18n({
  locale: 'zh-CN',
  fallbackLocale: 'en-US',
  messages
})
```

### 7.2 时区和格式化
#### 7.2.1 时区处理
- **服务器时区**：统一使用UTC时间存储
- **客户端显示**：根据用户时区自动转换显示
- **时区配置**：用户可以设置个人时区偏好

#### 7.2.2 数据格式化
```java
// 后端国际化配置
@Configuration
public class InternationalizationConfig {
    
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600);
        return messageSource;
    }
    
    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        return resolver;
    }
}

// 日期时间格式化
@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
private LocalDateTime createdAt;
```