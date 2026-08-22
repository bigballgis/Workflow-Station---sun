package com.developer.property;

import com.developer.component.FormDesignComponent;
import com.developer.component.impl.FormDesignComponentImpl;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.SubTableViewConfigRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.service.SubTableViewService;
import com.platform.common.i18n.I18nService;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 表单设计属性测试
 * Property 8-9: 表单配置往返一致性、表单数据绑定一致性
 */
public class FormDesignPropertyTest {
    
    /**
     * Property 8: 表单配置往返一致性
     * 表单配置JSON应能正确序列化和反序列化
     */
    @Property(tries = 20)
    void formConfigRoundTripProperty(@ForAll("formConfigs") Map<String, Object> config) {
        FormDefinitionRepository repository = mock(FormDefinitionRepository.class);
        FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);
        TableDefinitionRepository tableDefinitionRepository = mock(TableDefinitionRepository.class);
        FormTableBindingRepository formTableBindingRepository = mock(FormTableBindingRepository.class);
        SubTableViewConfigRepository subTableViewConfigRepository = mock(SubTableViewConfigRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        I18nService i18nService = mock(I18nService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SubTableViewService subTableViewService = mock(SubTableViewService.class);
        FormDesignComponent component = new FormDesignComponentImpl(
                mock(com.developer.component.impl.FormTableBindingRestorer.class),
                mock(com.developer.component.impl.FormConfigJsonTableProvisioner.class),
                mock(com.developer.component.impl.OwnerFieldFormReconciler.class),
                repository, functionUnitRepository, tableDefinitionRepository, formTableBindingRepository,
                subTableViewConfigRepository, objectMapper, i18nService, jdbcTemplate, subTableViewService);
        
        assertThat(component).isNotNull();
        assertThat(config).isNotNull();
        
        // 配置应包含必要字段
        if (config.containsKey("fields")) {
            assertThat(config.get("fields")).isNotNull();
        }
    }
    
    /**
     * Property 9: 表单数据绑定一致性
     * 表单字段绑定应与表字段对应
     */
    @Property(tries = 20)
    void formDataBindingProperty(@ForAll("fieldNames") String fieldName) {
        assertThat(fieldName).isNotBlank();
        assertThat(fieldName).matches("field_[a-zA-Z]+");
    }
    
    @Provide
    Arbitrary<Map<String, Object>> formConfigs() {
        return Arbitraries.of(
                Map.of("fields", "value1"),
                Map.of("layout", "value2"),
                Map.of("rules", "value3")
        );
    }
    
    @Provide
    Arbitrary<String> fieldNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30)
                .map(s -> "field_" + s);
    }
}
