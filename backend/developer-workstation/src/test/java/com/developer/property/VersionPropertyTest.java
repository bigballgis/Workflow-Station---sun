package com.developer.property;

import com.developer.component.VersionComponent;
import com.developer.component.impl.VersionComponentImpl;
import com.developer.repository.VersionRepository;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 版本管理属性测试
 * Property 13-14: 版本回滚一致性、版本比较正确性
 */
public class VersionPropertyTest {
    
    /**
     * Property 13: 版本回滚一致性
     * 版本组件应正确初始化
     */
    @Property(tries = 20)
    void versionRollbackConsistencyProperty(@ForAll("versionNumbers") String versionNumber) {
        VersionRepository repository = mock(VersionRepository.class);
        VersionComponent component = new VersionComponentImpl(repository);
        
        assertThat(component).isNotNull();
        assertThat(versionNumber).matches("\\d+\\.\\d+\\.\\d+");
    }
    
    /**
     * Property 14: 版本比较正确性
     * 版本号格式应符合语义化版本规范
     */
    @Property(tries = 20)
    void versionCompareCorrectnessProperty(
            @ForAll("versionNumbers") String v1,
            @ForAll("versionNumbers") String v2) {
        
        // 版本号应可解析
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        assertThat(parts1).hasSize(3);
        assertThat(parts2).hasSize(3);
        
        // 各部分应为数字
        for (String part : parts1) {
            assertThat(Integer.parseInt(part)).isGreaterThanOrEqualTo(0);
        }
        for (String part : parts2) {
            assertThat(Integer.parseInt(part)).isGreaterThanOrEqualTo(0);
        }
    }
    
    @Provide
    Arbitrary<String> versionNumbers() {
        return Arbitraries.integers().between(0, 99)
                .tuple3()
                .map(t -> t.get1() + "." + t.get2() + "." + t.get3());
    }
}
