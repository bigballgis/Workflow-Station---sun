package com.admin.controller;

import com.admin.component.FunctionUnitManagerComponent;
import com.admin.dto.response.FunctionUnitContentItemDTO;
import com.admin.dto.response.FunctionUnitInfo;
import com.admin.entity.FunctionUnit;
import com.admin.enums.ContentType;
import com.admin.enums.FunctionUnitStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.FunctionUnitNotFoundException;
import com.platform.common.dto.ApiResponse;
import net.jqwik.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for {@link FunctionUnitController} unified response format.
 *
 * <p><b>Validates: Requirements 5.2, 5.3, 5.4, 32.1, 32.2, 32.3, 33.2, 35.2</b>
 */
class FunctionUnitControllerPropertyTest {

    private FunctionUnitController createController(FunctionUnitManagerComponent mgr) {
        return new FunctionUnitController(
                mgr,
                mock(com.admin.component.DeploymentManagerComponent.class),
                mock(com.admin.service.FunctionUnitAccessService.class));
    }

    // ── Property 4: Admin Center API unified response format ──────────

    /**
     * **Validates: Requirements 5.2, 32.1**
     *
     * Property 4a: success → HTTP 200 + success=true
     */
    @Property(tries = 100)
    @Label("Property 4: success → HTTP 200 + success=true")
    void successAlwaysReturns200WithSuccessTrue(
            @ForAll("functionUnitStatuses") FunctionUnitStatus status) {
        var mgr = mock(FunctionUnitManagerComponent.class);
        FunctionUnit unit = FunctionUnit.builder()
                .id("test-id").name("Test").code("test").version("1.0.0")
                .status(status).enabled(true)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        var page = new org.springframework.data.domain.PageImpl<>(List.of(unit));
        when(mgr.listFunctionUnitsByStatus(eq(FunctionUnitStatus.DEPLOYED), any()))
                .thenReturn(page);

        var controller = createController(mgr);
        ResponseEntity<ApiResponse<List<FunctionUnitInfo>>> response =
                controller.getDeployedFunctionUnits();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
    }

    /**
     * **Validates: Requirements 5.3, 5.4, 32.2, 32.3**
     *
     * Property 4b: AdminBusinessException → HTTP 400 + success=false
     */
    @Property(tries = 100)
    @Label("Property 4: AdminBusinessException → HTTP 400 + success=false")
    void adminBusinessExceptionReturns400(@ForAll String errorMessage) {
        var mgr = mock(FunctionUnitManagerComponent.class);
        when(mgr.listFunctionUnitsByStatus(any(), any()))
                .thenThrow(new AdminBusinessException("TEST_ERROR", errorMessage));

        var controller = createController(mgr);
        ResponseEntity<ApiResponse<List<FunctionUnitInfo>>> response =
                controller.getDeployedFunctionUnits();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError()).isNotNull();
    }

    /**
     * **Validates: Requirements 5.4, 32.3**
     *
     * Property 4c: FunctionUnitNotFoundException → HTTP 400 + success=false
     */
    @Property(tries = 100)
    @Label("Property 4: FunctionUnitNotFoundException → HTTP 400 + success=false")
    void functionUnitNotFoundReturns400(@ForAll String unitId) {
        var mgr = mock(FunctionUnitManagerComponent.class);
        when(mgr.setEnabled(anyString(), any(Boolean.class)))
                .thenThrow(new FunctionUnitNotFoundException(unitId));

        var controller = createController(mgr);
        var request = new com.admin.dto.request.SetEnabledRequest(true);
        ResponseEntity<ApiResponse<FunctionUnitInfo>> response =
                controller.setEnabled(unitId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    /**
     * **Validates: Requirements 5.4, 32.3**
     *
     * Property 4d: generic Exception → HTTP 500 + success=false
     */
    @Property(tries = 100)
    @Label("Property 4: generic Exception → HTTP 500 + success=false")
    void genericExceptionReturns500(@ForAll String errorMessage) {
        var mgr = mock(FunctionUnitManagerComponent.class);
        when(mgr.listLatestDeployedFunctionUnits())
                .thenThrow(new RuntimeException(errorMessage));

        var controller = createController(mgr);
        ResponseEntity<ApiResponse<List<FunctionUnitInfo>>> response =
                controller.getLatestDeployedFunctionUnits();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Provide
    Arbitrary<FunctionUnitStatus> functionUnitStatuses() {
        return Arbitraries.of(FunctionUnitStatus.values());
    }

    // ── Property 16: Consolidated content endpoint filter correctness ──

    /**
     * **Validates: Requirements 35.2**
     *
     * Property 16a: type=null → returns all content items
     */
    @Property(tries = 100)
    @Label("Property 16: type=null → returns all content items")
    void nullTypeReturnsAllContents(@ForAll("contentTypes") ContentType contentType) {
        var mgr = mock(FunctionUnitManagerComponent.class);
        var item = FunctionUnitContentItemDTO.builder()
                .id("item-1").contentType(contentType.name())
                .contentName("test").contentData("data").sourceId("src-1")
                .build();
        when(mgr.getContentsByType("fu-1", null)).thenReturn(List.of(item));

        var controller = createController(mgr);
        ResponseEntity<ApiResponse<List<FunctionUnitContentItemDTO>>> response =
                controller.getContents("fu-1", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);
    }

    /**
     * **Validates: Requirements 35.2**
     *
     * Property 16b: type=valid → returns filtered content items
     */
    @Property(tries = 100)
    @Label("Property 16: type=valid → returns filtered content items")
    void validTypeReturnsFilteredContents(@ForAll("contentTypes") ContentType contentType) {
        var mgr = mock(FunctionUnitManagerComponent.class);
        var item = FunctionUnitContentItemDTO.builder()
                .id("item-1").contentType(contentType.name())
                .contentName("test").contentData("data").sourceId("src-1")
                .build();
        when(mgr.getContentsByType("fu-1", contentType.name())).thenReturn(List.of(item));

        var controller = createController(mgr);
        ResponseEntity<ApiResponse<List<FunctionUnitContentItemDTO>>> response =
                controller.getContents("fu-1", contentType.name());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).allMatch(
                dto -> dto.getContentType().equals(contentType.name()));
    }

    /**
     * **Validates: Requirements 35.2**
     *
     * Property 16c: type=invalid → HTTP 400
     */
    @Property(tries = 100)
    @Label("Property 16: type=invalid → HTTP 400")
    void invalidTypeReturns400(@ForAll("invalidContentTypes") String invalidType) {
        var mgr = mock(FunctionUnitManagerComponent.class);
        when(mgr.getContentsByType(anyString(), eq(invalidType)))
                .thenThrow(new AdminBusinessException("INVALID_CONTENT_TYPE",
                        "Invalid content type: " + invalidType));

        var controller = createController(mgr);
        ResponseEntity<ApiResponse<List<FunctionUnitContentItemDTO>>> response =
                controller.getContents("fu-1", invalidType);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Provide
    Arbitrary<ContentType> contentTypes() {
        return Arbitraries.of(ContentType.values());
    }

    @Provide
    Arbitrary<String> invalidContentTypes() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .filter(s -> {
                    try {
                        ContentType.valueOf(s.toUpperCase());
                        return false;
                    } catch (IllegalArgumentException e) {
                        return true;
                    }
                });
    }
}
