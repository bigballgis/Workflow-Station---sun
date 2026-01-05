package com.admin.properties;

import com.admin.component.DataPermissionManagerComponent;
import com.admin.component.DataPermissionManagerComponent.*;
import com.admin.entity.ColumnPermission;
import com.admin.entity.DataPermissionRule;
import com.admin.enums.DataPermissionType;
import com.admin.enums.DataScopeType;
import com.admin.repository.ColumnPermissionRepository;
import com.admin.repository.DataPermissionRuleRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 数据权限属性测试
 * 属性 12-14: 数据权限过滤、行级权限、列级权限
 * 验证需求: 需求 7.1-7.7
 */
class DataPermissionProperties {
    
    private DataPermissionManagerComponent component;
    private DataPermissionRuleRepository ruleRepository;
    private ColumnPermissionRepository columnPermissionRepository;
    
    @BeforeTry
    void setUp() {
        ruleRepository = Mockito.mock(DataPermissionRuleRepository.class);
        columnPermissionRepository = Mockito.mock(ColumnPermissionRepository.class);
        component = new DataPermissionManagerComponent(ruleRepository, columnPermissionRepository);
    }
    
    // ==================== 属性 12: 数据权限过滤正确性 ====================
    
    /**
     * 属性 12.1: ALL范围应返回无限制条件
     * Validates: Requirements 7.1
     */
    @Property(tries = 20)
    void allScopeReturnsNoRestriction(@ForAll("contexts") DataPermissionContext context) {
        DataPermissionRule rule = createRule(DataScopeType.ALL, null);
        when(ruleRepository.findApplicableRules(any(), any(), any(), any()))
                .thenReturn(List.of(rule));
        
        String filter = component.generateFilterCondition("test_table", context);
        assertThat(filter).isEqualTo("1=1");
    }
    
    /**
     * 属性 12.2: SELF范围应包含用户ID过滤
     * Validates: Requirements 7.3
     */
    @Property(tries = 20)
    void selfScopeFiltersbyUserId(@ForAll("contexts") DataPermissionContext context) {
        DataPermissionRule rule = createRule(DataScopeType.SELF, null);
        when(ruleRepository.findApplicableRules(any(), any(), any(), any()))
                .thenReturn(List.of(rule));
        
        String filter = component.generateFilterCondition("test_table", context);
        assertThat(filter).contains("created_by").contains(context.getUserId());
    }
    
    /**
     * 属性 12.3: DEPARTMENT范围应包含部门ID过滤
     * Validates: Requirements 7.2
     */
    @Property(tries = 20)
    void departmentScopeFiltersByDeptId(@ForAll("contexts") DataPermissionContext context) {
        DataPermissionRule rule = createRule(DataScopeType.DEPARTMENT, null);
        when(ruleRepository.findApplicableRules(any(), any(), any(), any()))
                .thenReturn(List.of(rule));
        
        String filter = component.generateFilterCondition("test_table", context);
        assertThat(filter).contains("department_id").contains(context.getCurrentDeptId());
    }
    
    /**
     * 属性 12.4: 无规则时返回全部数据
     * Validates: Requirements 7.7
     */
    @Property(tries = 20)
    void noRulesReturnsAll(@ForAll("contexts") DataPermissionContext context) {
        when(ruleRepository.findApplicableRules(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        
        String filter = component.generateFilterCondition("test_table", context);
        assertThat(filter).isEqualTo("1=1");
    }

    
    // ==================== 属性 13: 行级权限控制有效性 ====================
    
    /**
     * 属性 13.1: 部门层级过滤应包含所有子部门
     * Validates: Requirements 7.4
     */
    @Property(tries = 20)
    void deptHierarchyIncludesChildren(@ForAll("contextsWithChildren") DataPermissionContext context) {
        DataPermissionRule rule = createRule(DataScopeType.DEPARTMENT_AND_CHILDREN, null);
        when(ruleRepository.findApplicableRules(any(), any(), any(), any()))
                .thenReturn(List.of(rule));
        
        String filter = component.generateFilterCondition("test_table", context);
        
        assertThat(filter).contains("department_id IN");
        assertThat(filter).contains(context.getCurrentDeptId());
        for (String childId : context.getChildDeptIds()) {
            assertThat(filter).contains(childId);
        }
    }
    
    /**
     * 属性 13.2: 自定义过滤条件应被正确应用
     * Validates: Requirements 7.4
     */
    @Property(tries = 20)
    void customFilterIsApplied(@ForAll("customFilters") String customFilter,
                                @ForAll("contexts") DataPermissionContext context) {
        DataPermissionRule rule = createRule(DataScopeType.CUSTOM, customFilter);
        when(ruleRepository.findApplicableRules(any(), any(), any(), any()))
                .thenReturn(List.of(rule));
        
        String filter = component.generateFilterCondition("test_table", context);
        assertThat(filter).isEqualTo(customFilter);
    }
    
    // ==================== 属性 14: 列级权限和数据脱敏正确性 ====================
    
    /**
     * 属性 14.1: 手机号脱敏保留前3后4位
     * Validates: Requirements 7.5
     */
    @Property(tries = 20)
    void phoneMaskingPreservesEnds(@ForAll("phoneNumbers") String phone) {
        String masked = component.maskValue(phone, "phone");
        
        if (phone.length() >= 7) {
            assertThat(masked).startsWith(phone.substring(0, 3));
            assertThat(masked).endsWith(phone.substring(phone.length() - 4));
            assertThat(masked).contains("****");
        }
    }
    
    /**
     * 属性 14.2: 邮箱脱敏保留首字符和域名
     * Validates: Requirements 7.5
     */
    @Property(tries = 20)
    void emailMaskingPreservesDomain(@ForAll("emails") String email) {
        String masked = component.maskValue(email, "email");
        
        int atIndex = email.indexOf('@');
        if (atIndex > 1) {
            assertThat(masked).startsWith(String.valueOf(email.charAt(0)));
            assertThat(masked).endsWith(email.substring(atIndex));
        }
    }
    
    /**
     * 属性 14.3: 身份证脱敏保留前6后4位
     * Validates: Requirements 7.5
     */
    @Property(tries = 20)
    void idCardMaskingPreservesEnds(@ForAll("idCards") String idCard) {
        String masked = component.maskValue(idCard, "idcard");
        
        if (idCard.length() >= 10) {
            assertThat(masked).startsWith(idCard.substring(0, 6));
            assertThat(masked).endsWith(idCard.substring(idCard.length() - 4));
        }
    }
    
    /**
     * 属性 14.4: 脱敏后值不为空
     * Validates: Requirements 7.5
     */
    @Property(tries = 20)
    void maskedValueIsNotEmpty(@ForAll("phoneNumbers") String phone) {
        String masked = component.maskValue(phone, "phone");
        assertThat(masked).isNotNull().isNotEmpty();
    }

    
    // ==================== 数据生成器 ====================
    
    @Provide
    Arbitrary<DataPermissionContext> contexts() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofLength(8),
                Arbitraries.strings().alpha().ofLength(8).list().ofSize(2),
                Arbitraries.strings().alpha().ofLength(8)
        ).as((userId, roleIds, deptId) -> DataPermissionContext.builder()
                .userId(userId)
                .roleIds(roleIds)
                .currentDeptId(deptId)
                .deptIds(List.of(deptId))
                .childDeptIds(Collections.emptyList())
                .build());
    }
    
    @Provide
    Arbitrary<DataPermissionContext> contextsWithChildren() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofLength(8),
                Arbitraries.strings().alpha().ofLength(8).list().ofSize(2),
                Arbitraries.strings().alpha().ofLength(8),
                Arbitraries.strings().alpha().ofLength(8).list().ofMinSize(1).ofMaxSize(3)
        ).as((userId, roleIds, deptId, childIds) -> DataPermissionContext.builder()
                .userId(userId)
                .roleIds(roleIds)
                .currentDeptId(deptId)
                .deptIds(List.of(deptId))
                .childDeptIds(childIds)
                .build());
    }
    
    @Provide
    Arbitrary<String> customFilters() {
        return Arbitraries.of(
                "status = 'ACTIVE'",
                "created_at > '2024-01-01'",
                "amount < 10000",
                "region = 'ASIA'"
        );
    }
    
    @Provide
    Arbitrary<String> phoneNumbers() {
        return Arbitraries.strings().numeric().ofLength(11);
    }
    
    @Provide
    Arbitrary<String> emails() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10),
                Arbitraries.of("gmail.com", "example.com", "test.org")
        ).as((name, domain) -> name + "@" + domain);
    }
    
    @Provide
    Arbitrary<String> idCards() {
        return Arbitraries.strings().numeric().ofLength(18);
    }
    
    @Provide
    Arbitrary<String> anyValues() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20);
    }
    
    @Provide
    Arbitrary<String> maskTypes() {
        return Arbitraries.of("phone", "email", "idcard", "name", "bankcard", "default");
    }
    
    // ==================== 辅助方法 ====================
    
    private DataPermissionRule createRule(DataScopeType scope, String customFilter) {
        return DataPermissionRule.builder()
                .id(UUID.randomUUID().toString())
                .name("Test Rule")
                .permissionType(DataPermissionType.ROLE)
                .targetType("ROLE")
                .targetId("test-role")
                .resourceType("test_table")
                .dataScope(scope)
                .customFilter(customFilter)
                .enabled(true)
                .priority(100)
                .build();
    }
}
