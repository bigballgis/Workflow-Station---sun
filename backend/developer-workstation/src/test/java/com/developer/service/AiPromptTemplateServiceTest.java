package com.developer.service;

import com.developer.dto.AiPromptTemplateResponse;
import com.developer.entity.AiPromptTemplate;
import com.developer.exception.AiGenerationException;
import com.developer.repository.AiPromptTemplateRepository;
import com.developer.service.impl.AiPromptTemplateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 提示词覆盖机制的单元测试。
 *
 * <p>盯住三件事：没有覆盖行时用镜像里的内置默认值、有覆盖行时用覆盖值（这正是"改完不用重新部署"的依据）、
 * 还原默认会真的把覆盖行删掉。另外 phase 走白名单，未知相位必须拒绝而不是就近落到某一段。</p>
 */
class AiPromptTemplateServiceTest {

    private AiPromptTemplateRepository repository;
    private AiPromptTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(AiPromptTemplateRepository.class);
        when(repository.findByPhase(anyString())).thenReturn(Optional.empty());
        service = new AiPromptTemplateServiceImpl(repository);
    }

    @Test
    void resolve_withoutOverride_returnsBuiltInPrompt() {
        String requirements = service.resolve("REQUIREMENTS");
        String generation = service.resolve("GENERATION");

        assertFalse(requirements.isBlank());
        assertTrue(generation.contains("---GENERATED_DATA_START---"),
                "内置 GENERATION 提示词必须仍是那份带输出契约的全文");
        assertNotEquals(requirements, generation);
    }

    @Test
    void resolve_withOverride_returnsStoredContentSoEditsTakeEffectWithoutRedeploy() {
        when(repository.findByPhase("DESIGN")).thenReturn(Optional.of(
                AiPromptTemplate.builder().phase("DESIGN").content("overridden design prompt").build()));

        assertEquals("overridden design prompt", service.resolve("DESIGN"));
        // 其它相位不受影响，仍走内置默认值
        assertNotEquals("overridden design prompt", service.resolve("REQUIREMENTS"));
    }

    @Test
    void get_reportsSourceAndAlwaysCarriesTheBuiltInDefault() {
        AiPromptTemplateResponse builtIn = service.get("REQUIREMENTS");
        assertEquals("BUILT_IN", builtIn.getSource());
        assertEquals(builtIn.getContent(), builtIn.getDefaultContent());
        assertNull(builtIn.getUpdatedAt());

        Instant editedAt = Instant.parse("2026-08-01T00:00:00Z");
        when(repository.findByPhase("DESIGN")).thenReturn(Optional.of(AiPromptTemplate.builder()
                .phase("DESIGN").content("custom").updatedBy("44027893").updatedAt(editedAt).build()));

        AiPromptTemplateResponse custom = service.get("DESIGN");
        assertEquals("CUSTOM", custom.getSource());
        assertEquals("custom", custom.getContent());
        assertNotEquals("custom", custom.getDefaultContent(), "内置默认值必须一并返回，供 UI 做还原对比");
        assertEquals("44027893", custom.getUpdatedBy());
        assertEquals(editedAt, custom.getUpdatedAt());
    }

    @Test
    void save_upsertsOverrideRowForThatPhase() {
        when(repository.save(any(AiPromptTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        service.save("generation", "new generation prompt");

        ArgumentCaptor<AiPromptTemplate> captor = ArgumentCaptor.forClass(AiPromptTemplate.class);
        verify(repository).save(captor.capture());
        assertEquals("GENERATION", captor.getValue().getPhase());
        assertEquals("new generation prompt", captor.getValue().getContent());
    }

    @Test
    void save_rejectsBlankContent() {
        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> service.save("DESIGN", "   "));
        assertEquals("AI_PROMPT_CONTENT_EMPTY", ex.getErrorCode());
        verify(repository, never()).save(any());
    }

    @Test
    void reset_dropsOverrideAndReportsBuiltInAgain() {
        AiPromptTemplateResponse after = service.reset("DESIGN");

        verify(repository).deleteByPhase("DESIGN");
        assertEquals("BUILT_IN", after.getSource());
        assertEquals(after.getDefaultContent(), after.getContent());
    }

    @Test
    void unknownPhase_isRejectedRatherThanFallingBackToSomeOtherPrompt() {
        for (String bad : List.of("", "PLANNING", "requirement")) {
            AiGenerationException ex = assertThrows(AiGenerationException.class, () -> service.get(bad));
            assertEquals("AI_PROMPT_PHASE_UNKNOWN", ex.getErrorCode());
        }
        assertEquals(List.of("REQUIREMENTS", "DESIGN", "GENERATION"), service.phases());
    }
}
