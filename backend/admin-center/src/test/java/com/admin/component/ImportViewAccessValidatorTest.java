package com.admin.component;

import com.admin.exception.AdminBusinessException;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.security.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportViewAccessValidatorTest {

    @Mock private RoleRepository roleRepository;
    @Mock private BusinessUnitRepository businessUnitRepository;

    private ImportViewAccessValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ImportViewAccessValidator(roleRepository, businessUnitRepository, new ObjectMapper());
    }

    @Test
    void remapsTargetCodeToLocalId() throws Exception {
        Role role = new Role();
        role.setId("role-local");
        when(roleRepository.findByCode("REVIEWER")).thenReturn(Optional.of(role));
        com.platform.security.entity.BusinessUnit bu = new com.platform.security.entity.BusinessUnit();
        bu.setId("bu-local");
        when(businessUnitRepository.findByCode("HQ")).thenReturn(Optional.of(bu));

        String json = """
                [{"viewName":"All","accessRules":[
                  {"targetType":"BUSINESS_UNIT","targetCode":"HQ"},
                  {"targetType":"ROLE","targetCode":"REVIEWER"}
                ]}]
                """;
        String remapped = validator.remapAndValidate(json);
        assertThat(remapped).contains("bu-local").contains("role-local");
    }

    @Test
    void unresolvedCode_fails() {
        when(roleRepository.findByCode("MISSING")).thenReturn(Optional.empty());
        String json = """
                [{"viewName":"All","accessRules":[
                  {"targetType":"ROLE","targetCode":"MISSING"}
                ]}]
                """;
        assertThatThrownBy(() -> validator.remapAndValidate(json))
                .isInstanceOf(AdminBusinessException.class)
                .extracting(ex -> ((AdminBusinessException) ex).getErrorCode())
                .isEqualTo(ImportViewAccessValidator.IMPORT_UNRESOLVED_CODE);
    }

    @Test
    void unpairedRoleWithoutBu_fails() {
        Role role = new Role();
        role.setId("role-local");
        when(roleRepository.findByCode("REVIEWER")).thenReturn(Optional.of(role));
        String json = """
                [{"viewName":"All","accessRules":[
                  {"targetType":"ROLE","targetCode":"REVIEWER"}
                ]}]
                """;
        assertThatThrownBy(() -> validator.remapAndValidate(json))
                .isInstanceOf(AdminBusinessException.class)
                .extracting(ex -> ((AdminBusinessException) ex).getErrorCode())
                .isEqualTo(ImportViewAccessValidator.PAIR_ERROR_CODE);
    }
}
