package com.developer.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.developer.dto.MainTableViewDtos.MainTableViewAccessRuleDTO;
import com.developer.entity.MainTableViewAccess;
import com.developer.enums.MainTableViewAccessTargetType;
import com.developer.exception.DeveloperBusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class MainTableViewAccessRulesValidatorTest {

    @Test
    void emptyRulesAllowed() {
        assertThatCode(() -> MainTableViewAccessRulesValidator.validatePairedOrEmpty(List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void pairedDtoRulesAllowed() {
        assertThatCode(() -> MainTableViewAccessRulesValidator.validatePairedOrEmpty(List.of(
                MainTableViewAccessRuleDTO.builder()
                        .targetType("BUSINESS_UNIT")
                        .targetId("bu-e2e-finance")
                        .build(),
                MainTableViewAccessRuleDTO.builder()
                        .targetType("ROLE")
                        .targetId("role-manager")
                        .build())))
                .doesNotThrowAnyException();
    }

    @Test
    void partialDtoRulesRejected() {
        assertThatThrownBy(() -> MainTableViewAccessRulesValidator.validatePairedOrEmpty(List.of(
                MainTableViewAccessRuleDTO.builder()
                        .targetType("BUSINESS_UNIT")
                        .targetId("bu-e2e-finance")
                        .build())))
                .isInstanceOf(DeveloperBusinessException.class)
                .extracting(ex -> ((DeveloperBusinessException) ex).getErrorCode())
                .isEqualTo(MainTableViewAccessRulesValidator.PAIR_ERROR_CODE);
    }

    @Test
    void partialEntityRulesRejected() {
        MainTableViewAccess buOnly = MainTableViewAccess.builder()
                .targetType(MainTableViewAccessTargetType.BUSINESS_UNIT)
                .targetId("bu-e2e-finance")
                .build();
        assertThatThrownBy(() -> MainTableViewAccessRulesValidator.validatePairedOrEmptyEntities(List.of(buOnly)))
                .isInstanceOf(DeveloperBusinessException.class);
    }
}
