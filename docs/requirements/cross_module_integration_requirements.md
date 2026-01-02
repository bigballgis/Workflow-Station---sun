# 跨模块集成需求

## 1. 概述

本文档定义了低代码工作流平台各模块间的集成需求，包括基于Apache Kafka的事件通知机制和完整的数据迁移策略。

### 1.1 集成目标
- **异步通信**：模块间通过事件驱动的异步通信
- **数据一致性**：确保跨模块数据的最终一致性  
- **系统解耦**：降低模块间的直接依赖
- **可扩展性**：支持新模块的快速接入

### 1.2 核心组件
- **事件总线**：基于Apache Kafka的消息中间件
- **数据迁移引擎**：支持版本升级和数据迁移
- **事件处理器**：各模块的事件生产者和消费者
- **监控系统**：事件流和数据迁移的监控

## 2. Kafka事件通知机制

### 2.1 Kafka集群配置

#### 2.1.1 基础架构
```yaml
kafka_cluster:
  brokers: 3
  replication_factor: 3
  min_insync_replicas: 2
  
  zookeeper:
    nodes: 3
    data_dir: "/var/lib/zookeeper"
    
  kafka_config:
    log_retention_hours: 168  # 7 days
    log_segment_bytes: 1073741824  # 1GB
    num_network_threads: 8
    num_io_threads: 8
```

#### 2.1.2 Topic配置
```yaml
topics:
  function-unit-events:
    partitions: 6
    replication_factor: 3
    retention_ms: 604800000  # 7 days
    cleanup_policy: "delete"
    
  user-permission-events:
    partitions: 3
    replication_factor: 3
    retention_ms: 2592000000  # 30 days
    cleanup_policy: "delete"
    
  workflow-execution-events:
    partitions: 12
    replication_factor: 3
    retention_ms: 259200000  # 3 days
    cleanup_policy: "delete"
    
  system-notification-events:
    partitions: 3
    replication_factor: 3
    retention_ms: 86400000  # 1 day
    cleanup_policy: "delete"
```

### 2.2 事件消息格式

#### 2.2.1 标准事件结构
```json
{
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "event_type": "FUNCTION_UNIT_DEPLOYED", 
  "event_version": "1.0",
  "timestamp": "2026-01-02T10:30:00.000Z",
  "source_service": "admin-center",
  "correlation_id": "550e8400-e29b-41d4-a716-446655440001",
  "payload": {
    "function_unit_id": "550e8400-e29b-41d4-a716-446655440002",
    "version": "1.2.0",
    "deployment_environment": "production",
    "deployment_config": {}
  },
  "metadata": {
    "user_id": "550e8400-e29b-41d4-a716-446655440003",
    "session_id": "550e8400-e29b-41d4-a716-446655440004",
    "trace_id": "550e8400-e29b-41d4-a716-446655440005"
  }
}
```

### 2.3 事件类型定义

#### 2.3.1 功能单元相关事件
```yaml
function_unit_events:
  FUNCTION_UNIT_CREATED:
    source: developer-workstation
    consumers: [admin-center, monitoring-service]
    
  FUNCTION_UNIT_PUBLISHED:
    source: developer-workstation  
    consumers: [admin-center, notification-service]
    
  FUNCTION_UNIT_DEPLOYED:
    source: admin-center
    consumers: [user-portal, monitoring-service, audit-service]
    
  FUNCTION_UNIT_UNDEPLOYED:
    source: admin-center
    consumers: [user-portal, monitoring-service]
```

#### 2.3.2 用户权限相关事件
```yaml
user_permission_events:
  USER_CREATED:
    source: admin-center
    consumers: [user-portal, audit-service]
    
  USER_PERMISSION_GRANTED:
    source: admin-center
    consumers: [user-portal, audit-service]
    
  USER_PERMISSION_REVOKED:
    source: admin-center
    consumers: [user-portal, audit-service]
    
  ROLE_ASSIGNED:
    source: admin-center
    consumers: [user-portal, audit-service]
```

### 2.4 事件处理器实现

#### 2.4.1 Spring Boot Kafka配置
```java
@Configuration
@EnableKafka
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "workflow-platform");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
}
```

## 3. 数据迁移策略

### 3.1 迁移引擎架构

#### 3.1.1 核心组件
```yaml
migration_engine:
  version_detector:
    description: "检测当前版本和目标版本"
    implementation: "VersionDetectorService"
    
  migration_planner:
    description: "生成迁移计划和脚本"
    implementation: "MigrationPlannerService"
    
  migration_executor:
    description: "执行迁移操作"
    implementation: "MigrationExecutorService"
    
  rollback_manager:
    description: "管理回滚操作"
    implementation: "RollbackManagerService"
    
  validation_service:
    description: "验证迁移结果"
    implementation: "MigrationValidationService"
```

### 3.2 版本兼容性管理

#### 3.2.1 版本兼容性矩阵
```yaml
compatibility_matrix:
  version_1_0_to_1_1:
    compatibility: FULL
    migration_required: false
    description: "完全兼容，无需迁移"
    
  version_1_1_to_1_2:
    compatibility: BACKWARD
    migration_required: true
    description: "向后兼容，需要数据迁移"
    migration_scripts:
      - schema: "V1_2__add_function_unit_tags.sql"
      - data: "migrate_function_unit_metadata.py"
      
  version_1_2_to_2_0:
    compatibility: BREAKING
    migration_required: true
    description: "破坏性变更，需要完整迁移"
    migration_scripts:
      - schema: "V2_0__restructure_workflow_definitions.sql"
      - data: "migrate_workflow_definitions.py"
      - config: "migrate_form_configurations.py"
    rollback_scripts:
      - "rollback_to_v1_2.sql"
```

### 3.3 迁移脚本示例

#### 3.3.1 Schema迁移脚本
```sql
-- V1_2__add_function_unit_tags.sql
-- 为功能单元添加标签支持

BEGIN;

-- 1. 添加标签字段
ALTER TABLE function_units 
ADD COLUMN IF NOT EXISTS tags TEXT[] DEFAULT '{}';

-- 2. 创建标签索引  
CREATE INDEX IF NOT EXISTS idx_function_units_tags 
ON function_units USING gin(tags);

-- 3. 创建标签管理表
CREATE TABLE IF NOT EXISTS function_unit_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE,
    color VARCHAR(7) DEFAULT '#1890ff',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. 插入默认标签
INSERT INTO function_unit_tags (name, color, description) 
VALUES 
    ('审批流程', '#52c41a', '审批相关的业务流程'),
    ('数据收集', '#1890ff', '数据收集和录入流程'),
    ('通知提醒', '#faad14', '通知和提醒相关流程')
ON CONFLICT (name) DO NOTHING;

-- 5. 更新版本信息
INSERT INTO schema_version (version, description, applied_at)
VALUES ('1.2.0', 'Add function unit tags support', CURRENT_TIMESTAMP);

COMMIT;
```

#### 3.3.2 数据迁移脚本
```python
# migrate_function_unit_metadata.py
import json
import logging
from typing import Dict, List
from dataclasses import dataclass

@dataclass
class MigrationResult:
    success: bool
    migrated_count: int
    failed_count: int
    errors: List[str]

class FunctionUnitMetadataMigrator:
    
    def __init__(self, db_connection):
        self.conn = db_connection
        self.logger = logging.getLogger(__name__)
    
    def migrate_metadata(self) -> MigrationResult:
        """迁移功能单元元数据"""
        
        cursor = self.conn.cursor()
        result = MigrationResult(True, 0, 0, [])
        
        try:
            # 获取需要迁移的功能单元
            cursor.execute("""
                SELECT id, name, description, metadata 
                FROM function_units 
                WHERE metadata IS NOT NULL 
                AND (metadata->>'migrated_to_v1_2')::boolean IS NOT TRUE
            """)
            
            function_units = cursor.fetchall()
            
            for unit_id, name, description, metadata_json in function_units:
                try:
                    self.migrate_single_unit(cursor, unit_id, name, metadata_json)
                    result.migrated_count += 1
                    
                except Exception as e:
                    error_msg = f"Failed to migrate unit {unit_id}: {str(e)}"
                    self.logger.error(error_msg)
                    result.errors.append(error_msg)
                    result.failed_count += 1
            
            self.conn.commit()
            
        except Exception as e:
            self.conn.rollback()
            result.success = False
            result.errors.append(f"Migration failed: {str(e)}")
            
        finally:
            cursor.close()
            
        return result
    
    def migrate_single_unit(self, cursor, unit_id: str, name: str, metadata_json: str):
        """迁移单个功能单元的元数据"""
        
        metadata = json.loads(metadata_json) if metadata_json else {}
        
        # 根据名称推断标签
        tags = self.infer_tags_from_name(name)
        
        # 更新标签
        cursor.execute("""
            UPDATE function_units 
            SET tags = %s,
                metadata = jsonb_set(
                    COALESCE(metadata, '{}'), 
                    '{migrated_to_v1_2}', 
                    'true'
                )
            WHERE id = %s
        """, (tags, unit_id))
        
        self.logger.info(f"Migrated unit {unit_id} with tags: {tags}")
    
    def infer_tags_from_name(self, name: str) -> List[str]:
        """根据功能单元名称推断标签"""
        
        tags = []
        name_lower = name.lower()
        
        if any(keyword in name_lower for keyword in ['审批', 'approval', '批准']):
            tags.append('审批流程')
            
        if any(keyword in name_lower for keyword in ['收集', 'collection', '录入', 'input']):
            tags.append('数据收集')
            
        if any(keyword in name_lower for keyword in ['通知', 'notification', '提醒', 'reminder']):
            tags.append('通知提醒')
            
        return tags if tags else ['其他']
```

### 3.4 迁移执行引擎

#### 3.4.1 迁移执行器
```java
@Service
@Transactional
public class MigrationExecutorService {
    
    @Autowired
    private BackupService backupService;
    
    @Autowired
    private MigrationValidationService validationService;
    
    public MigrationResult executeMigration(MigrationPlan plan) {
        
        MigrationResult result = new MigrationResult();
        String backupId = null;
        
        try {
            // 1. 执行迁移前检查
            PreMigrationCheckResult checkResult = performPreMigrationChecks(plan);
            if (!checkResult.isSuccess()) {
                throw new MigrationException("Pre-migration checks failed: " + 
                    String.join(", ", checkResult.getFailureReasons()));
            }
            
            // 2. 创建备份
            backupId = backupService.createFullBackup("pre-migration-" + plan.getTargetVersion());
            result.setBackupId(backupId);
            
            // 3. 执行Schema迁移
            executeSchemamigrations(plan.getSchemaMigrations());
            
            // 4. 执行数据迁移
            executeDataMigrations(plan.getDataMigrations());
            
            // 5. 执行配置迁移
            executeConfigurationMigrations(plan.getConfigurationMigrations());
            
            // 6. 验证迁移结果
            ValidationResult validationResult = validationService.validateMigration(plan);
            if (!validationResult.isSuccess()) {
                throw new MigrationException("Migration validation failed: " + 
                    String.join(", ", validationResult.getErrors()));
            }
            
            // 7. 更新版本信息
            updateSystemVersion(plan.getTargetVersion());
            
            // 8. 发布迁移完成事件
            publishMigrationCompletedEvent(plan, result);
            
            result.setStatus(MigrationStatus.SUCCESS);
            log.info("Migration completed successfully: {} -> {}", 
                plan.getSourceVersion(), plan.getTargetVersion());
            
        } catch (Exception e) {
            log.error("Migration failed: {} -> {}", 
                plan.getSourceVersion(), plan.getTargetVersion(), e);
            
            // 执行回滚
            if (backupId != null) {
                rollbackMigration(plan, backupId);
            }
            
            result.setStatus(MigrationStatus.FAILED);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }
    
    private void executeSchemamigrations(List<SchemaMigration> migrations) {
        for (SchemaMigration migration : migrations) {
            try {
                log.info("Executing schema migration: {}", migration.getName());
                jdbcTemplate.execute(migration.getSql());
                
                // 记录迁移步骤
                recordMigrationStep(migration.getName(), "SCHEMA", MigrationStepStatus.SUCCESS);
                
            } catch (Exception e) {
                recordMigrationStep(migration.getName(), "SCHEMA", MigrationStepStatus.FAILED);
                throw new MigrationException("Schema migration failed: " + migration.getName(), e);
            }
        }
    }
}
```

## 4. 监控和运维

### 4.1 Kafka监控

#### 4.1.1 关键监控指标
```yaml
kafka_monitoring_metrics:
  broker_metrics:
    - kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec
    - kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec
    - kafka.server:type=BrokerTopicMetrics,name=BytesOutPerSec
    
  consumer_metrics:
    - kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*
    - kafka.consumer:type=consumer-coordinator-metrics,client-id=*
    
  producer_metrics:
    - kafka.producer:type=producer-metrics,client-id=*
    - kafka.producer:type=producer-topic-metrics,client-id=*
```

#### 4.1.2 告警配置
```yaml
kafka_alerts:
  broker_down:
    condition: "kafka_brokers_available < kafka_brokers_total"
    severity: "critical"
    
  high_consumer_lag:
    condition: "kafka_consumer_lag_sum > 10000"
    severity: "warning"
    
  low_disk_space:
    condition: "kafka_log_size_bytes / kafka_log_size_limit > 0.8"
    severity: "warning"
```

### 4.2 迁移监控

#### 4.2.1 迁移状态跟踪
```sql
-- 迁移历史表
CREATE TABLE migration_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    migration_name VARCHAR(200) NOT NULL,
    from_version VARCHAR(50) NOT NULL,
    to_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'ROLLED_BACK')),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    backup_id VARCHAR(100),
    error_message TEXT,
    executed_by UUID NOT NULL,
    
    CONSTRAINT fk_migration_executor FOREIGN KEY (executed_by) REFERENCES users(id)
);

-- 迁移步骤表  
CREATE TABLE migration_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    migration_id UUID NOT NULL REFERENCES migration_history(id),
    step_name VARCHAR(200) NOT NULL,
    step_type VARCHAR(50) NOT NULL CHECK (step_type IN ('SCHEMA', 'DATA', 'CONFIG')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED')),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    error_message TEXT,
    
    INDEX idx_migration_steps_migration_id (migration_id),
    INDEX idx_migration_steps_status (status)
);
```

## 5. 部署和配置

### 5.1 Kafka集群部署

#### 5.1.1 Docker Compose配置
```yaml
version: '3.8'

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - zookeeper-data:/var/lib/zookeeper/data
      
  kafka-1:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
    volumes:
      - kafka-1-data:/var/lib/kafka/data
      
  kafka-2:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 2
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-2:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      
  kafka-3:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 3
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-3:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3

volumes:
  zookeeper-data:
  kafka-1-data:
  kafka-2-data:
  kafka-3-data:
```

### 5.2 应用配置

#### 5.2.1 Spring Boot配置
```yaml
spring:
  kafka:
    bootstrap-servers: kafka-1:9092,kafka-2:9092,kafka-3:9092
    producer:
      acks: all
      retries: 3
      batch-size: 16384
      linger-ms: 5
      buffer-memory: 33554432
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      
    consumer:
      group-id: workflow-platform
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.hsbc.workflow.events"

# 迁移配置
migration:
  enabled: true
  backup:
    enabled: true
    retention-days: 30
    storage-path: "/var/backups/workflow"
  validation:
    enabled: true
    timeout-seconds: 300
```

---

**文档版本**：v1.0  
**创建日期**：2026年1月2日