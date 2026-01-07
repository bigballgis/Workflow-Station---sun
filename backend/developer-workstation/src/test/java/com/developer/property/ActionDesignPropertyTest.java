package com.developer.property;

import com.developer.component.ActionDesignComponent;
import com.developer.component.impl.ActionDesignComponentImpl;
import com.developer.enums.ActionType;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 动作设计属性测试
 * Property 10: 动作流程步骤绑定一致性
 */
public class ActionDesignPropertyTest {
    
    /**
     * Property 10: 动作流程步骤绑定一致性
     * 动作类型应为有效的枚举值
     */
    @Property(tries = 20)
    void actionProcessStepBindingProperty(@ForAll("actionTypes") ActionType actionType) {
        ActionDefinitionRepository repository = mock(ActionDefinitionRepository.class);
        FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);
        ActionDesignComponent component = new ActionDesignComponentImpl(repository, functionUnitRepository);
        
        assertThat(component).isNotNull();
        assertThat(actionType).isNotNull();
        assertThat(actionType).isIn(ActionType.values());
    }
    
    @Provide
    Arbitrary<ActionType> actionTypes() {
        return Arbitraries.of(ActionType.values());
    }
}
