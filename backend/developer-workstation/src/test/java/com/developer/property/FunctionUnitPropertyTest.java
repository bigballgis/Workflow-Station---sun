package com.developer.property;

import com.developer.component.FunctionUnitComponent;
import com.developer.component.impl.FunctionUnitComponentImpl;
import com.developer.dto.FunctionUnitRequest;
import com.developer.entity.FunctionUnit;
import com.developer.enums.FunctionUnitStatus;
import com.developer.repository.FunctionUnitRepository;
import net.jqwik.api.*;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 功能单元属性测试
 * Property 1-3: 名称唯一性、发布状态一致性、克隆完整性
 */
public class FunctionUnitPropertyTest {
    
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    /**
     * Property 1: 功能单元名称唯一性
     * 对于任意有效名称，创建后再次创建相同名称应抛出异常
     */
    @Property(tries = 20)
    void nameUniquenessProperty(@ForAll("validNames") String name) {
        FunctionUnitRepository repository = mock(FunctionUnitRepository.class);
        FunctionUnitComponent component = new FunctionUnitComponentImpl(repository);
        
        // 第一次检查名称不存在
        when(repository.existsByName(name)).thenReturn(false);
        when(repository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit fu = invocation.getArgument(0);
            fu.setId(idGenerator.getAndIncrement());
            return fu;
        });
        
        FunctionUnitRequest request = new FunctionUnitRequest();
        request.setName(name);
        request.setDescription("Test description");
        
        FunctionUnit created = component.create(request);
        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo(name);
        
        // 第二次检查名称已存在
        when(repository.existsByName(name)).thenReturn(true);
        
        assertThatThrownBy(() -> component.create(request))
                .isInstanceOf(RuntimeException.class);
    }
    
    /**
     * Property 2: 功能单元发布状态一致性
     * 新创建的功能单元状态应为DRAFT
     */
    @Property(tries = 20)
    void initialStatusProperty(@ForAll("validNames") String name) {
        FunctionUnitRepository repository = mock(FunctionUnitRepository.class);
        FunctionUnitComponent component = new FunctionUnitComponentImpl(repository);
        
        when(repository.existsByName(name)).thenReturn(false);
        when(repository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit fu = invocation.getArgument(0);
            fu.setId(idGenerator.getAndIncrement());
            return fu;
        });
        
        FunctionUnitRequest request = new FunctionUnitRequest();
        request.setName(name);
        
        FunctionUnit created = component.create(request);
        
        assertThat(created.getStatus()).isEqualTo(FunctionUnitStatus.DRAFT);
    }

    /**
     * Property 3: 功能单元克隆完整性
     * 克隆后的功能单元应具有新名称但保留原有描述
     */
    @Property(tries = 20)
    void cloneIntegrityProperty(
            @ForAll("validNames") String originalName,
            @ForAll("validNames") String cloneName) {
        
        Assume.that(!originalName.equals(cloneName));
        
        FunctionUnitRepository repository = mock(FunctionUnitRepository.class);
        FunctionUnitComponent component = new FunctionUnitComponentImpl(repository);
        
        // 创建原始功能单元
        FunctionUnit original = new FunctionUnit();
        original.setId(1L);
        original.setName(originalName);
        original.setDescription("Original description");
        original.setStatus(FunctionUnitStatus.PUBLISHED);
        
        when(repository.findById(1L)).thenReturn(Optional.of(original));
        when(repository.existsByName(cloneName)).thenReturn(false);
        
        // 使用不同的 ID 生成器确保克隆的 ID 不同
        final long[] nextId = {2L};
        when(repository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit fu = invocation.getArgument(0);
            if (fu.getId() == null) {
                fu.setId(nextId[0]++);
            }
            return fu;
        });
        
        FunctionUnit cloned = component.clone(1L, cloneName);
        
        assertThat(cloned.getName()).isEqualTo(cloneName);
        assertThat(cloned.getDescription()).isEqualTo(original.getDescription());
        assertThat(cloned.getStatus()).isEqualTo(FunctionUnitStatus.DRAFT);
        assertThat(cloned.getId()).isNotEqualTo(original.getId());
    }
    
    @Provide
    Arbitrary<String> validNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(50)
                .map(s -> "FU_" + s);
    }
}
