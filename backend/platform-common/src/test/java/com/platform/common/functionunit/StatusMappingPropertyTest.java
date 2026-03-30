package com.platform.common.functionunit;

import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature: function-unit-design-review, Property 20: 状态枚举映射 round-trip
 *
 * 使用 jqwik 对所有 developer-workstation 状态枚举值验证
 * toAdminStatus → toDeveloperStatus 等价性。
 *
 * Validates: Requirements 29.2
 */
class StatusMappingPropertyTest {

    // Property 20: 状态枚举映射 round-trip
    // For any developer-workstation status value, mapping to admin-center
    // and back should produce the original value.
    @Property(tries = 100)
    void developerStatusRoundTrip(@ForAll("developerStatuses") String developerStatus) {
        // Act: developer → admin → developer
        String adminStatus = StatusMapping.toAdminStatus(developerStatus);
        String roundTripped = StatusMapping.toDeveloperStatus(adminStatus);

        // Assert: round-trip produces the original value
        assertThat(roundTripped).isEqualTo(developerStatus);
    }

    @Property(tries = 100)
    void toAdminStatusShouldMapCorrectly(@ForAll("developerStatuses") String developerStatus) {
        String adminStatus = StatusMapping.toAdminStatus(developerStatus);

        // Verify the specific mapping
        switch (developerStatus) {
            case StatusMapping.DEV_DRAFT ->
                    assertThat(adminStatus).isEqualTo(StatusMapping.ADMIN_DRAFT);
            case StatusMapping.DEV_PUBLISHED ->
                    assertThat(adminStatus).isEqualTo(StatusMapping.ADMIN_VALIDATED);
            case StatusMapping.DEV_ARCHIVED ->
                    assertThat(adminStatus).isEqualTo(StatusMapping.ADMIN_DEPRECATED);
            default ->
                    throw new AssertionError("Unexpected developer status: " + developerStatus);
        }
    }

    @Property(tries = 100)
    void toDeveloperStatusShouldMapCorrectly(@ForAll("adminStatuses") String adminStatus) {
        String developerStatus = StatusMapping.toDeveloperStatus(adminStatus);

        // Verify the specific mapping
        switch (adminStatus) {
            case StatusMapping.ADMIN_DRAFT ->
                    assertThat(developerStatus).isEqualTo(StatusMapping.DEV_DRAFT);
            case StatusMapping.ADMIN_VALIDATED ->
                    assertThat(developerStatus).isEqualTo(StatusMapping.DEV_PUBLISHED);
            case StatusMapping.ADMIN_DEPLOYED ->
                    assertThat(developerStatus).isEqualTo(StatusMapping.DEV_PUBLISHED);
            case StatusMapping.ADMIN_DEPRECATED ->
                    assertThat(developerStatus).isEqualTo(StatusMapping.DEV_ARCHIVED);
            default ->
                    throw new AssertionError("Unexpected admin status: " + adminStatus);
        }
    }

    @Property(tries = 100)
    void invalidDeveloperStatusShouldThrow(@ForAll("invalidStatuses") String invalidStatus) {
        assertThatThrownBy(() -> StatusMapping.toAdminStatus(invalidStatus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown developer-workstation status");
    }

    @Property(tries = 100)
    void invalidAdminStatusShouldThrow(@ForAll("invalidStatuses") String invalidStatus) {
        assertThatThrownBy(() -> StatusMapping.toDeveloperStatus(invalidStatus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown admin-center status");
    }

    @Provide
    Arbitrary<String> developerStatuses() {
        return Arbitraries.of(
                StatusMapping.DEV_DRAFT,
                StatusMapping.DEV_PUBLISHED,
                StatusMapping.DEV_ARCHIVED
        );
    }

    @Provide
    Arbitrary<String> adminStatuses() {
        return Arbitraries.of(
                StatusMapping.ADMIN_DRAFT,
                StatusMapping.ADMIN_VALIDATED,
                StatusMapping.ADMIN_DEPLOYED,
                StatusMapping.ADMIN_DEPRECATED
        );
    }

    @Provide
    Arbitrary<String> invalidStatuses() {
        return Arbitraries.of(
                "INVALID", "UNKNOWN", "ACTIVE", "INACTIVE",
                "draft", "published", "validated", "", "null"
        );
    }
}
