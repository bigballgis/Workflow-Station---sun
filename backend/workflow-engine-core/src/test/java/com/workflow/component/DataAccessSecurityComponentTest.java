package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.workflow.component.DataAccessSecurityComponent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DataAccessSecurityComponent 单元测试
 * 
 * 测试行级权限、列级权限、数据脱敏和安全事件监控功能
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("数据访问安全组件测试")
class DataAccessSecurityComponentTest {

    @Mock(lenient = true)
    private StringRedisTemplate stringRedisTemplate;
    
    @Mock(lenient = true)
    private ValueOperations<String, String> valueOperations;
    
    @Mock(lenient = true)
    private HashOperations<String, Object, Object> hashOperations;
    
    @Mock(lenient = true)
    private AuditManagerComponent auditManagerComponent;
    
    @Mock(lenient = true)
    private SecurityManagerComponent securityManagerComponent;
    
    private ObjectMapper objectMapper;
    private DataAccessSecurityComponent dataAccessSecurity;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        
        dataAccessSecurity = new DataAccessSecurityComponent(
                stringRedisTemplate, objectMapper, auditManagerComponent, securityManagerComponent);
    }

    // ==================== 行级权限测试 ====================

    @Nested
    @DisplayName("行级权限控制测试")
    class RowLevelPermissionTests {

        @Test
        @DisplayName("定义行级权限策略成功")
        void defineRowLevelPolicy_Success() {
            // Given
            DataAccessSecurityComponent.RowLevelPolicy policy = new DataAccessSecurityComponent.RowLevelPolicy();
            policy.setPolicyId("policy-001");
            policy.setTableName("orders");
            policy.setConditionExpression("owner_id = ${username}");
            policy.setAllowedRoles(Set.of("ADMIN"));
            policy.setAllowedUsers(Set.of("superuser"));
            policy.setDescription("订单表行级权限");
            policy.setEnabled(true);

            // When
            dataAccessSecurity.defineRowLevelPolicy(policy);

            // Then
            verify(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
        }

        @Test
        @DisplayName("无策略时默认允许访问")
        void checkRowAccess_NoPolicyAllowsAccess() {
            // Given
            String username = "user1";
            String tableName = "products";
            Map<String, Object> rowData = Map.of("id", 1, "name", "Product A");
            when(securityManagerComponent.getUserRoles(username)).thenReturn(Set.of("USER"));

            // When
            DataAccessSecurityComponent.RowAccessResult result = 
                    dataAccessSecurity.checkRowAccess(username, tableName, rowData);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("允许列表中的用户可以访问")
        void checkRowAccess_AllowedUserCanAccess() {
            // Given
            DataAccessSecurityComponent.RowLevelPolicy policy = new DataAccessSecurityComponent.RowLevelPolicy();
            policy.setPolicyId("policy-001");
            policy.setTableName("orders");
            policy.setConditionExpression("owner_id = ${username}");
            policy.setAllowedUsers(Set.of("admin"));
            policy.setEnabled(true);
            dataAccessSecurity.defineRowLevelPolicy(policy);

            when(securityManagerComponent.getUserRoles("admin")).thenReturn(Set.of("USER"));

            // When
            DataAccessSecurityComponent.RowAccessResult result = 
                    dataAccessSecurity.checkRowAccess("admin", "orders", Map.of("owner_id", "other"));

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("允许角色中的用户可以访问")
        void checkRowAccess_AllowedRoleCanAccess() {
            // Given
            DataAccessSecurityComponent.RowLevelPolicy policy = new DataAccessSecurityComponent.RowLevelPolicy();
            policy.setPolicyId("policy-001");
            policy.setTableName("orders");
            policy.setConditionExpression("owner_id = ${username}");
            policy.setAllowedRoles(Set.of("ADMIN"));
            policy.setEnabled(true);
            dataAccessSecurity.defineRowLevelPolicy(policy);

            when(securityManagerComponent.getUserRoles("user1")).thenReturn(Set.of("ADMIN"));

            // When
            DataAccessSecurityComponent.RowAccessResult result = 
                    dataAccessSecurity.checkRowAccess("user1", "orders", Map.of("owner_id", "other"));

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("条件满足时允许访问")
        void checkRowAccess_ConditionMetAllowsAccess() {
            // Given
            DataAccessSecurityComponent.RowLevelPolicy policy = new DataAccessSecurityComponent.RowLevelPolicy();
            policy.setPolicyId("policy-001");
            policy.setTableName("orders");
            policy.setConditionExpression("owner_id = ${username}");
            policy.setEnabled(true);
            dataAccessSecurity.defineRowLevelPolicy(policy);

            when(securityManagerComponent.getUserRoles("user1")).thenReturn(Set.of("USER"));

            // When
            DataAccessSecurityComponent.RowAccessResult result = 
                    dataAccessSecurity.checkRowAccess("user1", "orders", Map.of("owner_id", "user1"));

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("条件不满足时拒绝访问")
        void checkRowAccess_ConditionNotMetDeniesAccess() {
            // Given
            DataAccessSecurityComponent.RowLevelPolicy policy = new DataAccessSecurityComponent.RowLevelPolicy();
            policy.setPolicyId("policy-001");
            policy.setTableName("orders");
            policy.setConditionExpression("owner_id = ${username}");
            policy.setEnabled(true);
            dataAccessSecurity.defineRowLevelPolicy(policy);

            when(securityManagerComponent.getUserRoles("user1")).thenReturn(Set.of("USER"));

            // When
            DataAccessSecurityComponent.RowAccessResult result = 
                    dataAccessSecurity.checkRowAccess("user1", "orders", Map.of("owner_id", "other_user"));

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getPolicyId()).isEqualTo("policy-001");
        }

        @Test
        @DisplayName("生成行级过滤条件 - 无策略返回1=1")
        void generateRowFilterCondition_NoPolicyReturnsNoFilter() {
            // Given
            when(securityManagerComponent.getUserRoles("user1")).thenReturn(Set.of("USER"));

            // When
            String condition = dataAccessSecurity.generateRowFilterCondition("user1", "products");

            // Then
            assertThat(condition).isEqualTo("1=1");
        }

        @Test
        @DisplayName("生成行级过滤条件 - 有策略返回条件")
        void generateRowFilterCondition_WithPolicyReturnsCondition() {
            // Given
            DataAccessSecurityComponent.RowLevelPolicy policy = new DataAccessSecurityComponent.RowLevelPolicy();
            policy.setPolicyId("policy-001");
            policy.setTableName("orders");
            policy.setConditionExpression("owner_id = ${username}");
            policy.setEnabled(true);
            dataAccessSecurity.defineRowLevelPolicy(policy);

            when(securityManagerComponent.getUserRoles("user1")).thenReturn(Set.of("USER"));

            // When
            String condition = dataAccessSecurity.generateRowFilterCondition("user1", "orders");

            // Then
            assertThat(condition).contains("owner_id = 'user1'");
        }
    }

    // ==================== 列级权限测试 ====================

    @Nested
    @DisplayName("列级权限控制测试")
    class ColumnLevelPermissionTests {

        @Test
        @DisplayName("定义列级权限策略成功")
        void defineColumnLevelPolicy_Success() {
            // Given
            DataAccessSecurityComponent.ColumnLevelPolicy policy = new DataAccessSecurityComponent.ColumnLevelPolicy();
            policy.setPolicyId("col-policy-001");
            policy.setTableName("users");
            policy.setHiddenColumns(Set.of("password", "secret_key"));
            policy.setMaskedColumns(Set.of("phone", "email"));
            policy.setAllowedRoles(Set.of("ADMIN"));
            policy.setEnabled(true);

            // When
            dataAccessSecurity.defineColumnLevelPolicy(policy);

            // Then
            verify(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
        }

        @Test
        @DisplayName("无策略时返回所有列")
        void getVisibleColumns_NoPolicyReturnsAllColumns() {
            // Given
            String username = "user1";
            String tableName = "products";
            Set<String> allColumns = Set.of("id", "name", "price", "description");
            when(securityManagerComponent.getUserRoles(username)).thenReturn(Set.of("USER"));

            // When
            Set<String> visibleColumns = dataAccessSecurity.getVisibleColumns(username, tableName, allColumns);

            // Then
            assertThat(visibleColumns).containsExactlyInAnyOrderElementsOf(allColumns);
        }

        @Test
        @DisplayName("隐藏列对普通用户不可见")
        void getVisibleColumns_HiddenColumnsNotVisible() {
            // Given
            DataAccessSecurityComponent.ColumnLevelPolicy policy = new DataAccessSecurityComponent.ColumnLevelPolicy();
            policy.setPolicyId("col-policy-001");
            policy.setTableName("users");
            policy.setHiddenColumns(Set.of("password", "secret_key"));
            policy.setEnabled(true);
            dataAccessSecurity.defineColumnLevelPolicy(policy);

            when(securityManagerComponent.getUserRoles("user1")).thenReturn(Set.of("USER"));

            Set<String> allColumns = new HashSet<>(Set.of("id", "name", "email", "password", "secret_key"));

            // When
            Set<String> visibleColumns = dataAccessSecurity.getVisibleColumns("user1", "users", allColumns);

            // Then
            assertThat(visibleColumns).containsExactlyInAnyOrder("id", "name", "email");
            assertThat(visibleColumns).doesNotContain("password", "secret_key");
        }

        @Test
        @DisplayName("允许角色可以看到所有列")
        void getVisibleColumns_AllowedRoleSeesAllColumns() {
            // Given
            DataAccessSecurityComponent.ColumnLevelPolicy policy = new DataAccessSecurityComponent.ColumnLevelPolicy();
            policy.setPolicyId("col-policy-001");
            policy.setTableName("users");
            policy.setHiddenColumns(Set.of("password", "secret_key"));
            policy.setAllowedRoles(Set.of("ADMIN"));
            policy.setEnabled(true);
            dataAccessSecurity.defineColumnLevelPolicy(policy);

            when(securityManagerComponent.getUserRoles("admin")).thenReturn(Set.of("ADMIN"));

            Set<String> allColumns = new HashSet<>(Set.of("id", "name", "email", "password", "secret_key"));

            // When
            Set<String> visibleColumns = dataAccessSecurity.getVisibleColumns("admin", "users", allColumns);

            // Then
            assertThat(visibleColumns).containsExactlyInAnyOrderElementsOf(allColumns);
        }

        @Test
        @DisplayName("获取需要脱敏的列")
        void getMaskedColumns_ReturnsMaskedColumns() {
            // Given
            DataAccessSecurityComponent.ColumnLevelPolicy policy = new DataAccessSecurityComponent.ColumnLevelPolicy();
            policy.setPolicyId("col-policy-001");
            policy.setTableName("users");
            policy.setMaskedColumns(Set.of("phone", "email", "id_card"));
            policy.setEnabled(true);
            dataAccessSecurity.defineColumnLevelPolicy(policy);

            when(securityManagerComponent.getUserRoles("user1")).thenReturn(Set.of("USER"));

            // When
            Set<String> maskedColumns = dataAccessSecurity.getMaskedColumns("user1", "users");

            // Then
            assertThat(maskedColumns).containsExactlyInAnyOrder("phone", "email", "id_card");
        }

        @Test
        @DisplayName("允许角色不需要脱敏")
        void getMaskedColumns_AllowedRoleNoMasking() {
            // Given
            DataAccessSecurityComponent.ColumnLevelPolicy policy = new DataAccessSecurityComponent.ColumnLevelPolicy();
            policy.setPolicyId("col-policy-001");
            policy.setTableName("users");
            policy.setMaskedColumns(Set.of("phone", "email"));
            policy.setAllowedRoles(Set.of("ADMIN"));
            policy.setEnabled(true);
            dataAccessSecurity.defineColumnLevelPolicy(policy);

            when(securityManagerComponent.getUserRoles("admin")).thenReturn(Set.of("ADMIN"));

            // When
            Set<String> maskedColumns = dataAccessSecurity.getMaskedColumns("admin", "users");

            // Then
            assertThat(maskedColumns).isEmpty();
        }
    }

    // ==================== 数据脱敏测试 ====================

    @Nested
    @DisplayName("数据脱敏测试")
    class DataMaskingTests {

        @Test
        @DisplayName("手机号脱敏")
        void maskData_Phone() {
            // When
            String masked = dataAccessSecurity.maskData("13812345678", "PHONE");

            // Then
            assertThat(masked).isEqualTo("138****5678");
        }

        @Test
        @DisplayName("身份证脱敏")
        void maskData_IdCard() {
            // When
            String masked = dataAccessSecurity.maskData("110101199001011234", "ID_CARD");

            // Then
            assertThat(masked).isEqualTo("110101********1234");
        }

        @Test
        @DisplayName("邮箱脱敏")
        void maskData_Email() {
            // When
            String masked = dataAccessSecurity.maskData("test@example.com", "EMAIL");

            // Then
            assertThat(masked).isEqualTo("te***@example.com");
        }

        @Test
        @DisplayName("银行卡脱敏")
        void maskData_BankCard() {
            // When
            String masked = dataAccessSecurity.maskData("6222021234567890123", "BANK_CARD");

            // Then
            assertThat(masked).isEqualTo("6222 **** **** 0123");
        }

        @Test
        @DisplayName("姓名脱敏")
        void maskData_Name() {
            // When
            String masked = dataAccessSecurity.maskData("张三丰", "NAME");

            // Then
            assertThat(masked).isEqualTo("张**");
        }

        @Test
        @DisplayName("地址脱敏")
        void maskData_Address() {
            // When
            String masked = dataAccessSecurity.maskData("北京市朝阳区建国路100号", "ADDRESS");

            // Then
            assertThat(masked).isEqualTo("北京市朝阳区****");
        }

        @Test
        @DisplayName("空数据不脱敏")
        void maskData_NullOrEmpty() {
            assertThat(dataAccessSecurity.maskData(null, "PHONE")).isNull();
            assertThat(dataAccessSecurity.maskData("", "PHONE")).isEmpty();
        }

        @Test
        @DisplayName("自定义脱敏规则")
        void maskData_CustomRule() {
            // Given
            DataAccessSecurityComponent.DataMaskRule rule = new DataAccessSecurityComponent.DataMaskRule();
            rule.setRuleId("custom-001");
            rule.setDataType("CUSTOM");
            rule.setKeepStart(2);
            rule.setKeepEnd(2);
            rule.setReplacement("#");
            rule.setEnabled(true);
            dataAccessSecurity.defineDataMaskRule(rule);

            // When
            String masked = dataAccessSecurity.maskData("ABCDEFGH", "CUSTOM");

            // Then
            assertThat(masked).isEqualTo("AB####GH");
        }

        @Test
        @DisplayName("批量脱敏行数据")
        void maskRowData_Success() {
            // Given
            Map<String, Object> rowData = new HashMap<>();
            rowData.put("id", 1);
            rowData.put("name", "张三");
            rowData.put("phone", "13812345678");
            rowData.put("email", "test@example.com");

            Set<String> columnsToMask = Set.of("phone", "email");
            Map<String, String> columnDataTypes = Map.of("phone", "PHONE", "email", "EMAIL");

            // When
            Map<String, Object> maskedData = dataAccessSecurity.maskRowData(rowData, columnsToMask, columnDataTypes);

            // Then
            assertThat(maskedData.get("id")).isEqualTo(1);
            assertThat(maskedData.get("name")).isEqualTo("张三");
            assertThat(maskedData.get("phone")).isEqualTo("138****5678");
            assertThat(maskedData.get("email")).isEqualTo("te***@example.com");
        }

        @Test
        @DisplayName("自动检测并脱敏敏感数据")
        void autoMaskSensitiveData_Success() {
            // Given
            String text = "联系电话：13812345678，邮箱：test@example.com";

            // When
            String masked = dataAccessSecurity.autoMaskSensitiveData(text);

            // Then
            assertThat(masked).contains("138****5678");
            assertThat(masked).contains("te***@example.com");
        }

        @Test
        @DisplayName("匿名化数据")
        void anonymizeData_Success() {
            // Given
            when(securityManagerComponent.hashPassword(anyString())).thenReturn("abcdef1234567890abcdef");
            when(securityManagerComponent.encryptData(anyString())).thenReturn("encrypted_data");

            // When
            String anonymized = dataAccessSecurity.anonymizeData("13812345678", "PHONE");

            // Then
            assertThat(anonymized).startsWith("ANON_");
            assertThat(anonymized).hasSize(21); // "ANON_" + 16 chars
        }
    }

    // ==================== 告警规则测试 ====================

    @Nested
    @DisplayName("告警规则测试")
    class AlertRuleTests {

        @Test
        @DisplayName("定义告警规则成功")
        void defineAlertRule_Success() {
            // Given
            DataAccessSecurityComponent.AlertRule rule = new DataAccessSecurityComponent.AlertRule();
            rule.setRuleId("alert-001");
            rule.setRuleName("登录失败告警");
            rule.setEventType("LOGIN_FAILED");
            rule.setThreshold(5);
            rule.setTimeWindowMinutes(10);
            rule.setSeverity("HIGH");
            rule.setNotifyChannels(List.of("EMAIL", "SMS"));
            rule.setNotifyUsers(List.of("admin@example.com"));
            rule.setEnabled(true);

            // When
            dataAccessSecurity.defineAlertRule(rule);

            // Then
            verify(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
        }

        @Test
        @DisplayName("记录安全事件")
        void recordSecurityEvent_Success() {
            // Given
            String username = "user1";
            String eventType = "LOGIN_FAILED";
            String description = "登录失败";

            // When
            dataAccessSecurity.recordSecurityEvent(username, eventType, description);

            // Then
            verify(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
            verify(valueOperations).increment(anyString());
        }

        @Test
        @DisplayName("记录完整安全事件")
        void recordSecurityEvent_Full() {
            // Given
            String username = "user1";
            String eventType = "PERMISSION_DENIED";
            String description = "权限被拒绝";
            String ipAddress = "192.168.1.100";
            String resource = "/api/admin/users";
            Map<String, Object> details = Map.of("action", "DELETE", "targetUser", "user2");

            // When
            dataAccessSecurity.recordSecurityEvent(username, eventType, description, ipAddress, resource, details);

            // Then
            verify(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
            verify(auditManagerComponent).recordAuditLog(any(), any(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("初始化默认告警规则")
        void initializeDefaultAlertRules_Success() {
            // When
            dataAccessSecurity.initializeDefaultAlertRules();

            // Then
            verify(valueOperations, atLeast(2)).set(anyString(), anyString(), any(java.time.Duration.class));
        }
    }

    // ==================== 安全事件查询测试 ====================

    @Nested
    @DisplayName("安全事件查询测试")
    class SecurityEventQueryTests {

        @Test
        @DisplayName("查询安全事件 - 无结果")
        void querySecurityEvents_NoResults() {
            // Given
            when(stringRedisTemplate.keys(anyString())).thenReturn(Collections.emptySet());

            // When
            List<DataAccessSecurityComponent.SecurityEvent> events = 
                    dataAccessSecurity.querySecurityEvents("LOGIN_FAILED", null, null, null, 100);

            // Then
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("获取告警列表 - 无结果")
        void getAlerts_NoResults() {
            // Given
            when(stringRedisTemplate.keys(anyString())).thenReturn(Collections.emptySet());

            // When
            List<Map<String, Object>> alerts = 
                    dataAccessSecurity.getAlerts("HIGH", null, null, 100);

            // Then
            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("获取安全监控统计")
        void getSecurityMonitoringStats_Success() {
            // Given
            when(stringRedisTemplate.keys(anyString())).thenReturn(Collections.emptySet());
            LocalDateTime startTime = LocalDateTime.now().minusDays(1);
            LocalDateTime endTime = LocalDateTime.now();

            // When
            Map<String, Object> stats = dataAccessSecurity.getSecurityMonitoringStats(startTime, endTime);

            // Then
            assertThat(stats).containsKey("eventCounts");
            assertThat(stats).containsKey("alertCounts");
            assertThat(stats).containsKey("securityScore");
            assertThat(stats.get("securityScore")).isEqualTo(100); // 无事件时满分
        }
    }

    // ==================== RowAccessResult 测试 ====================

    @Nested
    @DisplayName("RowAccessResult 测试")
    class RowAccessResultTests {

        @Test
        @DisplayName("创建允许结果")
        void allowed_Success() {
            // When
            DataAccessSecurityComponent.RowAccessResult result = DataAccessSecurityComponent.RowAccessResult.allowed();

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getMessage()).isEqualTo("访问允许");
            assertThat(result.getPolicyId()).isNull();
        }

        @Test
        @DisplayName("创建拒绝结果")
        void denied_Success() {
            // When
            DataAccessSecurityComponent.RowAccessResult result = 
                    DataAccessSecurityComponent.RowAccessResult.denied("policy-001", "权限不足");

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getPolicyId()).isEqualTo("policy-001");
            assertThat(result.getMessage()).isEqualTo("权限不足");
        }
    }
}
