package com.developer.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiStudioPhaseTest {

    @Test
    void keyPatternListsExactlyTheEnumConstantsInOrder() {
        List<String> names = Arrays.stream(AiStudioPhase.values()).map(Enum::name).toList();
        assertEquals(names, List.of(AiStudioPhase.KEY_PATTERN.split("\\|")));
    }

    /** 常量名是落库 / API 的 key，改名等于数据迁移。 */
    @Test
    void keysAreTheStoredPhaseStringsInDesignerOrder() {
        assertEquals(List.of("REQUIREMENTS", "PROCESS_DESIGN", "TABLE_DESIGN", "FORM_DESIGN", "VIEW_DESIGN", "ACTION_DESIGN",
                        "AUTOMATION", "CONNECTIONS", "EMAIL_TEMPLATES", "EMAIL_MONITORS", "DECISION_DESIGN",
                        "VALIDATION"),
                Arrays.stream(AiStudioPhase.values()).map(Enum::name).toList());
    }

    @Test
    void onlyRequirementsAndValidationHaveNoProposalScope() {
        for (AiStudioPhase phase : AiStudioPhase.values()) {
            assertEquals(phase != AiStudioPhase.REQUIREMENTS && phase != AiStudioPhase.VALIDATION,
                    phase.proposalScope().isPresent(), phase.name());
        }
    }

    /** Function Unit Design 文档按设计阶段分节：需求访谈不是其中一节。 */
    @Test
    void designPhasesAreEveryPhaseButTheRequirementsInterview() {
        List<AiStudioPhase> expected = Arrays.stream(AiStudioPhase.values())
                .filter(phase -> phase != AiStudioPhase.REQUIREMENTS).toList();
        assertEquals(expected, AiStudioPhase.designPhases());
        assertEquals(AiStudioPhase.PROCESS_DESIGN, AiStudioPhase.designPhases().get(0));
    }

    @Test
    void fromKeyIsExactAndNullSafe() {
        assertEquals(AiStudioPhase.VIEW_DESIGN, AiStudioPhase.fromKey("VIEW_DESIGN").orElseThrow());
        assertTrue(AiStudioPhase.fromKey(null).isEmpty());
        assertFalse(AiStudioPhase.isValid("view_design"));
        assertFalse(AiStudioPhase.isValid("REVIEW"));
    }
}
