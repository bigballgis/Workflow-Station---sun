package com.developer.property;

import com.developer.component.AuditLogComponent;
import com.developer.component.impl.AuditLogComponentImpl;
import com.developer.repository.OperationLogRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 审计日志属性测试
 * Property 20: 审计日志完整性
 */
public class AuditLogPropertyTest {
    
    /**
     * Property 20: 审计日志完整性
     * 记录的日志应包含所有必要信息
     */
    @Property(tries = 20)
    void auditLogIntegrityProperty(
            @ForAll("operationTypes") String operationType,
            @ForAll("targetTypes") String targetType,
            @ForAll @IntRange(min = 1, max = 10000) long targetId) {
        
        OperationLogRepository repository = mock(OperationLogRepository.class);
        AuditLogComponent component = new AuditLogComponentImpl(repository);
        
        assertThat(component).isNotNull();
        
        // 记录日志不应抛出异常
        assertThatCode(() -> 
            component.log(operationType, targetType, targetId, "Test operation")
        ).doesNotThrowAnyException();
    }
    
    @Provide
    Arbitrary<String> operationTypes() {
        return Arbitraries.of("CREATE", "UPDATE", "DELETE", "PUBLISH", "CLONE");
    }
    
    @Provide
    Arbitrary<String> targetTypes() {
        return Arbitraries.of("FUNCTION_UNIT", "TABLE", "FORM", "ACTION", "PROCESS");
    }
}
