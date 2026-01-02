# 技术需求

## 1. 技术架构概览

### 1.1 整体架构
```
┌─────────────────────────────────────────────────────────────┐
│                    前端层 (Frontend Layer)                    │
├─────────────────────────────────────────────────────────────┤
│  开发工作站        │   管理中心        │   用户门户          │
│  Vue 3 + TS       │   Vue 3 + TS     │   Vue 3 + TS       │
│  bpmn.js          │   Element Plus   │   Element Plus      │
│  form-create      │   ECharts        │   ECharts           │
│  Handsontable     │                  │                     │
└─────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │   API Gateway     │
                    │   (Nginx/Kong)    │
                    └─────────┬─────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    后端层 (Backend Layer)                     │
├─────────────────────────────────────────────────────────────┤
│           Spring Boot 3.x + Java 17                        │
│  ┌─────────────┬─────────────┬─────────────┬─────────────┐  │
│  │ 开发工作站   │  管理中心    │  用户门户    │  工作流引擎  │  │
│  │   服务      │    服务     │    服务     │   (Flowable) │  │
│  └─────────────┴─────────────┴─────────────┴─────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    数据层 (Data Layer)                       │
├─────────────────────────────────────────────────────────────┤
│  PostgreSQL 14+  │   Redis 6+      │   MinIO             │
│  (主数据库)       │   (缓存/会话)    │   (文件存储)         │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 微服务架构
- **服务拆分原则**：按业务域拆分，每个模块独立部署
- **服务通信**：RESTful API + 消息队列（RabbitMQ）
- **服务发现**：Consul / Eureka
- **配置管理**：Spring Cloud Config
- **熔断降级**：Hystrix / Resilience4j

## 2. 前端技术栈

### 2.1 核心框架
#### Vue 3 生态系统
```json
{
  "vue": "^3.4.0",
  "vue-router": "^4.2.0",
  "pinia": "^2.1.0",
  "typescript": "^5.0.0",
  "vite": "^5.0.0"
}
```

#### UI组件库
- **Element Plus**：主要UI组件库
- **Ant Design Vue**：备选方案
- **自定义组件**：业务特定组件封装

### 2.2 专业组件

#### 2.2.1 流程设计器 (bpmn.js)
```javascript
// 核心依赖
{
  "bpmn-js": "^17.0.0",
  "bpmn-js-properties-panel": "^5.0.0",
  "camunda-bpmn-moddle": "^7.0.0"
}

// 自定义扩展
- 中文化支持
- 自定义属性面板
- 节点表单绑定
- 流程验证规则
```

#### 2.2.2 表单引擎 (form-create)
```javascript
// 核心配置
{
  "@form-create/element-ui": "^3.1.0",
  "@form-create/designer": "^3.1.0"
}

// 扩展字段类型
- 文件上传组件
- 富文本编辑器
- 数据字典选择器
- 关联数据选择器
- 自定义业务组件
```

#### 2.2.3 表格设计器 (Handsontable)
```javascript
// 商业版本
{
  "handsontable": "^14.0.0",
  "@handsontable/vue3": "^14.0.0"
}

// 功能配置
- 数据类型验证
- 列宽自适应
- 右键菜单
- 数据导入导出
- 撤销重做
```

### 2.3 开发工具链

#### 2.3.1 构建工具
```javascript
// Vite 配置
{
  "build": {
    "target": "es2020",
    "outDir": "dist",
    "sourcemap": true,
    "rollupOptions": {
      "output": {
        "manualChunks": {
          "vendor": ["vue", "vue-router", "pinia"],
          "bpmn": ["bpmn-js"],
          "form": ["@form-create/element-ui"],
          "table": ["handsontable"]
        }
      }
    }
  }
}
```

#### 2.3.2 代码质量
```javascript
// ESLint + Prettier
{
  "@typescript-eslint/eslint-plugin": "^6.0.0",
  "@typescript-eslint/parser": "^6.0.0",
  "eslint-plugin-vue": "^9.0.0",
  "prettier": "^3.0.0"
}

// 提交规范
{
  "husky": "^8.0.0",
  "lint-staged": "^15.0.0",
  "@commitlint/cli": "^18.0.0"
}
```

### 2.4 状态管理

#### 2.4.1 Pinia Store 结构
```typescript
// 用户状态
interface UserState {
  userInfo: UserInfo | null
  permissions: string[]
  roles: string[]
  token: string | null
}

// 应用状态
interface AppState {
  theme: 'light' | 'dark'
  locale: 'zh-CN' | 'en-US'
  sidebarCollapsed: boolean
  breadcrumbs: BreadcrumbItem[]
}

// 功能单元状态
interface FunctionUnitState {
  currentUnit: FunctionUnit | null
  units: FunctionUnit[]
  activeTab: 'workflow' | 'table' | 'form' | 'action'
}
```

## 3. 后端技术栈

### 3.1 核心框架

#### 3.1.1 Spring Boot 生态
```xml
<!-- 核心依赖 -->
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>3.2.0</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
        <version>3.2.0</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
        <version>3.2.0</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
        <version>3.2.0</version>
    </dependency>
</dependencies>
```

#### 3.1.2 工作流引擎 (Flowable)
```xml
<!-- Flowable 依赖 -->
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>7.0.0</version>
</dependency>
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-json-converter</artifactId>
    <version>7.0.0</version>
</dependency>
```

### 3.2 数据访问层

#### 3.2.1 JPA/Hibernate 配置
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
      naming:
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        show_sql: false
    open-in-view: false
```

#### 3.2.2 数据库连接池
```yaml
spring:
  datasource:
    type: com.zaxxer.hikari.HikariDataSource
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      auto-commit: true
      idle-timeout: 30000
      pool-name: DatebookHikariCP
      max-lifetime: 1800000
      connection-timeout: 30000
```

### 3.3 安全框架

#### 3.3.1 Spring Security 配置
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

#### 3.3.2 JWT 认证
```java
// JWT 配置
@Component
public class JwtTokenProvider {
    private final String secretKey = "mySecretKey";
    private final long validityInMilliseconds = 3600000; // 1h
    
    public String createToken(String username, List<String> roles) {
        Claims claims = Jwts.claims().setSubject(username);
        claims.put("roles", roles);
        
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);
        
        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(validity)
            .signWith(SignatureAlgorithm.HS256, secretKey)
            .compact();
    }
}
```

### 3.4 API 设计

#### 3.4.1 RESTful API 规范
```java
// 统一响应格式
@Data
@Builder
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;
    
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .code(200)
            .message("Success")
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}

// 控制器示例
@RestController
@RequestMapping("/api/v1/function-units")
@Validated
public class FunctionUnitController {
    
    @GetMapping
    public ApiResponse<Page<FunctionUnitDTO>> getFunctionUnits(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String keyword) {
        // 实现逻辑
    }
    
    @PostMapping
    public ApiResponse<FunctionUnitDTO> createFunctionUnit(
        @Valid @RequestBody CreateFunctionUnitRequest request) {
        // 实现逻辑
    }
}
```

#### 3.4.2 API 文档 (OpenAPI 3.0)
```yaml
openapi: 3.0.3
info:
  title: 低代码工作流平台 API
  version: 1.0.0
  description: 企业级低代码工作流开发和运行平台

servers:
  - url: https://api.workflow.hsbc.com/v1
    description: 生产环境
  - url: https://api-test.workflow.hsbc.com/v1
    description: 测试环境

paths:
  /function-units:
    get:
      summary: 获取功能单元列表
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/FunctionUnitPageResponse'
```

## 4. 数据库设计

### 4.1 PostgreSQL 配置

#### 4.1.1 数据库参数优化
```sql
-- postgresql.conf 关键参数
shared_buffers = 256MB
effective_cache_size = 1GB
maintenance_work_mem = 64MB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
random_page_cost = 1.1
effective_io_concurrency = 200
```

#### 4.1.2 连接池配置
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workflow_platform
    username: ${DB_USERNAME:workflow_user}
    password: ${DB_PASSWORD:workflow_pass}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
```

### 4.2 数据库架构

#### 4.2.1 核心表结构
```sql
-- 功能单元表
CREATE TABLE function_units (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'DEPRECATED')),
    description TEXT,
    icon_url VARCHAR(500),
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_function_unit_name_version UNIQUE (name, version)
);

-- 工作流定义表
CREATE TABLE workflow_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    function_unit_id UUID NOT NULL REFERENCES function_units(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    bpmn_xml TEXT NOT NULL,
    deployment_id VARCHAR(64),
    process_definition_id VARCHAR(64),
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_workflow_function_unit UNIQUE (function_unit_id)
);

-- 数据表定义
CREATE TABLE table_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    function_unit_id UUID NOT NULL REFERENCES function_units(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    table_type VARCHAR(20) NOT NULL CHECK (table_type IN ('MAIN', 'SUB', 'ACTION')),
    parent_table_id UUID REFERENCES table_definitions(id),
    ddl_script TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

#### 4.2.2 索引策略
```sql
-- 性能优化索引
CREATE INDEX idx_function_units_status ON function_units(status);
CREATE INDEX idx_function_units_created_by ON function_units(created_by);
CREATE INDEX idx_function_units_name_version ON function_units(name, version);

CREATE INDEX idx_workflow_definitions_function_unit ON workflow_definitions(function_unit_id);
CREATE INDEX idx_table_definitions_function_unit ON table_definitions(function_unit_id);
CREATE INDEX idx_table_definitions_parent ON table_definitions(parent_table_id);

-- 全文搜索索引
CREATE INDEX idx_function_units_search ON function_units 
USING gin(to_tsvector('english', name || ' ' || display_name || ' ' || COALESCE(description, '')));
```

### 4.3 数据迁移策略

#### 4.3.1 Flyway 版本控制
```sql
-- V1.0.0__Initial_schema.sql
-- 初始化数据库结构

-- V1.0.1__Add_function_unit_tags.sql
ALTER TABLE function_units ADD COLUMN tags TEXT[];
CREATE INDEX idx_function_units_tags ON function_units USING gin(tags);

-- V1.0.2__Add_workflow_variables.sql
CREATE TABLE workflow_variables (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_definition_id UUID NOT NULL REFERENCES workflow_definitions(id),
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    default_value TEXT,
    description TEXT
);
```

## 5. 文件存储 (MinIO)

### 5.1 MinIO 配置

#### 5.1.2 存储桶策略
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {"AWS": ["*"]},
      "Action": ["s3:GetObject"],
      "Resource": ["arn:aws:s3:::workflow-public/*"]
    },
    {
      "Effect": "Allow",
      "Principal": {"AWS": ["arn:aws:iam::workflow:user/app-user"]},
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
      "Resource": ["arn:aws:s3:::workflow-private/*"]
    }
  ]
}
```

### 5.2 文件管理服务

#### 5.2.1 文件上传接口
```java
@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    
    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> uploadFile(
        @RequestParam("file") MultipartFile file,
        @RequestParam("bucket") String bucket,
        @RequestParam(value = "path", required = false) String path) {
        
        // 文件验证
        validateFile(file);
        
        // 生成文件名
        String fileName = generateFileName(file.getOriginalFilename());
        String objectName = StringUtils.hasText(path) ? path + "/" + fileName : fileName;
        
        // 上传到MinIO
        String url = minioService.uploadFile(bucket, objectName, file);
        
        return ApiResponse.success(FileUploadResponse.builder()
            .fileName(fileName)
            .originalName(file.getOriginalFilename())
            .url(url)
            .size(file.getSize())
            .contentType(file.getContentType())
            .build());
    }
}
```

## 6. 缓存策略 (Redis)

### 6.1 Redis 配置

#### 6.1.1 连接配置
```yaml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    database: 0
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
        max-wait: 2000ms
```

#### 6.1.2 缓存策略
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .transactionAware()
            .build();
    }
}

// 缓存使用示例
@Service
public class FunctionUnitService {
    
    @Cacheable(value = "function-units", key = "#id")
    public FunctionUnitDTO getFunctionUnit(UUID id) {
        // 查询逻辑
    }
    
    @CacheEvict(value = "function-units", key = "#id")
    public void updateFunctionUnit(UUID id, UpdateFunctionUnitRequest request) {
        // 更新逻辑
    }
}
```

## 7. 部署架构

### 7.1 Docker 容器化

#### 7.1.1 Dockerfile 示例
```dockerfile
# 前端构建
FROM node:18-alpine AS frontend-builder
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci --only=production
COPY frontend/ .
RUN npm run build

# 后端构建
FROM openjdk:17-jdk-slim AS backend-builder
WORKDIR /app
COPY backend/pom.xml .
COPY backend/src ./src
RUN ./mvnw clean package -DskipTests

# 运行时镜像
FROM openjdk:17-jre-slim
WORKDIR /app
COPY --from=backend-builder /app/target/*.jar app.jar
COPY --from=frontend-builder /app/dist ./static
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 7.1.2 Docker Compose 配置
```yaml
version: '3.8'

services:
  # 数据库
  postgres:
    image: postgres:14-alpine
    environment:
      POSTGRES_DB: workflow_platform
      POSTGRES_USER: workflow_user
      POSTGRES_PASSWORD: workflow_pass
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  # 缓存
  redis:
    image: redis:6-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  # 文件存储
  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data

  # 应用服务
  app:
    build: .
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_HOST: postgres
      REDIS_HOST: redis
      MINIO_ENDPOINT: http://minio:9000
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - redis
      - minio

volumes:
  postgres_data:
  redis_data:
  minio_data:
```

### 7.2 Kubernetes 部署

#### 7.2.1 应用部署配置
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: workflow-platform
  namespace: workflow
spec:
  replicas: 3
  selector:
    matchLabels:
      app: workflow-platform
  template:
    metadata:
      labels:
        app: workflow-platform
    spec:
      containers:
      - name: app
        image: workflow-platform:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: DB_HOST
          value: "postgres-service"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
```

## 8. 监控和日志

### 8.1 应用监控

#### 8.1.1 Spring Boot Actuator
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

#### 8.1.2 Prometheus 配置
```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'workflow-platform'
    static_configs:
      - targets: ['app:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s
```

### 8.2 日志管理

#### 8.2.1 Logback 配置
```xml
<configuration>
    <springProfile name="!prod">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
    
    <springProfile name="prod">
        <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>logs/application.log</file>
            <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                <fileNamePattern>logs/application.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
                <maxFileSize>100MB</maxFileSize>
                <maxHistory>30</maxHistory>
            </rollingPolicy>
            <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
                <providers>
                    <timestamp/>
                    <logLevel/>
                    <loggerName/>
                    <message/>
                    <mdc/>
                    <stackTrace/>
                </providers>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="FILE"/>
        </root>
    </springProfile>
</configuration>
```

## 9. 扩展性设计

### 9.1 插件架构

#### 9.1.1 插件接口定义
```java
public interface WorkflowPlugin {
    String getName();
    String getVersion();
    void initialize(PluginContext context);
    void destroy();
}

public interface FormFieldPlugin extends WorkflowPlugin {
    String getFieldType();
    FormFieldRenderer getRenderer();
    FormFieldValidator getValidator();
}

public interface ActionPlugin extends WorkflowPlugin {
    String getActionType();
    ActionExecutor getExecutor();
    ActionConfigurer getConfigurer();
}
```

#### 9.1.2 插件加载机制
```java
@Component
public class PluginManager {
    private final Map<String, WorkflowPlugin> plugins = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void loadPlugins() {
        // 扫描插件目录
        Path pluginDir = Paths.get("plugins");
        if (Files.exists(pluginDir)) {
            try (Stream<Path> paths = Files.walk(pluginDir)) {
                paths.filter(path -> path.toString().endsWith(".jar"))
                     .forEach(this::loadPlugin);
            }
        }
    }
    
    private void loadPlugin(Path jarPath) {
        try {
            URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                this.getClass().getClassLoader()
            );
            
            // 加载插件类
            ServiceLoader<WorkflowPlugin> loader = 
                ServiceLoader.load(WorkflowPlugin.class, classLoader);
            
            for (WorkflowPlugin plugin : loader) {
                plugin.initialize(createPluginContext());
                plugins.put(plugin.getName(), plugin);
            }
        } catch (Exception e) {
            log.error("Failed to load plugin: {}", jarPath, e);
        }
    }
}
```

### 9.2 API 扩展

#### 9.2.1 自定义端点
```java
@RestController
@RequestMapping("/api/v1/extensions")
public class ExtensionController {
    
    @Autowired
    private PluginManager pluginManager;
    
    @PostMapping("/{pluginName}/execute")
    public ApiResponse<Object> executePlugin(
        @PathVariable String pluginName,
        @RequestBody Map<String, Object> parameters) {
        
        WorkflowPlugin plugin = pluginManager.getPlugin(pluginName);
        if (plugin instanceof ExecutablePlugin) {
            Object result = ((ExecutablePlugin) plugin).execute(parameters);
            return ApiResponse.success(result);
        }
        
        throw new PluginNotFoundException("Plugin not found or not executable: " + pluginName);
    }
}
```

## 10. 性能优化

### 10.1 数据库优化

#### 10.1.1 查询优化
```java
// 使用 JPA Specification 进行动态查询
@Repository
public class FunctionUnitRepository extends JpaRepository<FunctionUnit, UUID> {
    
    @Query(value = """
        SELECT fu.* FROM function_units fu
        WHERE (:keyword IS NULL OR 
               fu.name ILIKE %:keyword% OR 
               fu.display_name ILIKE %:keyword% OR
               fu.description ILIKE %:keyword%)
        AND (:status IS NULL OR fu.status = :status)
        ORDER BY fu.updated_at DESC
        """, nativeQuery = true)
    Page<FunctionUnit> findByKeywordAndStatus(
        @Param("keyword") String keyword,
        @Param("status") String status,
        Pageable pageable);
}
```

#### 10.1.2 连接池监控
```java
@Component
public class DatabaseMetrics {
    
    @EventListener
    public void handleDataSourceMetrics(DataSourcePoolMetrics metrics) {
        Gauge.builder("hikari.connections.active")
             .register(Metrics.globalRegistry, metrics::getActive);
        
        Gauge.builder("hikari.connections.idle")
             .register(Metrics.globalRegistry, metrics::getIdle);
        
        Gauge.builder("hikari.connections.pending")
             .register(Metrics.globalRegistry, metrics::getPending);
    }
}
```

### 10.2 缓存优化

#### 10.2.1 多级缓存
```java
@Service
public class CachedFunctionUnitService {
    
    // L1 缓存：本地缓存
    @Cacheable(value = "function-units-local", key = "#id")
    public FunctionUnitDTO getFunctionUnitLocal(UUID id) {
        return getFunctionUnitFromRedis(id);
    }
    
    // L2 缓存：Redis 缓存
    @Cacheable(value = "function-units-redis", key = "#id")
    public FunctionUnitDTO getFunctionUnitFromRedis(UUID id) {
        return getFunctionUnitFromDatabase(id);
    }
    
    // L3 缓存：数据库
    public FunctionUnitDTO getFunctionUnitFromDatabase(UUID id) {
        return functionUnitRepository.findById(id)
            .map(functionUnitMapper::toDTO)
            .orElseThrow(() -> new EntityNotFoundException("FunctionUnit not found: " + id));
    }
}
```

### 10.3 异步处理

#### 10.3.1 异步任务配置
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

@Service
public class AsyncWorkflowService {
    
    @Async("taskExecutor")
    public CompletableFuture<Void> deployFunctionUnitAsync(UUID functionUnitId) {
        try {
            // 异步部署逻辑
            deployFunctionUnit(functionUnitId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
```